"""
Golden dataset, Feature 2 (Insights): sends the 15 cases (INS-001 to 015) to POST /explain exactly as the app does,
scores them and uploads the run to the dashboard's AI evaluation page.

    cd server
    BASE=https://expense-tracker-metrics.vercel.app TESTER_KEY=... DASHBOARD_PASSWORD=... .venv/bin/python evals/run_f2.py

Two layers:
- model layer: what the AI wrote (the response's "raw"), scored by plain-code checks and an LLM judge (/evals/judge);
- app layer: what the guard lets onto the screen (the response's status/title/explanation).
Every score here is plain Python; the judge only gives rubric grades. Synthetic cases only.
"""
from __future__ import annotations

import base64
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
sys.path.insert(0, HERE)
from explain import BANNED, ExplainRequest, TEXT_MAX, TITLE_MAX, _numbers, allowed_numbers  # noqa: E402
from upload import commit, fetch  # noqa: E402

BASE = os.environ.get("BASE", "http://127.0.0.1:8000").rstrip("/")
KEY = os.environ.get("TESTER_KEY", "")
PASSWORD = os.environ.get("DASHBOARD_PASSWORD", "")
AUTH = "Basic " + base64.b64encode(f"team:{PASSWORD}".encode()).decode()


def post(path: str, body: dict, headers: dict) -> tuple[dict | None, int, str]:
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode(), method="POST",
                                 headers={"Content-Type": "application/json", **headers})
    t0 = time.time()
    for n in range(4):  # retry only a stalled connection, never an HTTP error answer
        try:
            return json.loads(urllib.request.urlopen(req, timeout=60).read()), int((time.time() - t0) * 1000), ""
        except urllib.error.HTTPError as e:
            return None, int((time.time() - t0) * 1000), f"HTTP {e.code}"
        except Exception as e:  # noqa: BLE001
            if n == 3:
                return None, int((time.time() - t0) * 1000), str(e)[:120]
            time.sleep(5 * (n + 1))
    return None, 0, "unreachable"


def pct(x: int, y: int):
    return None if y == 0 else round(100 * x / y, 1)


def indian(v) -> str:
    """5000 -> "5,000", 125000 -> "1,25,000" (as the app and the AI write rupees). Display only."""
    if not isinstance(v, (int, float)) or isinstance(v, bool):
        return str(v)
    neg, n = v < 0, abs(v)
    whole, frac = int(n), ("%g" % (n - int(n)))[1:] if n != int(n) else ""
    d = str(whole)
    if len(d) > 3:
        head, tail = d[:-3], d[-3:]
        head = ",".join([head[max(0, i - 2):i] for i in range(len(head), 0, -2)][::-1])
        d = head + "," + tail
    return ("-" if neg else "") + d + frac


def facts_supplied(case: dict) -> str:
    """Everything the AI was given, for the reviewer to check numbers against (golden eval F2 #4).
    Metrics not in metric_keys are marked: the AI may see them but shouldn't cite them."""
    i = case["input"]
    ev = i["validated_evidence_facts_or_references"]
    citable = set(ev.get("metric_keys") or [])
    metrics = " · ".join(f"{k} = {indian(v)}" + ("" if k in citable else " (not citable)") for k, v in i["verified_metrics"].items())
    lab = i["period_and_comparison_labels"]
    period = lab["current_period"] + (f" vs {lab['comparison_period']}" if lab.get("comparison_period") else "")
    cand = i.get("approved_pattern_candidate")
    parts = [f"Type: {i['approved_insight_type']}", f"Metrics: {metrics or 'none'}", f"Period: {period}",
             f"Pattern: {cand['candidate_id']} \"{cand['pattern']}\"" if cand else "Pattern: none",
             "Evidence: " + (", ".join(ev.get("evidence_refs") or []) or "none")]
    return " | ".join(parts)


def ai_cited(case: dict, raw: dict, resp: dict) -> str:
    """What the AI cited (keys and refs, after the server's filter) and every number in its words,
    flagging numbers that only come from a metric it wasn't asked to cite."""
    i = case["input"]
    citable = set(i["validated_evidence_facts_or_references"].get("metric_keys") or [])
    text = f"{raw.get('title') or ''} {raw.get('explanation') or ''}"
    by_value: dict = {}
    for k, v in i["verified_metrics"].items():
        if isinstance(v, (int, float)) and not isinstance(v, bool):
            by_value.setdefault(abs(float(v)), set()).add(k)
    lab = i["period_and_comparison_labels"]
    dates = set(_numbers(f"{lab['current_period']} {lab.get('comparison_period') or ''}"))  # "October 2026" isn't an amount
    nums = []
    for n in _numbers(text):
        if n in dates:
            continue
        keys = by_value.get(n, set())
        flag = " (from non-citable " + ", ".join(sorted(keys)) + ")" if keys and not keys & citable else ""
        nums.append(indian(int(n) if n == int(n) else n) + flag)
    return " | ".join([
        "Keys: " + (", ".join(resp.get("metric_keys_used") or []) or "none"),
        "Refs: " + (", ".join(resp.get("evidence_refs") or []) or "none"),
        "Numbers in text: " + (", ".join(nums) or "none"),
    ])


def score(case: dict, resp: dict | None, err: str) -> dict:
    exp = case["expected"]
    req = ExplainRequest(**case["input"])
    exp_status = exp["status"]
    row = {"id": case["id"], "scenario": case["scenario"],
           "expected": exp_status.replace("_", " ") + (f": {exp['explanation']}" if exp.get("explanation") else ""),
           "critical": "", "facts": facts_supplied(case), "cited": "—"}
    if resp is None:
        return {**row, "ai": f"Error: {err}", "app": "Rule wording (AI unavailable)", "result": "Miss",
                "why": "No answer from /explain", "_s": {"error": True}}

    if exp_status == "reuse_existing":
        return score_reuse(row, exp, resp)

    raw = resp["raw"]
    text = f"{raw.get('title') or ''} {raw.get('explanation') or ''}".strip()
    s: dict = {"status_ok": raw["status"] == exp_status, "generated": raw["status"] == "generate_explanation"}

    # Contract: text only when explaining, within length; keys and refs are enforced by the response schema.
    if s["generated"]:
        s["contract_ok"] = bool(raw.get("title")) and bool(raw.get("explanation")) and \
            len(raw["title"]) <= TITLE_MAX and len(raw["explanation"]) <= TEXT_MAX
    else:
        s["contract_ok"] = not raw.get("title") and not raw.get("explanation")

    if s["generated"]:
        ok = allowed_numbers(req)
        s["ungrounded"] = [n for n in _numbers(text) if n not in ok]
        s["banned"] = sorted({m.group(0).lower() for m in BANNED.finditer(text)})
        # Required facts: the amounts and percents in the expected sentence (case wording like INS-013/015 has none).
        need = [n for n in _numbers(exp.get("explanation") or "") if n in ok] if exp_status == "generate_explanation" else []
        have = set(_numbers(text))
        s["facts_n"], s["facts_hit"] = (1, int(all(n in have for n in need))) if need else (0, 0)
        j, _, jerr = post("/evals/judge", {"case_input": case["input"], "expected": exp, "prohibited": case.get("prohibited") or "",
                                           "output": raw}, {"Authorization": AUTH})
        s["judge"] = j
        s["unsupported"] = bool(s["ungrounded"] or s["banned"] or (j and j.get("unsupported_claim")))
    shown = resp["status"] == "generate_explanation"

    # Critical (app layer): what reaches the screen.
    crit = ""
    if shown and exp_status != "generate_explanation":
        crit = f"Explanation shown where the case expects {exp_status.replace('_', ' ')}"
    elif shown and s.get("unsupported"):
        crit = "Unsupported claim shown: " + ((s["judge"] or {}).get("notes") or ", ".join(s.get("banned") or []))
    s["critical"] = bool(crit)

    ai = raw["status"].replace("_", " ") + (f": {raw.get('title')} — {raw.get('explanation')}" if s["generated"] else "") + \
        (f" (reason: {resp.get('reason')})" if resp.get("reason") else "")
    app = "AI wording" if shown else ("Rule wording (guard blocked: " + "; ".join(resp.get("guard_issues") or []) + ")"
                                      if resp.get("guard_issues") else "No card / rule wording")
    j = s.get("judge") or {}
    scores = "·".join(str(j.get(k, "–")) for k in ("factual", "grounding", "no_speculation", "clarity")) if j else "—"

    why = []
    if crit:
        result = "Critical"
    elif not s["status_ok"]:
        result = "Miss"
        why.append(f"AI chose {raw['status'].replace('_', ' ')}")
    elif s["generated"] and (s.get("unsupported") or (s["facts_n"] and not s["facts_hit"]) or not s["contract_ok"]):
        result = "Partial"
    else:
        result = "Pass"
    if s.get("ungrounded"):
        why.append("number not supplied: " + ", ".join(f"{n:g}" for n in s["ungrounded"][:3]))
    if s.get("banned"):
        why.append("words: " + ", ".join(s["banned"]))
    if s.get("facts_n") and not s.get("facts_hit"):
        why.append("an expected amount or percent is missing")
    if j.get("unsupported_claim") or (j and min(j.get(k, 4) for k in ("factual", "grounding", "no_speculation")) < 4):
        why.append("judge: " + (j.get("notes") or ""))
    if resp.get("guard_issues") and not crit:
        why.append("guard kept it off the screen")
    return {**row, "ai": ai, "app": app, "scores": scores, "result": result, "why": "; ".join(why),
            "critical": crit, "cited": ai_cited(case, raw, resp) if s["generated"] else "—", "_s": s}


def score_reuse(row: dict, exp: dict, resp: dict) -> dict:
    """Golden v1.1 INS-013, app layer: an unchanged insight must reuse the stored card. Pass only if all three hold."""
    checks = {
        "AI not called": resp.get("ai_called") is False,
        "same insight id returned": bool(exp.get("insight_id")) and resp.get("insight_id") == exp.get("insight_id")
                                    and resp.get("status") == "reuse_existing",
        "no new card": resp.get("status") != "generate_explanation" and not resp.get("title") and not resp.get("explanation"),
    }
    failed = [k for k, ok in checks.items() if not ok]
    new_card = not checks["no new card"]
    s = {"status_ok": not failed, "generated": False, "contract_ok": True, "reuse_checks": checks, "critical": new_card}
    result = "Pass" if not failed else ("Critical" if new_card else "Miss")
    return {**row, "ai": "not called" if checks["AI not called"] else f"called: {resp.get('status')}",
            "app": f"{resp.get('status')}" + (f" · insight {resp.get('insight_id')}" if resp.get("insight_id") else ""),
            "scores": "—", "result": result, "why": "; ".join(f"failed: {k}" for k in failed),
            "critical": "New card created for an unchanged insight" if new_card else "", "cited": "—", "_s": s}


REVIEW_COLS = ["id", "scenario", "facts", "expected", "ai", "cited", "app", "scores", "result", "why"]


def write_review(rows: list, path: str) -> None:
    """Review sheet for the human judge: everything needed to check numbers and evidence, plus empty score columns."""
    head = ["Case", "Scenario", "Facts supplied", "Expected", "AI wrote", "AI cited", "App shows", "LLM judge F·G·S·C",
            "Result", "Why", "Human F", "Human G", "Human S", "Human C", "Human notes"]
    clean = lambda v: str(v or "").replace("\t", " ").replace("\n", " ")  # noqa: E731
    lines = ["\t".join(head)] + ["\t".join([clean(r.get(c)) for c in REVIEW_COLS] + [""] * 5) for r in rows]
    open(path, "w").write("\n".join(lines) + "\n")


def main() -> None:
    data = json.load(open(os.path.join(HERE, "golden_f2_v1.0.json")))
    health = json.loads(fetch(BASE + "/health"))
    rows, times = [], []
    for case in data["cases"]:
        resp, ms, err = post("/explain", case["input"], {"X-Tester-Key": KEY})
        times.append(ms)
        rows.append(score(case, resp, err))
        print(f"{case['id']}: {rows[-1]['result']}  {rows[-1]['ai'][:90]}")

    S = [r["_s"] for r in rows]
    gen = [s for s in S if s.get("generated")]
    judged = [s["judge"] for s in gen if s.get("judge")]
    summary = {
        "cases": len(rows),
        "status": pct(sum(s.get("status_ok", False) for s in S), len(S)),
        "facts": pct(sum(s.get("facts_hit", 0) for s in gen), sum(s.get("facts_n", 0) for s in gen)),
        "grounded": pct(sum(not s["ungrounded"] for s in gen), len(gen)),
        "unsupported": pct(sum(s["unsupported"] for s in gen), len(gen)),
        "evidence": pct(sum(s.get("contract_ok", False) for s in S), len(S)),
        "clarity": round(statistics.mean(j["clarity"] for j in judged), 2) if judged else None,
        "critical": sum(s.get("critical", False) for s in S),
        "median_ms": int(statistics.median(times)) if times else None,
    }
    now = datetime.now()
    run = {
        "run_id": "F2-" + now.strftime("%Y%m%d-%H%M"),
        "run_date": now.strftime("%Y-%m-%d"),
        "dataset_version": data["dataset_version"],
        "feature": "F2 Insights",
        "model": str(health.get("explain_ai", "unknown")) + f" · judge {(judged[0].get('judge_model') if judged else '—')}",
        "prompt_version": health.get("explain_prompt", "unknown"),
        "app_commit": commit(),
        "summary": summary,
        "cases": [{k: v for k, v in r.items() if k != "_s"} for r in rows],
    }
    os.makedirs(os.path.join(HERE, "results"), exist_ok=True)
    json.dump({**run, "detail": S}, open(os.path.join(HERE, "results", "f2_latest.json"), "w"), indent=1, ensure_ascii=False)
    write_review(rows, os.path.join(HERE, "results", "f2_review.tsv"))  # open in Sheets/Excel for human scoring
    out, _, err = post("/evals/runs", run, {"Authorization": AUTH})
    print(json.dumps(summary, indent=1))
    print("uploaded" if out else f"upload failed: {err}")


if __name__ == "__main__":
    main()
