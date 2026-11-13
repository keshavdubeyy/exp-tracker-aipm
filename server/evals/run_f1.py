"""
Golden dataset, Feature 1 (categorisation): model layer.

Sends each case to POST /categorise exactly as the app would, maps our answer back to the dataset's
format, and scores it. The app layer (does the app auto-file, show guesses, or ask?) is scored by
app/src/test/.../GoldenF1AppLayerTest.kt from the results file this writes.

    cd server
    BASE=https://expense-tracker-metrics.vercel.app TESTER_KEY=... .venv/bin/python evals/run_f1.py

Synthetic cases only: no real payments or SMS ever go into this script or its results.
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.request
from datetime import date

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.environ.get("BASE", "http://127.0.0.1:8000").rstrip("/")
KEY = os.environ.get("TESTER_KEY", "")


def to_request(case: dict) -> tuple[dict, list[str]]:
    """Dataset input -> our CategoriseRequest. Returns the request and notes on what doesn't map."""
    t = case["input"]["transaction"]
    cats = case["input"]["approved_expense_categories"]
    notes = []
    if t.get("direction") == "credit":
        notes.append("credit: the app never sends money received to the AI (income is rules-based)")
    if not t.get("merchant_or_recipient") and not t.get("upi_or_recipient_id"):
        notes.append("no payee: the app never sends a payment with no name or UPI ID to the AI")
    hour = None
    if t.get("transaction_datetime"):
        hour = int(t["transaction_datetime"][11:13])
    req = {
        "payee": t.get("merchant_or_recipient"),
        "vpa": t.get("upi_or_recipient_id"),
        "amount_paise": int(round((t.get("amount_inr") or 0) * 100)),
        "hour": hour,
        "method": t.get("payment_method"),
        "categories": [c["category_name"] for c in cats],
        "past": [],
    }
    return req, notes


def call(req: dict) -> tuple[dict | None, int, str]:
    body = json.dumps(req).encode()
    r = urllib.request.Request(BASE + "/categorise", data=body, method="POST",
                               headers={"Content-Type": "application/json", "X-Tester-Key": KEY})
    t0 = time.time()
    try:
        with urllib.request.urlopen(r, timeout=40) as resp:
            return json.loads(resp.read()), int((time.time() - t0) * 1000), ""
    except Exception as e:  # noqa: BLE001
        return None, int((time.time() - t0) * 1000), str(e)[:200]


def score(case: dict, out: dict | None, err: str = "") -> dict:
    name_to_id = {c["category_name"]: c["category_id"] for c in case["input"]["approved_expense_categories"]}
    exp = case["expected"]
    expect_abstain = exp["status"] == "insufficient_evidence"
    exp_top1 = exp["suggestions"][0]["category_id"] if exp["suggestions"] else None
    acceptable = set(case["acceptable_top3"])
    if out is None:
        if "422" in err:  # the server refused it as malformed (no payee and no UPI ID): a safe abstention
            return {"rejected": True, "expect_abstain": expect_abstain, "abstained": expect_abstain,
                    "abstained_or_unsure": expect_abstain, "confident_guess_on_abstain_case": False, "contract_ok": True}
        return {"error": True}
    raw = out.get("suggestions", [])
    ids = [name_to_id.get(s) for s in raw]
    contract_ok = len(raw) <= 3 and len(set(raw)) == len(raw) and all(ids)
    ids = [i for i in ids if i]
    abstained = len(ids) == 0
    mapped = {
        "status": "insufficient_evidence" if abstained else "suggest",
        "suggestions": [{"category_id": i, "rank": n + 1} for n, i in enumerate(ids)],
        "requires_user_confirmation": not out.get("confident", False),
    }
    s = {"mapped_output": mapped, "contract_ok": contract_ok, "expect_abstain": expect_abstain}
    if expect_abstain:
        s["abstained"] = abstained
        # Not a strict abstain, but flagged unsure: the app shows no guess for an unsure AI answer.
        s["abstained_or_unsure"] = abstained or not out.get("confident", False)
        s["confident_guess_on_abstain_case"] = (not abstained) and bool(out.get("confident"))
    else:
        s["top1_correct"] = bool(ids) and ids[0] == exp_top1
        s["top3_hit"] = exp_top1 in ids[:3]
        s["all_in_acceptable"] = all(i in acceptable for i in ids)
        s["false_abstain"] = abstained
        s["expected_confirmation"] = exp.get("requires_user_confirmation")
        s["confident"] = bool(out.get("confident"))
    return s


def main() -> None:
    data = json.load(open(os.path.join(HERE, "golden_f1_v1.0.json")))
    health = json.loads(urllib.request.urlopen(BASE + "/health", timeout=30).read())
    results = []
    for case in data["cases"]:
        req, notes = to_request(case)
        out, ms, err = call(req)
        sc = score(case, out, err)
        if sc.get("rejected"):
            err = ""
        results.append({"id": case["id"], "scenario": case["scenario"], "request": req, "notes": notes,
                        "raw_output": out, "ms": ms, "error": err, "score": sc,
                        "expected_top1": case["expected_top1"], "expected": case["expected"]})
        mark = "ERR" if err else "rejected" if sc.get("rejected") else ("abstain" if sc.get("expect_abstain") else ("ok" if sc.get("top1_correct") else "miss"))
        print(f"{case['id']} {mark:7} {ms:5}ms  {out}", file=sys.stderr)

    sug = [r for r in results if not r["score"].get("expect_abstain") and not r["error"]]
    abst = [r for r in results if r["score"].get("expect_abstain") and not r["error"]]
    pct = lambda a, b: None if b == 0 else round(100 * a / b, 1)  # noqa: E731
    summary = {
        "run_date": date.today().isoformat(),
        "dataset_version": data["dataset"],
        "server": BASE,
        "model": health.get("ai"),
        "cases_executed": len(results),
        "errors": sum(1 for r in results if r["error"]),
        "f1_top1_accuracy": pct(sum(r["score"]["top1_correct"] for r in sug), len(sug)),
        "f1_top3_accuracy": pct(sum(r["score"]["top3_hit"] for r in sug), len(sug)),
        "f1_false_abstain_rate": pct(sum(r["score"]["false_abstain"] for r in sug), len(sug)),
        "f1_abstention_strict": pct(sum(r["score"]["abstained"] for r in abst), len(abst)),
        "f1_abstention_or_unsure": pct(sum(r["score"]["abstained_or_unsure"] for r in abst), len(abst)),
        "f1_confident_guess_on_abstain_cases": sum(r["score"]["confident_guess_on_abstain_case"] for r in abst),
        "f1_output_contract_valid": pct(sum(r["score"]["contract_ok"] for r in results if not r["error"]), len([r for r in results if not r["error"]])),
        "f1_rejected_as_malformed": sum(1 for r in results if r["score"].get("rejected")),
        "median_ms": sorted(r["ms"] for r in results)[len(results) // 2],
    }
    out = {"summary": summary, "results": results}
    path = os.path.join(HERE, "results", f"f1_{date.today().isoformat()}.json")
    json.dump(out, open(path, "w"), indent=1, ensure_ascii=False)
    json.dump(out, open(os.path.join(HERE, "results", "f1_latest.json"), "w"), indent=1, ensure_ascii=False)
    # For the app-layer test (Kotlin unit tests have no JSON parser): one line per case.
    with open(os.path.join(HERE, "results", "f1_app_input.tsv"), "w") as f:
        for case, r in zip(data["cases"], results):
            id_to_name = {c["category_id"]: c["category_name"] for c in case["input"]["approved_expense_categories"]}
            exp = case["expected"]
            top1 = id_to_name[exp["suggestions"][0]["category_id"]] if exp["suggestions"] else "ABSTAIN"
            ok3 = ";".join(id_to_name.get(i, "") for i in case["acceptable_top3"])
            ai = r["raw_output"] or {}
            sent = "1" if r["raw_output"] is not None and not r["notes"] else "0"  # would the app have asked the AI?
            cells = [case["id"], r["request"]["payee"] or "", r["request"]["vpa"] or "", str(r["request"]["amount_paise"]),
                     ";".join(ai.get("suggestions", [])), "1" if ai.get("confident") else "0", sent, top1, ok3,
                     "1" if exp.get("requires_user_confirmation") else "0"]
            f.write("\t".join(c.replace("\t", " ") for c in cells) + "\n")
    print(json.dumps(summary, indent=1))


if __name__ == "__main__":
    main()
