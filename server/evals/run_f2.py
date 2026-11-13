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


def score(case: dict, resp: dict | None, err: str) -> dict:
    exp = case["expected"]
    req = ExplainRequest(**case["input"])
    exp_status = exp["status"]
    row = {"id": case["id"], "scenario": case["scenario"],
           "expected": exp_status.replace("_", " ") + (f": {exp['explanation']}" if exp.get("explanation") else ""),
           "critical": ""}
    if resp is None:
        return {**row, "ai": f"Error: {err}", "app": "Rule wording (AI unavailable)", "result": "Miss",
                "why": "No answer from /explain", "_s": {"error": True}}

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
    if case["id"] == "INS-013":
        why.append("app side: the same insight reuses its saved wording (InsightWriter cache), so no duplicate card")
    return {**row, "ai": ai, "app": app, "scores": scores, "result": result, "why": "; ".join(why),
            "critical": crit, "_s": s}


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
    out, _, err = post("/evals/runs", run, {"Authorization": AUTH})
    print(json.dumps(summary, indent=1))
    print("uploaded" if out else f"upload failed: {err}")


if __name__ == "__main__":
    main()
