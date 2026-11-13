"""
Joins the two layers of a golden-dataset run and uploads it to the dashboard (POST /evals/runs).

    BASE=https://expense-tracker-metrics.vercel.app DASHBOARD_PASSWORD=... .venv/bin/python evals/upload.py

Reads results/f1_latest.json (AI layer, run_f1.py) and results/f1_app_latest.tsv (app layer,
GoldenF1AppLayerTest). Synthetic cases only.
"""
from __future__ import annotations

import base64
import json
import os
import subprocess
import sys
import urllib.request
from datetime import datetime

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.environ.get("BASE", "http://127.0.0.1:8000").rstrip("/")
PASSWORD = os.environ.get("DASHBOARD_PASSWORD", "")


def app_rows() -> dict:
    path = os.path.join(HERE, "results", "f1_app_latest.tsv")
    if not os.path.exists(path):
        sys.exit("Missing results/f1_app_latest.tsv: run the app-layer test first (see run_all.sh)")
    lines = open(path).read().splitlines()
    head = lines[0].split("\t")
    rows = {}
    for line in lines[1:]:
        if not line.strip():
            break
        cells = line.split("\t") + [""] * len(head)
        r = dict(zip(head, cells))
        rows[r["case"]] = r
    return rows


def fetch(req, tries: int = 4) -> bytes:
    """The connection to Vercel sometimes stalls for a minute: retry instead of failing the run."""
    import time
    for n in range(tries):
        try:
            return urllib.request.urlopen(req, timeout=30).read()
        except Exception:  # noqa: BLE001
            if n == tries - 1:
                raise
            time.sleep(10 * (n + 1))


def judge(case: dict, output: dict) -> dict | None:
    """Feature 1 rubric scores from POST /evals/judge, or None. An HTTP error is final; a stalled connection is retried."""
    import urllib.error
    body = {"feature": "F1", "case_input": case["input"],
            "expected": {**case["expected"], "expected_top1": case.get("expected_top1"), "acceptable_top3": case.get("acceptable_top3")},
            "output": output}
    req = urllib.request.Request(
        BASE + "/evals/judge", data=json.dumps(body).encode(), method="POST",
        headers={"Content-Type": "application/json",
                 "Authorization": "Basic " + base64.b64encode(f"team:{PASSWORD}".encode()).decode()},
    )
    import time
    for n in range(3):
        try:
            return json.loads(urllib.request.urlopen(req, timeout=90).read())
        except urllib.error.HTTPError as e:
            print(f"judge {case['id']}: HTTP {e.code}")
            return None
        except Exception:  # noqa: BLE001
            time.sleep(5 * (n + 1))
    return None


def commit() -> str:
    try:
        root = os.path.join(HERE, "..", "..")
        sha = subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], cwd=root, text=True).strip()
        dirty = subprocess.run(["git", "diff", "--quiet"], cwd=root).returncode != 0
        return sha + ("+local" if dirty else "")
    except Exception:  # noqa: BLE001
        return "unknown"


def main() -> None:
    model = json.load(open(os.path.join(HERE, "results", "f1_latest.json")))
    golden = {c["id"]: c for c in json.load(open(os.path.join(HERE, "golden_f1_v1.0.json")))["cases"]}
    judged = []
    app = app_rows()
    health = json.loads(fetch(BASE + "/health"))

    cases, crit = [], 0
    filed = filed_right = 0
    abstain_n = abstain_ok = 0
    for r in model["results"]:
        sc, a = r["score"], app.get(r["id"], {})
        exp_top1 = r["expected_top1"]
        expect_abstain = sc.get("expect_abstain")
        out = r["raw_output"]
        if sc.get("rejected"):
            ai = "Not sent: no payee or UPI ID (server rejects it)"
        elif out is None:
            ai = "Error"
        else:
            ai = (", ".join(out["suggestions"]) or "nothing") + (" · sure" if out.get("confident") else " · not sure")
        app_did = a.get("app_outcome", "—") + (f": {a['filed']}" if a.get("filed") else "") + \
            (f" (first: {a['shown_first']})" if a.get("shown_first") and not a.get("filed") else "")
        if a.get("filed"):
            filed += 1
            filed_right += a["filed"] == exp_top1

        why = ""
        if a.get("critical"):
            result, why = "Critical", a["critical"] + f" (app rule: {a.get('basis', '')}, score {a.get('score', '')})"
            crit += 1
        elif expect_abstain:
            abstain_n += 1
            if not a.get("filed"):
                abstain_ok += 1
            if a.get("app_outcome") == "asked, no guess":
                result = "Pass"
            else:
                result, why = "Partial", "Nothing filed, but guesses were offered where the case expects none"
        elif sc.get("top1_correct"):
            result = "Pass"
            if a.get("app_outcome") == "asked, no guess":
                why = "AI was right but not sure, so the app showed no guess"
        elif sc.get("top3_hit"):
            result, why = "Partial", "Expected category is in the AI's list, not first"
        else:
            result, why = "Miss", "Expected category not in the AI's suggestions"
        # LLM judge on the Feature 1 rubric (0-4): what the AI said and what the app then did.
        j = judge(golden[r["id"]], {
            "ai_suggestions": (out or {}).get("suggestions", []), "ai_confident": (out or {}).get("confident", False),
            "sent_to_ai": not sc.get("rejected") and out is not None,
            "app_did": a.get("app_outcome", ""), "app_filed": a.get("filed") or None,
        }) if r["id"] in golden else None
        if j:
            judged.append(j)
            if j.get("acceptable_alternative") and result in ("Miss", "Partial"):
                why = (why + "; " if why else "") + "judge: acceptable alternative"
        cases.append({
            "scores": "·".join(str(j[k]) for k in ("correctness", "ranking", "uncertainty", "restraint")) if j else "—",
            "needs_human": bool(j and j.get("needs_human")), "judge_notes": (j or {}).get("notes", ""),
            "id": r["id"], "scenario": r["scenario"], "payee": r["request"]["payee"],
            "expected": "Abstain" if exp_top1 == "ABSTAIN / NO CATEGORY" else exp_top1,
            "ai": ai, "app": app_did, "result": result, "why": why, "critical": a.get("critical", ""),
        })

    m = model["summary"]
    pct = lambda x, y: None if y == 0 else round(100 * x / y, 1)  # noqa: E731
    summary = {
        "cases": m["cases_executed"],
        "top1": m["f1_top1_accuracy"],
        "top3": m["f1_top3_accuracy"],
        "precision": pct(filed_right, filed),
        "abstain_app": pct(abstain_ok, abstain_n),
        "abstain_ai": m["f1_abstention_or_unsure"],
        "contract": m["f1_output_contract_valid"],
        "critical": crit,
        "median_ms": m["median_ms"],
        **{f"judge_{k[0]}": (round(sum(j[k] for j in judged) / len(judged), 2) if judged else None)
           for k in ("correctness", "ranking", "uncertainty")},
        "judge_e": round(sum(j["restraint"] for j in judged) / len(judged), 2) if judged else None,
        "needs_human": sum(bool(j.get("needs_human")) for j in judged),
    }
    now = datetime.now()
    run = {
        "run_id": "R-" + now.strftime("%Y%m%d-%H%M"),
        "run_date": now.strftime("%Y-%m-%d"),
        "dataset_version": model["summary"]["dataset_version"],
        "feature": "F1 Categorisation",
        "model": health.get("ai", "unknown") + (f" · judge {judged[0].get('judge_model')}" if judged else ""),
        "prompt_version": health.get("prompt", "unknown"),
        "app_commit": commit(),
        "summary": summary,
        "cases": cases,
    }
    req = urllib.request.Request(
        BASE + "/evals/runs", data=json.dumps(run).encode(), method="POST",
        headers={"Content-Type": "application/json",
                 "Authorization": "Basic " + base64.b64encode(f"team:{PASSWORD}".encode()).decode()},
    )
    print(fetch(req).decode())
    print(json.dumps(summary, indent=1))


if __name__ == "__main__":
    main()
