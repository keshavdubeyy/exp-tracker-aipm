"""
Tester metrics (MVP M24): every tester phone sends one counts-only report a day; the team reads
all testers together on /dashboard.

POST /metrics    - one report per install per day (a later report the same day replaces it).
                   Counts only: unknown fields are rejected, so SMS text, names or amounts can't
                   arrive here even by mistake (AGENTS.md rule 3, MVP M21).
GET  /dashboard  - criteria across all testers, trend by day, by app version, and per tester.
                   Team password (HTTP Basic, any user name, password = DASHBOARD_PASSWORD).
GET  /dashboard.json - the same numbers as JSON.

Every number is computed here in plain Python from the counts. No AI.
No IP addresses or user agents are stored or logged.
"""

from __future__ import annotations

import html
import json
import logging
import os
import secrets
import sqlite3
import statistics
import threading
import time
from dataclasses import dataclass
from datetime import date, timedelta
from typing import Callable, Dict, List, Literal, Optional

from fastapi import APIRouter, Depends, Header, HTTPException
from fastapi.responses import HTMLResponse
from fastapi.security import HTTPBasic, HTTPBasicCredentials
from pydantic import BaseModel, ConfigDict, Field

log = logging.getLogger("metrics")

DB_PATH = os.environ.get("METRICS_DB", os.path.join(os.path.dirname(__file__), "metrics.db"))
DASHBOARD_PASSWORD = os.environ.get("DASHBOARD_PASSWORD", "")
TESTER_KEY = os.environ.get("TESTER_KEY", "")
MAX_REPORTS_PER_INSTALL_PER_HOUR = 30
MIN_TESTERS_FOR_TOTAL = 3  # the rupee total stays hidden until this many testers share theirs

router = APIRouter()
basic = HTTPBasic()
_lock = threading.Lock()


# ---------------------------------------------------------------------------------------------
# What a phone may send. Counts and flags only. Add a field here AND on the phone together.
# ---------------------------------------------------------------------------------------------

def _n(desc: str):
    return Field(default=None, ge=0, le=10_000_000, description=desc)


ScenarioKey = Literal[
    "missing_fields", "adversarial", "clear_merchant", "weak_merchant", "generic_business", "abbreviated",
    "person", "brand_name", "upi_id_only", "phone_number_id", "non_latin",
]


class ScenarioCounts(BaseModel):
    """Real payments the AI answered for, in one golden-dataset scenario (GoldenScenario.kt). Counts only."""
    model_config = ConfigDict(extra="forbid")
    asked: int = Field(ge=0, le=1_000_000)
    picked: int = Field(ge=0, le=1_000_000)
    top3: int = Field(ge=0, le=1_000_000)
    first: int = Field(ge=0, le=1_000_000)


InsightKind = Literal["change", "share", "repeat", "weekend", "concentration"]


class InsightKindCounts(BaseModel):
    model_config = ConfigDict(extra="forbid")
    shown: int = Field(ge=0, le=1_000_000)
    opened: int = Field(ge=0, le=1_000_000)
    helpful: int = Field(ge=0, le=1_000_000)
    not_relevant: int = Field(ge=0, le=1_000_000)
    dismissed: int = Field(ge=0, le=1_000_000)


class Counts(BaseModel):
    model_config = ConfigDict(extra="forbid")

    # M1 onboarding
    onboarding_started: Optional[int] = _n("1 if setup was started on this phone")
    onboarding_done: Optional[int] = _n("1 if setup reached Home")
    sms_allowed: Optional[int] = _n("1 if SMS access is on")
    onboarding_seconds: Optional[int] = _n("seconds from Get started to Home")
    sms_live: Optional[int] = _n("1 if new bank SMS wake the app (RECEIVE_SMS)")
    alerts_on: Optional[int] = _n("1 if payment-app alerts reach the app (notification access)")
    # M2-M5 capture
    sms_bank_kept: Optional[int] = _n("bank/payment SMS kept by the sender filter")
    sms_read: Optional[int] = _n("of those, read as a debit or credit")
    # Feature 1 "Tracked so far". The rupee total arrives only from testers who agreed to share it (consent v2),
    # and the dashboard shows it only added up across several testers, never per tester.
    pay_all: Optional[int] = _n("payments counted as spent, all time")
    paid_rupees_all: Optional[int] = Field(default=None, ge=0, le=10_000_000_000, description="total spent, all time, whole rupees")
    paid_rupees_30d: Optional[int] = Field(default=None, ge=0, le=10_000_000_000, description="total spent, last 30 days, whole rupees")
    pay_sorted_all: Optional[int] = _n("of those, with a category now (\"Sorted so far\")")
    # Feature 1 capture: payment-app alerts and what recorded each payment
    alert_amount: Optional[int] = _n("payment-app alerts that mention an amount")
    alert_read: Optional[int] = _n("of those, read as money paid or received")
    pay_30d: Optional[int] = _n("payments in the last 30 days (not waiting for an answer)")
    pay_auto_30d: Optional[int] = _n("of those, recorded by a bank SMS or app alert")
    pay_alert_30d: Optional[int] = _n("of those, with a payment-app alert")
    pay_alert_sms_30d: Optional[int] = _n("of those, also matched to a bank SMS with the same amount")
    pay_added_notif_30d: Optional[int] = _n("of pay_30d, added by the user from a notification or pop-up")
    pay_added_app_30d: Optional[int] = _n("of pay_30d, added by the user inside the app (answered or typed)")
    pay_typed_30d: Optional[int] = _n("of those, typed in with +")
    pay_named_30d: Optional[int] = _n("of pay_30d, with a real payee name")
    pay_double_30d: Optional[int] = _n("possible double counts: unlinked alert/answer and bank SMS, same amount, within 30 min")
    capture_timed: Optional[int] = _n("bank SMS timed from arriving to showing in the app")
    capture_1m: Optional[int] = _n("of those, within 1 minute")
    sms_paylike: Optional[int] = _n("bank SMS with a payment word and an amount")
    sms_paylike_read: Optional[int] = _n("of those, read")
    alert_paylike: Optional[int] = _n("payment-app alerts with a payment word and an amount")
    alert_paylike_read: Optional[int] = _n("of those, read")
    # M7 / M23 did it go through
    started_total: Optional[int] = _n("payments started in the app or seen on a PIN screen")
    started_answered: Optional[int] = _n("of those, answered or confirmed by SMS/alert")
    started_pending_24h: Optional[int] = _n("still pending more than 24 h after starting")
    # M8-M11 categorise (Feature 1 section 7)
    cat_all: Optional[int] = _n("payments with a categorisation record")
    cat_auto: Optional[int] = _n("categorised automatically")
    cat_auto_kept: Optional[int] = _n("automatic categories the user never changed")
    cat_learned: Optional[int] = _n("placed from the user's saved choice for that payee")
    cat_learned_kept: Optional[int] = _n("of those, never changed")
    cat_asked: Optional[int] = _n("needed the user to pick")
    cat_picked: Optional[int] = _n("of those, the user picked one")
    cat_picked_top3: Optional[int] = _n("of those, one of our top 3 suggestions")
    cat_ai_picked: Optional[int] = _n("user picks where the AI made the guesses shown")
    cat_ai_top3: Optional[int] = _n("of those, one of the AI's top 3")
    cat_ai_first: Optional[int] = _n("of those, the AI's first guess")
    cat_ai_auto: Optional[int] = _n("categories the AI filed on its own (no user pick)")
    cat_ai_auto_kept: Optional[int] = _n("of those, never changed by the user")
    spend_count_30d: Optional[int] = _n("counted payments in the last 30 days")
    spend_uncategorised_30d: Optional[int] = _n("of those, still with no category")
    past_pay: Optional[int] = _n("counted payments dated before the app was installed (history found at install)")
    past_sorted: Optional[int] = _n("of those, with a category now")
    past_sorted_auto: Optional[int] = _n("of those, sorted by the app on its own (merchant map, shop words, AI)")
    past_sorted_ai: Optional[int] = _n("of those, sorted by the AI")
    # M14-M16 screens
    active_days_7d: Optional[int] = _n("days in the last 7 the app was opened")
    insights_days_7d: Optional[int] = _n("days in the last 7 Insights was opened")
    # Feature 2 Insights: cards first shown in the last 30 days and what happened to them
    ins_days_30d: Optional[int] = _n("days in the last 30 Insights was opened")
    ins_card_days_30d: Optional[int] = _n("of those, days it showed at least one card")
    ins_shown_30d: Optional[int] = _n("insight cards shown")
    ins_opened_30d: Optional[int] = _n("of those, opened")
    ins_rated_30d: Optional[int] = _n("of those, rated")
    ins_helpful_30d: Optional[int] = _n("of those, rated helpful")
    ins_negative_30d: Optional[int] = _n("of those, hidden or marked not relevant")
    ins_ai_shown_30d: Optional[int] = _n("of the cards shown, ones in AI words")
    ins_ai_rated_30d: Optional[int] = _n("of those, rated")
    ins_ai_helpful_30d: Optional[int] = _n("of those, rated helpful")
    ins_ai_negative_30d: Optional[int] = _n("of those, hidden or marked not relevant")
    ins_kinds: Optional[Dict[InsightKind, InsightKindCounts]] = None
    # M25 budgets
    budget_set: Optional[int] = _n("1 if this month has a budget")
    budget_months: Optional[int] = _n("months with a budget so far")
    budget_categories: Optional[int] = _n("category budgets this month")
    budget_over: Optional[int] = _n("category budgets over their limit this month")
    budget_alerts_sent: Optional[int] = _n("budget notifications sent this month")
    # M20: real AI cases by golden-dataset scenario (counts only, never payee names)
    golden: Optional[Dict[ScenarioKey, ScenarioCounts]] = None


class Report(BaseModel):
    model_config = ConfigDict(extra="forbid")
    install_id: str = Field(pattern=r"^[a-f0-9]{16,32}$")  # one-way hash made on the phone; not linked to a person
    # The phone's earlier random ID (builds before 13 Nov): its reports are moved to install_id, so one phone = one tester.
    replaces: Optional[str] = Field(default=None, pattern=r"^[a-f0-9]{16,32}$")
    app_version: str = Field(max_length=24, pattern=r"^[0-9A-Za-z.\-_ ]+$")
    day: date
    counts: Counts


# ---------------------------------------------------------------------------------------------
# Storage
# ---------------------------------------------------------------------------------------------

# Postgres when DATABASE_URL / POSTGRES_URL is set (Vercel + Neon: serverless disks are wiped),
# otherwise a SQLite file (laptop).
PG_URL = os.environ.get("DATABASE_URL") or os.environ.get("POSTGRES_URL") or ""
_ready = False


def _db():
    global _ready
    if PG_URL:
        import psycopg
        # A stalled connect would hold _lock forever and every database request would wait behind it.
        conn = psycopg.connect(PG_URL, autocommit=True, connect_timeout=10)
    else:
        conn = sqlite3.connect(DB_PATH)
    if not _ready:
        conn.execute(
            """CREATE TABLE IF NOT EXISTS reports (
                   install_id TEXT NOT NULL,
                   day TEXT NOT NULL,
                   app_version TEXT NOT NULL,
                   counts TEXT NOT NULL,
                   received_at BIGINT NOT NULL,
                   PRIMARY KEY (install_id, day))"""
        )
        conn.execute("CREATE TABLE IF NOT EXISTS rejected (day TEXT PRIMARY KEY, n INTEGER NOT NULL)")
        _ready = True
    return conn


def _run(sql: str, params: tuple = (), fetch: bool = False):
    """One statement, written with ? placeholders; opens and closes its own connection."""
    if PG_URL:
        sql = sql.replace("?", "%s")
    with _lock:
        conn = _db()
        try:
            cur = conn.execute(sql, params)
            rows = cur.fetchall() if fetch else None
            if not PG_URL:
                conn.commit()
            return rows
        finally:
            conn.close()


_recent: Dict[str, List[float]] = {}


def _rate_ok(install_id: str) -> bool:
    now = time.time()
    hits = [t for t in _recent.get(install_id, []) if now - t < 3600]
    hits.append(now)
    _recent[install_id] = hits
    return len(hits) <= MAX_REPORTS_PER_INSTALL_PER_HOUR


def count_rejected() -> None:
    """Called by the app's validation handler for /metrics: 'Uploads rejected for unexpected fields'."""
    _run(
        "INSERT INTO rejected(day, n) VALUES(?, 1) ON CONFLICT(day) DO UPDATE SET n = rejected.n + 1",
        (date.today().isoformat(),),
    )


@router.post("/metrics")
def receive(report: Report, x_tester_key: str = Header(default="")) -> dict:
    if TESTER_KEY and x_tester_key != TESTER_KEY:
        raise HTTPException(status_code=401, detail="Unknown tester key")
    if report.day > date.today() + timedelta(days=1) or report.day < date.today() - timedelta(days=14):
        raise HTTPException(status_code=422, detail="day out of range")
    if not _rate_ok(report.install_id):
        raise HTTPException(status_code=429, detail="Too many reports")
    counts = {k: v for k, v in report.counts.model_dump().items() if v is not None}
    if report.replaces and report.replaces != report.install_id:
        # Same phone, old random ID: keep its history under the new ID (the new ID's own day wins on a clash).
        _run("DELETE FROM reports WHERE install_id = ? AND day IN (SELECT day FROM reports WHERE install_id = ?)",
             (report.replaces, report.install_id))
        _run("UPDATE reports SET install_id = ? WHERE install_id = ?", (report.install_id, report.replaces))
    _run(
        "INSERT INTO reports(install_id, day, app_version, counts, received_at) VALUES (?,?,?,?,?) "
        "ON CONFLICT(install_id, day) DO UPDATE SET app_version = excluded.app_version, "
        "counts = excluded.counts, received_at = excluded.received_at",
        (report.install_id, report.day.isoformat(), report.app_version, json.dumps(counts), int(time.time())),
    )
    log.info("metrics report fields=%d", len(counts))  # no id, no values
    return {"ok": True}


def _rows() -> List[dict]:
    rows = _run("SELECT install_id, day, app_version, counts, received_at FROM reports", fetch=True)
    return [
        {"install_id": i, "day": d, "app_version": v, "counts": json.loads(c), "received_at": t}
        for i, d, v, c, t in rows
    ]


def _rejected_total() -> int:
    return int(_run("SELECT COALESCE(SUM(n), 0) FROM rejected", fetch=True)[0][0])


# ---------------------------------------------------------------------------------------------
# Criteria (MVP M24 table). Pooled = sum of numerators / sum of denominators across testers.
# Share-of-testers criteria count testers who meet a condition.
# ---------------------------------------------------------------------------------------------

@dataclass
class Criterion:
    feature: str
    name: str
    target_text: str
    compute: Callable[[List[dict]], Optional[float]]  # list of counts dicts -> value (None = no data)
    passes: Callable[[float], bool]
    unit: str = "%"
    basis: Callable[[List[dict]], int] = lambda cs: len(cs)  # testers the value is based on
    meaning: str = ""
    group: str = ""


def _sum(cs: List[dict], k: str) -> int:
    return sum(int(c.get(k) or 0) for c in cs)


def pooled(num: str, den: str) -> Callable[[List[dict]], Optional[float]]:
    def f(cs: List[dict]) -> Optional[float]:
        d = _sum(cs, den)
        return None if d == 0 else 100.0 * _sum(cs, num) / d
    return f


def pooled_left(num: str, den: str) -> Callable[[List[dict]], Optional[float]]:
    """100 - pooled: e.g. 'not read' from 'read'."""
    p = pooled(num, den)
    return lambda cs: None if p(cs) is None else 100.0 - p(cs)


def share(cond: Callable[[dict], bool], among: Callable[[dict], bool] = lambda c: True) -> Callable[[List[dict]], Optional[float]]:
    def f(cs: List[dict]) -> Optional[float]:
        pool = [c for c in cs if among(c)]
        return None if not pool else 100.0 * sum(1 for c in pool if cond(c)) / len(pool)
    return f


def has(k: str) -> Callable[[List[dict]], int]:
    return lambda cs: sum(1 for c in cs if int(c.get(k) or 0) > 0)


def median_minutes(cs: List[dict]) -> Optional[float]:
    vals = [c["onboarding_seconds"] / 60.0 for c in cs if c.get("onboarding_seconds")]
    return statistics.median(vals) if vals else None


ge = lambda t: (lambda v: v >= t)  # noqa: E731
le = lambda t: (lambda v: v <= t)  # noqa: E731
info = lambda v: True  # noqa: E731  (no target yet: tracked only)

PAY = "Payment tracking"
CAT = "Smart categorisation"
INS = "Insights"
BUD = "Budgets"
USE = "Engagement"
# Sidebar, in order. Each feature is one page; [group] is a heading inside the page.
FEATURE_ORDER = [PAY, CAT, INS, BUD, USE]

CRITERIA: List[Criterion] = [
    # 1. Payment tracking: onboarding
    Criterion(PAY, "Setup completed", "≥ 80%", pooled("onboarding_done", "onboarding_started"), ge(80), basis=has("onboarding_started"),
              meaning="Testers who started setup and reached Home", group="Onboarding"),
    Criterion(PAY, "SMS access allowed", "≥ 90%", pooled("sms_allowed", "onboarding_started"), ge(90), basis=has("onboarding_started"),
              meaning="Testers who let the app read bank SMS", group="Onboarding"),
    Criterion(PAY, "Median setup time", "≤ 2 min", median_minutes, le(2), unit=" min", basis=has("onboarding_seconds"),
              meaning="From Get started to Home", group="Onboarding"),
    # 1. Payment tracking: capture. Was every payment found on its own, quickly, once, and read well?
    Criterion(PAY, "Capture switched on", "≥ 90%",
              share(lambda c: int(c.get("sms_live") or 0) > 0 and int(c.get("alerts_on") or 0) > 0,
                    among=lambda c: c.get("sms_live") is not None and c.get("alerts_on") is not None), ge(90),
              basis=lambda cs: sum(1 for c in cs if c.get("sms_live") is not None and c.get("alerts_on") is not None),
              meaning="Testers with both live bank SMS and payment-app alerts on. Without them payments show late or not at all", group="Capture"),
    Criterion(PAY, "Found on its own", "≥ 90%", pooled("pay_auto_30d", "pay_30d"), ge(90), basis=has("pay_30d"),
              meaning="Payments (last 30 days) recorded by a bank SMS, app alert or Scan & Pay result, with nothing from the user", group="Capture"),
    Criterion(PAY, "Added from a notification", "track", pooled("pay_added_notif_30d", "pay_30d"), info, basis=has("pay_30d"),
              meaning="Payments the user had to confirm from our notification or the pop-up over the payment app: no SMS or alert came", group="Capture"),
    Criterion(PAY, "Added after opening the app", "track", pooled("pay_added_app_30d", "pay_30d"), info, basis=has("pay_30d"),
              meaning="Payments the user confirmed or typed in inside the app: we didn't catch them in time", group="Capture"),
    Criterion(PAY, "Captured within 1 minute", "≥ 90%", pooled("capture_1m", "capture_timed"), ge(90), basis=has("capture_timed"),
              meaning="Bank SMS that showed up in the app within a minute of arriving", group="Capture"),
    Criterion(PAY, "Payee identified", "≥ 90%", pooled("pay_named_30d", "pay_30d"), ge(90), basis=has("pay_30d"),
              meaning="Payments with a real name, not \"Other bank payments\"", group="Capture"),
    Criterion(PAY, "Possible double counts", "≤ 2%", pooled("pay_double_30d", "pay_30d"), le(2), basis=has("pay_30d"),
              meaning="An app alert and a bank SMS for the same amount within 30 min that weren't joined into one payment", group="Capture"),
    Criterion(PAY, "Still pending after 24 h", "≤ 10%", pooled("started_pending_24h", "started_total"), le(10), basis=has("started_total"),
              meaning="Payments seen starting that no SMS, alert or answer confirmed within a day", group="Capture"),
    # 1. Payment tracking: health checks. Of messages that look like a payment, how many we read. Should be ~100%;
    # a drop means a bank or payment app changed its wording.
    Criterion(PAY, "Payment SMS read", "≥ 99%", pooled("sms_paylike_read", "sms_paylike"), ge(99), basis=has("sms_paylike"),
              meaning="Bank SMS with a payment word and an amount that we read. Offers, OTPs and statements aren't counted", group="Health checks"),
    Criterion(PAY, "Payment alerts read", "≥ 99%", pooled("alert_paylike_read", "alert_paylike"), ge(99), basis=has("alert_paylike"),
              meaning="Payment-app alerts with a payment word and an amount that we read", group="Health checks"),
    # 2. Smart categorisation
    Criterion(CAT, "AI suggestion hit rate", "≥ 80%", pooled("cat_ai_top3", "cat_ai_picked"), ge(80), basis=has("cat_ai_picked"),
              meaning="When the AI made the guesses, the user's pick was one of its top 3"),
    # Was "AI categories kept" (cat_ai_auto_kept / cat_ai_auto), merged with "AI first-guess rate" on 13 Nov 2026: the AI no
    # longer files on its own (three tiers), so the question is how often its first guess is the one the user accepts.
    # cat_ai_auto / cat_ai_auto_kept are still received and stored, just not shown.
    Criterion(CAT, "AI first pick accepted", "≥ 50%", pooled("cat_ai_first", "cat_ai_picked"), ge(50), basis=has("cat_ai_picked"),
              meaning="Of payments where the AI's guesses were shown and the user picked, the share where they tapped the AI's first suggestion"),
    Criterion(CAT, "Categorisation accuracy", "≥ 85%", pooled("cat_auto_kept", "cat_auto"), ge(85), basis=has("cat_auto"),
              meaning="Categories the app filled in on its own that the user never changed"),
    Criterion(CAT, "Suggestion hit rate (all sources)", "≥ 80%", pooled("cat_picked_top3", "cat_picked"), ge(80), basis=has("cat_picked"),
              meaning="When the user had to pick, the pick was one of the 3 guesses"),
    Criterion(CAT, "Uncategorised payments", "≤ 10%", pooled("spend_uncategorised_30d", "spend_count_30d"), le(10), basis=has("spend_count_30d"),
              meaning="Payments in the last 30 days still with no category"),
    # 2. Smart categorisation: the history already on the phone at install.
    Criterion(CAT, "Past payments sorted", "≥ 90%", pooled("past_sorted", "past_pay"), ge(90), basis=has("past_pay"),
              meaning="Payments from before install (the inbox found at setup) that have a category now. Sent by build 455954 and later", group="History at install"),
    Criterion(CAT, "Past payments sorted on their own", "track", pooled("past_sorted_auto", "past_pay"), info, basis=has("past_pay"),
              meaning="Of those past payments, sorted by the merchant map, shop words or AI with nothing from the user. AI picks show ✦ in the app and can be changed", group="History at install"),
    Criterion(CAT, "Past payments sorted by AI", "track", pooled("past_sorted_ai", "past_pay"), info, basis=has("past_pay"),
              meaning="Of those past payments, the share the AI sorted (rules couldn't)", group="History at install"),
    # 3. Insights (Feature 2). Rule-based today: these say whether people come back to it and find the cards useful.
    Criterion(INS, "Open Insights each week", "≥ 50%", share(lambda c: int(c.get("insights_days_7d") or 0) >= 1), ge(50),
              meaning="Share of testers who opened Insights in the last 7 days", group="Reach"),
    Criterion(INS, "Something to show", "≥ 80%", pooled("ins_card_days_30d", "ins_days_30d"), ge(80), basis=has("ins_days_30d"),
              meaning="Days Insights was opened that it showed at least one insight card (not just totals)", group="Reach"),
    Criterion(INS, "Cards opened", "≥ 30%", pooled("ins_opened_30d", "ins_shown_30d"), ge(30), basis=has("ins_shown_30d"),
              meaning="Insight cards shown that the person tapped to see how it was worked out", group="Usefulness"),
    Criterion(INS, "Rated helpful", "≥ 70%", pooled("ins_helpful_30d", "ins_rated_30d"), ge(70), basis=has("ins_rated_30d"),
              meaning="Of cards the person rated, the share marked Helpful", group="Usefulness"),
    Criterion(INS, "Hidden or not relevant", "≤ 20%", pooled("ins_negative_30d", "ins_shown_30d"), le(20), basis=has("ins_shown_30d"),
              meaning="Insight cards shown that the person hid or marked Not relevant", group="Usefulness"),
    # Feature 2 AI wording in real use: AI words vs the app's own words, rated by the same people.
    Criterion(INS, "Cards in AI words", "track", pooled("ins_ai_shown_30d", "ins_shown_30d"), info, basis=has("ins_shown_30d"),
              meaning="Cards shown with AI wording (the rest kept the app's words: AI off, unsure, or blocked by the check)", group="AI wording"),
    Criterion(INS, "AI-worded cards rated helpful", "≥ 70%", pooled("ins_ai_helpful_30d", "ins_ai_rated_30d"), ge(70), basis=has("ins_ai_rated_30d"),
              meaning="Of AI-worded cards people rated, the share marked Helpful", group="AI wording"),
    Criterion(INS, "App-worded cards rated helpful", "track",
              lambda cs: None if _sum(cs, "ins_rated_30d") - _sum(cs, "ins_ai_rated_30d") <= 0 else
              100.0 * (_sum(cs, "ins_helpful_30d") - _sum(cs, "ins_ai_helpful_30d")) / (_sum(cs, "ins_rated_30d") - _sum(cs, "ins_ai_rated_30d")),
              info, basis=has("ins_rated_30d"), meaning="The same for cards in the app's own words: the baseline AI should beat", group="AI wording"),
    Criterion(INS, "AI-worded cards hidden or not relevant", "≤ 20%", pooled("ins_ai_negative_30d", "ins_ai_shown_30d"), le(20),
              basis=has("ins_ai_shown_30d"), meaning="AI-worded cards shown that the person hid or marked Not relevant", group="AI wording"),
    # 4. Budgets
    Criterion(BUD, "Testers with a budget this month", "track", share(lambda c: int(c.get("budget_set") or 0) > 0), info),
    Criterion(BUD, "Still budgeting after a month", "track",
              share(lambda c: int(c.get("budget_months") or 0) >= 2, among=lambda c: int(c.get("budget_set") or 0) > 0 or int(c.get("budget_months") or 0) > 0),
              info, basis=has("budget_months"), meaning="Budget users with budgets in 2 or more months"),
    Criterion(BUD, "Category budgets over their limit", "track", pooled("budget_over", "budget_categories"), info, basis=has("budget_categories")),
    # Engagement
    Criterion(USE, "Open the app on ≥ 3 days a week", "≥ 60%", share(lambda c: int(c.get("active_days_7d") or 0) >= 3), ge(60),
              meaning="Share of testers"),
]


def _latest_per_install(rows: List[dict]) -> Dict[str, dict]:
    latest: Dict[str, dict] = {}
    for r in rows:
        if r["install_id"] not in latest or r["day"] > latest[r["install_id"]]["day"]:
            latest[r["install_id"]] = r
    return latest


def _fmt(v: Optional[float], unit: str) -> str:
    if v is None:
        return "—"
    if unit.strip() == "min" or (0 < v < 10 and round(v, 1) != round(v)):
        return f"{v:.1f}{unit}"  # 2.5% would read as a passing "2%"
    return f"{round(v)}{unit}"


def _sorted_all(c: dict) -> Optional[int]:
    """
    "Sorted so far" for one tester: payments with a category now, all time. Builds from 13 Nov 2026 send it as
    pay_sorted_all. Older builds don't, so it is worked out from counts they already send: every payment, minus the
    ones from before install still without a category, minus the last 30 days' still without one. Payments from
    before install that are also in the last 30 days can be taken off twice, so this errs low, never high.
    """
    if c.get("pay_all") is None:
        return None
    if c.get("pay_sorted_all") is not None:
        return int(c["pay_sorted_all"])
    if c.get("past_pay") is None or c.get("past_sorted") is None:
        # Oldest builds: categories the app placed on its own plus ones the user picked (misses later AI sorts, so low).
        if c.get("cat_auto") is None:
            return None
        return min(int(c["pay_all"]), int(c["cat_auto"]) + int(c.get("cat_picked") or 0))
    unsorted = (int(c["past_pay"]) - int(c["past_sorted"])) + int(c.get("spend_uncategorised_30d") or 0)
    return max(int(c["past_sorted"]), min(int(c["pay_all"]), int(c["pay_all"]) - unsorted))


def _month(current: List[dict]) -> dict:
    """The "Last 30 days" side of the Tracked card. Payments and sorted are exact counts every build already sends."""
    have = [c for c in current if c.get("spend_count_30d") is not None]
    sharing = [c for c in current if _rupees_30d(c) is not None]
    return {
        "payments": _sum(have, "spend_count_30d"),
        "sorted": _sum(have, "spend_count_30d") - _sum(have, "spend_uncategorised_30d") if have else None,
        "testers": len(have),
        "rupees": sum(_rupees_30d(c) for c in sharing) if len(sharing) >= MIN_TESTERS_FOR_TOTAL else None,
        "testers_sharing": len(sharing),
        "testers_estimated": sum(1 for c in sharing if c.get("paid_rupees_30d") is None),
    }


def _rupees_30d(c: dict) -> Optional[int]:
    """
    One tester's last-30-days total. Builds from 13 Nov 2026 send it (paid_rupees_30d). Older ones only send the
    90-day total, so it is estimated: that total times the share of their 90-day payments made in the last 30 days.
    Only for testers who agreed to share their total; still shown only summed across MIN_TESTERS_FOR_TOTAL testers.
    """
    if c.get("paid_rupees_30d") is not None:
        return int(c["paid_rupees_30d"])
    total, n_all, n_30 = c.get("paid_rupees_all"), c.get("pay_all"), c.get("spend_count_30d")
    if total is None or n_30 is None or not n_all:
        return None
    return round(int(total) * min(int(n_30), int(n_all)) / int(n_all))


def summary(days: int = 14) -> dict:
    rows = _rows()
    latest = _latest_per_install(rows)
    current = [r["counts"] for r in latest.values()]
    today = date.today()
    window = [(today - timedelta(days=i)).isoformat() for i in range(days - 1, -1, -1)]
    by_day = {d: [r["counts"] for r in rows if r["day"] == d] for d in window}
    versions = sorted({r["app_version"] for r in latest.values()})

    criteria = []
    for c in CRITERIA:
        v = c.compute(current)
        criteria.append({
            "feature": c.feature, "name": c.name, "meaning": c.meaning, "group": c.group, "target": c.target_text,
            "value": None if v is None else round(v, 1), "text": _fmt(v, c.unit),
            "status": "no data" if v is None else ("tracked" if c.passes is info else ("pass" if c.passes(v) else "fail")),
            "testers": c.basis(current),
            "trend": [None if not by_day[d] or c.compute(by_day[d]) is None else round(c.compute(by_day[d]), 1) for d in window],
            "by_version": {ver: _fmt(c.compute([r["counts"] for r in latest.values() if r["app_version"] == ver]), c.unit) for ver in versions},
        })

    testers = []
    for iid, r in sorted(latest.items(), key=lambda kv: kv[1]["day"], reverse=True):
        testers.append({
            "tester": "T-" + iid[:6], "app_version": r["app_version"], "last_report": r["day"],
            "reports": sum(1 for x in rows if x["install_id"] == iid),
            "values": [_fmt(c.compute([r["counts"]]), c.unit) for c in CRITERIA],
        })

    sharing = [c for c in current if c.get("paid_rupees_all") is not None]
    sorting = [c for c in current if _sorted_all(c) is not None]
    sorting_est = sum(1 for c in sorting if c.get("pay_sorted_all") is None)
    tracked = {
        "payments": _sum(current, "pay_all"),
        # "Sorted so far": only testers on a build that reports it, so older builds don't drag the share down.
        "sorted": sum(_sorted_all(c) for c in sorting) if sorting else None,
        "testers_estimated": sorting_est,
        "sorted_of": _sum(sorting, "pay_all"),
        "testers_sorting": len(sorting),
        "testers_total": len(current),
        "month": _month(current),
        "rupees": _sum(sharing, "paid_rupees_all") if len(sharing) >= MIN_TESTERS_FOR_TOTAL else None,
        "testers_sharing": len(sharing),
        "min_testers": MIN_TESTERS_FOR_TOTAL,
    }

    return {
        "generated": today.isoformat(),
        "tracked": tracked,
        "testers": len(latest),
        "active_7d": sum(1 for r in latest.values() if r["day"] >= (today - timedelta(days=6)).isoformat()),
        "rejected_uploads": _rejected_total(),
        "window": window,
        "versions": versions,
        "criteria": criteria,
        "tester_rows": testers,
    }


# ---------------------------------------------------------------------------------------------
# Dashboard
# ---------------------------------------------------------------------------------------------

def _team(creds: HTTPBasicCredentials = Depends(basic)) -> None:
    if not DASHBOARD_PASSWORD:
        raise HTTPException(status_code=503, detail="Set DASHBOARD_PASSWORD on the server to open the dashboard")
    if not secrets.compare_digest(creds.password.encode(), DASHBOARD_PASSWORD.encode()):
        raise HTTPException(status_code=401, detail="Wrong password", headers={"WWW-Authenticate": "Basic"})


@router.get("/dashboard.json")
def dashboard_json(_: None = Depends(_team)) -> dict:
    return summary()


def _spark(values: List[Optional[float]]) -> str:
    pts = [(i, v) for i, v in enumerate(values) if v is not None]
    if len(pts) < 2:
        return '<div class="spark-empty">Not enough days yet</div>'
    w, h = 220, 40
    lo, hi = min(v for _, v in pts), max(v for _, v in pts)
    span = (hi - lo) or 1
    step = w / (len(values) - 1)
    path = " ".join(f"{'M' if k == 0 else 'L'}{i * step:.1f},{h - 4 - (v - lo) / span * (h - 8):.1f}" for k, (i, v) in enumerate(pts))
    return f'<svg class="spark" viewBox="0 0 {w} {h}" preserveAspectRatio="none" aria-hidden="true"><path d="{path}"/></svg>'


def _card(c: dict) -> str:
    e = html.escape
    meaning = f'<div class="meaning">{e(c["meaning"])}</div>' if c.get("meaning") else ""
    return f"""
        <article class="card">
          <h3>{e(c['name'])}</h3>{meaning}
          <div class="row"><span class="value">{e(c['text'])}</span>
            <span class="badge {c['status'].replace(' ', '-')}">{e({'pass': 'Pass', 'fail': 'Below target', 'tracked': 'Tracked', 'no data': 'No data yet'}[c['status']])}</span></div>
          <div class="meta">Target {e(c['target'])} · {c['testers']} {'tester' if c['testers'] == 1 else 'testers'}</div>
          {_spark(c['trend'])}
        </article>"""




def _inr(n: int) -> str:
    """12345678 -> 1,23,45,678 (Indian grouping)."""
    d = str(n)
    if len(d) <= 3:
        return d
    head, tail = d[:-3], d[-3:]
    groups = []
    while len(head) > 2:
        groups.insert(0, head[-2:])
        head = head[:-2]
    return ",".join(([head] if head else []) + groups) + "," + tail


def _tracked_html(t: dict) -> str:
    if t["rupees"] is not None:
        amount = f"₹{_inr(t['rupees'])}"
        note = f"spent and tracked, last 90 days on each phone · {t['testers_sharing']} testers sharing their total"
    else:
        amount = "—"
        note = f"total shows once {t['min_testers']} testers share it ({t['testers_sharing']} so far), so no one's spending can be picked out"
    return f"""<div class="period">
<input type="radio" name="period" id="p30" checked><label for="p30">Last 30 days</label>
<input type="radio" name="period" id="p90"><label for="p90">Last 90 days</label>
{_month_html(t)}
<div class="tracked p90">
  <div><div class="kicker">Tracked so far</div><div class="hero">{amount}</div><div class="meta">{html.escape(note)}</div></div>
  <div><div class="kicker">Payments</div><div class="hero">{_inr(t['payments'])}</div><div class="meta">counted as spent, all testers, last 90 days</div></div>
  {_sorted_html(t)}
</div>
</div>"""


# Beside a hero number worked out from older reports. Each tester's part turns exact once their phone sends the real one.
EST_TAG = '<span class="est">est.</span>'


def _month_html(t: dict) -> str:
    m, e = t["month"], html.escape
    n = f"{m['testers']} of {t.get('testers_total', m['testers'])} testers"
    if m["rupees"] is not None:
        est = m.get("testers_estimated", 0)
        amount = f"₹{_inr(m['rupees'])}" + (EST_TAG if est else "")
        note = (f"spent and tracked, last 30 days · {m['testers_sharing']} testers sharing their total"
                + (f" ({est} estimated from their 90-day total by number of payments)" if est else ""))
    else:
        amount, note = "—", (f"shows once {t['min_testers']} testers share it ({m['testers_sharing']} so far), "
                             "so no one's spending can be picked out")
    if m["sorted"] is None:
        sorted_v, sorted_note = "—", "no tester has reported their payments yet"
    else:
        share = f" · {round(m['sorted'] * 100 / m['payments'])}%" if m["payments"] else ""
        sorted_v, sorted_note = _inr(m["sorted"]), f"of {_inr(m['payments'])} payments have a category{share} · {n}"
    return f"""<div class="tracked p30">
  <div><div class="kicker">Tracked so far</div><div class="hero">{amount}</div><div class="meta">{e(note)}</div></div>
  <div><div class="kicker">Payments</div><div class="hero">{_inr(m['payments'])}</div><div class="meta">{e('counted as spent, last 30 days · ' + n)}</div></div>
  <div><div class="kicker">Sorted so far</div><div class="hero">{sorted_v}</div><div class="meta">{e(sorted_note)}</div></div>
</div>"""


def _sorted_html(t: dict) -> str:
    """"Sorted so far": payments with a category now, last 90 days on each phone, beside "Tracked so far"."""
    if t["sorted"] is None:
        value, note = "—", f"none of {t.get('testers_total', 0)} testers has reported their payments yet"
    else:
        share = f" · {round(t['sorted'] * 100 / t['sorted_of'])}%" if t["sorted_of"] else ""
        n = t["testers_sorting"]
        value = _inr(t["sorted"])
        est = t.get("testers_estimated", 0)
        if est:
            value += EST_TAG
        note = (f"of {_inr(t['sorted_of'])} payments have a category{share} · {n} of {t.get('testers_total', n)} testers"
                + (f" ({est} worked out from older reports, may run slightly low)" if est else ""))
    return f'<div><div class="kicker">Sorted so far</div><div class="hero">{value}</div><div class="meta">{html.escape(note)}</div></div>'


INSIGHT_KINDS = [
    ("change", "Category up or down vs last period"),
    ("share", "Category's share of spending"),
    ("repeat", "Paid the same place often"),
    ("weekend", "Weekends vs weekdays"),
    ("concentration", "Spending bunched on a few days"),
]


def _insight_kinds_html() -> str:
    """Each tester's latest report, summed per insight kind: which kinds earn their place on the screen."""
    e = html.escape
    tot: Dict[str, Dict[str, int]] = {}
    for r in _latest_per_install(_rows()).values():
        for k, c in (r["counts"].get("ins_kinds") or {}).items():
            t = tot.setdefault(k, {"shown": 0, "opened": 0, "helpful": 0, "not_relevant": 0, "dismissed": 0})
            for f in t:
                t[f] += int(c.get(f) or 0)
    pct = lambda x, y: "—" if not y else f"{round(100 * x / y)}%"  # noqa: E731
    rows = []
    for key, label in INSIGHT_KINDS:
        t = tot.get(key, {})
        shown, rated = t.get("shown", 0), t.get("helpful", 0) + t.get("not_relevant", 0) + t.get("dismissed", 0)
        rows.append(f"<tr><td class='sticky'>{e(label)}</td><td>{shown}</td><td>{pct(t.get('opened', 0), shown)}</td>"
                    f"<td>{rated}</td><td>{pct(t.get('helpful', 0), rated)}</td><td>{t.get('not_relevant', 0)}</td><td>{t.get('dismissed', 0)}</td></tr>")
    return ("<h3 class='group'>By insight kind</h3><p class='sub'>Cards first shown in each tester's last 30 days. "
            "A kind that's often hidden or rarely opened is a candidate to change or drop.</p>"
            "<div class='table'><table><thead><tr><th class='sticky'>Insight</th><th>Shown</th><th>Opened</th><th>Rated</th>"
            f"<th>Helpful</th><th>Not relevant</th><th>Hidden</th></tr></thead><tbody>{''.join(rows)}</tbody></table></div>"
            "<h3 class='group'>AI quality on the golden dataset</h3><div class='card'><p class='sub' style='margin:0'>The app works out "
            "every number; the AI only words the card, and a plain-code check keeps any wording with a new number or a cause, "
            "prediction or advice word off the screen. The 15 golden cases (INS-001 to 015) are scored on the "
            "<a href='#ai-evaluation'>AI evaluation</a> page.</p></div>")


def _slug(name: str) -> str:
    return "".join(ch if ch.isalnum() else "-" for ch in name.lower()).strip("-")


@router.get("/dashboard", response_class=HTMLResponse)
def dashboard(_: None = Depends(_team)) -> str:
    started = time.time()
    try:
        return _dashboard_html()
    finally:
        print(f"dashboard ms={round((time.time() - started) * 1000)}", flush=True)  # shows in Vercel's logs


def _dashboard_html() -> str:
    s = summary()
    e = html.escape
    features = FEATURE_ORDER + [f for f in dict.fromkeys(c.feature for c in CRITERIA) if f not in FEATURE_ORDER]
    no_reports = "No reports yet. Testers' phones send one a day once they agree."

    nav, pages = [], []
    for n, feat in enumerate(features, 1):
        idx = [i for i, c in enumerate(CRITERIA) if c.feature == feat]
        crit = [s["criteria"][i] for i in idx]
        judged = [c for c in crit if c["status"] in ("pass", "fail")]
        passed = sum(1 for c in judged if c["status"] == "pass")
        score = f"{passed}/{len(judged)} pass" if judged else ("tracked only" if any(c["status"] == "tracked" for c in crit) else "no data yet")
        slug = _slug(feat)
        nav.append(f'<a href="#{slug}" data-page="{slug}"><span class="num">{n}</span><span class="name">{e(feat)}<small>{score}</small></span></a>')

        body = []
        for group in dict.fromkeys(c["group"] for c in crit):
            if group:
                body.append(f"<h3 class='group'>{e(group)}</h3>")
            body.append('<div class="grid">' + "".join(_card(c) for c in crit if c["group"] == group) + "</div>")

        head = "".join(f"<th>{e(CRITERIA[i].name)}</th>" for i in idx)
        rows = "".join(
            f"<tr><td class='sticky'>{e(t['tester'])}</td><td>{e(t['app_version'])}</td><td>{e(t['last_report'])}</td>"
            + "".join(f"<td>{e(t['values'][i])}</td>" for i in idx) + "</tr>"
            for t in s["tester_rows"]
        ) or f"<tr><td colspan='{len(idx) + 3}'>{no_reports}</td></tr>"
        body.append(f"<h3 class='group'>Per tester</h3><div class='table'><table><thead><tr><th class='sticky'>Tester</th><th>Version</th><th>Last report</th>{head}</tr></thead><tbody>{rows}</tbody></table></div>")
        top = _tracked_html(s["tracked"]) if feat == PAY else ""
        if feat == INS:
            body.insert(len(body) - 1, _insight_kinds_html())
        pages.append(f'<section class="page" id="{slug}"><div class="kicker">Feature {n}</div><h2>{e(feat)}</h2>{top}{"".join(body)}</section>')

    # AI evaluation on the golden dataset (evals.py).
    import evals  # here, not at the top: evals imports this module
    ev_small, ev_page = evals.page_html()
    nav.append(f'<a href="#ai-evaluation" data-page="ai-evaluation"><span class="num">AI</span><span class="name">AI evaluation<small>{e(ev_small)}</small></span></a>')
    pages.append(ev_page)

    # Testers page: everyone, and every criterion by app version.
    trs = "".join(
        f"<tr><td class='sticky'>{e(t['tester'])}</td><td>{e(t['app_version'])}</td><td>{e(t['last_report'])}</td><td>{t['reports']}</td></tr>"
        for t in s["tester_rows"]
    ) or f"<tr><td colspan='4'>{no_reports}</td></tr>"
    vhead = "".join(f"<th>{e(v)}</th>" for v in s["versions"])
    vrows = "".join(
        f"<tr><td class='sticky'>{e(c['feature'])} · {e(c['name'])}</td>" + "".join(f"<td>{e(c['by_version'][v])}</td>" for v in s["versions"]) + "</tr>"
        for c in s["criteria"]
    )
    nav.append(f'<a href="#testers" data-page="testers"><span class="num">·</span><span class="name">Testers<small>{s["testers"]} total</small></span></a>')
    pages.append(f"""<section class="page" id="testers"><h2>Testers</h2>
<div class="table"><table><thead><tr><th class="sticky">Tester</th><th>Version</th><th>Last report</th><th>Reports</th></tr></thead><tbody>{trs}</tbody></table></div>
<h3 class="group">By app version</h3>
<div class="table"><table><thead><tr><th class="sticky">Criterion</th>{vhead}</tr></thead><tbody>{vrows}</tbody></table></div>
<p class="sub">Testers are random install IDs, not linked to a name, email or phone number.</p></section>""")

    return f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Tester metrics</title>
<style>
:root {{ --bg:#F4F4F6; --surface:#FFFFFF; --raised:#E9E9ED; --text:#0B0B0D; --muted:#5C5C64; --accent:#2D5FC4; --pos:#0F7040; --neg:#C62828; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg:#0B0B0D; --surface:#1C1C1F; --raised:#2A2A2E; --text:#F4F4F6; --muted:#A1A1A8; --accent:#98BBF4; --pos:#3DD68C; --neg:#FF6B6B; }} }}
* {{ box-sizing:border-box }}
body {{ margin:0; background:var(--bg); color:var(--text); font:14px/1.45 system-ui,-apple-system,"Segoe UI",sans-serif; }}
.layout {{ display:grid; grid-template-columns:260px 1fr; min-height:100vh }}
aside {{ background:var(--surface); padding:24px 14px; position:sticky; top:0; height:100vh; overflow-y:auto }}
aside .brand {{ font-size:18px; font-weight:600; padding:0 10px }}
aside .brand small {{ display:block; color:var(--muted); font-size:12px; font-weight:400; margin:2px 0 18px }}
aside a {{ display:flex; gap:12px; align-items:center; padding:10px; border-radius:14px; color:var(--text); text-decoration:none; margin-bottom:4px }}
aside a:hover {{ background:var(--raised) }}
aside a.on {{ background:var(--raised); font-weight:600 }}
aside .num {{ width:26px; height:26px; border-radius:999px; background:var(--bg); display:grid; place-items:center; font-size:12px; flex:none }}
aside a.on .num {{ background:var(--accent); color:var(--bg) }}
aside small {{ display:block; color:var(--muted); font-size:12px; font-weight:400 }}
aside .stats {{ margin-top:22px; padding:0 10px; color:var(--muted); font-size:12px; line-height:1.9 }}
aside .stats b {{ color:var(--text) }}
main {{ padding:28px 28px 48px; max-width:1200px; min-width:0 }}
.kicker {{ color:var(--muted); font-size:12px; text-transform:uppercase; letter-spacing:.06em }}
h2 {{ font-size:26px; margin:2px 0 8px; font-weight:600 }}
h3.group {{ font-size:15px; color:var(--muted); font-weight:600; margin:26px 0 10px }}
.sub {{ color:var(--muted) }}
.grid {{ display:grid; grid-template-columns:repeat(auto-fill,minmax(260px,1fr)); gap:12px }}
.card {{ background:var(--surface); border-radius:24px; padding:18px 20px }}
.card h3 {{ font-size:15px; margin:0 0 10px; font-weight:600 }}
.meta {{ color:var(--muted); font-size:12px }}
.tracked {{ display:flex; flex-wrap:wrap; gap:12px 48px; background:var(--surface); border-radius:24px; padding:20px 24px; margin:14px 0 4px }}
.period {{ margin:14px 0 4px }}
.period input {{ position:absolute; opacity:0; pointer-events:none }}
.period label {{ display:inline-block; font-size:13px; font-weight:500; padding:6px 14px; border-radius:999px; background:var(--raised); color:var(--muted); cursor:pointer; margin:0 6px 10px 0 }}
.period input:checked + label {{ background:var(--accent); color:var(--bg) }}
.period input:focus-visible + label {{ outline:2px solid var(--accent); outline-offset:2px }}
.period .tracked {{ margin:0 }}
#p30:not(:checked) ~ .p30, #p90:not(:checked) ~ .p90 {{ display:none }}
.tracked .est {{ font-size:14px; font-weight:500; color:var(--muted); background:var(--raised); border-radius:999px; padding:3px 10px; margin-left:10px; vertical-align:middle }}
.tracked .hero {{ font-size:40px; font-weight:600; line-height:1.15; margin:4px 0 2px }}
.why {{ white-space:normal; min-width:260px; color:var(--muted) }}
ul.crit {{ margin:0; padding-left:18px; line-height:1.8 }}
code {{ background:var(--raised); padding:1px 6px; border-radius:6px }}
.meaning {{ color:var(--muted); font-size:13px; margin:-6px 0 10px }}
.row {{ display:flex; align-items:center; justify-content:space-between; gap:8px }}
.value {{ font-size:30px; font-weight:600 }}
.badge {{ font-size:12px; font-weight:600; padding:3px 10px; border-radius:999px; background:var(--raised) }}
.badge.pass {{ color:var(--pos) }} .badge.fail {{ color:var(--neg) }}
.spark {{ width:100%; height:40px; margin-top:10px }} .spark path {{ fill:none; stroke:var(--accent); stroke-width:2 }}
.spark-empty {{ color:var(--muted); font-size:12px; margin-top:10px }}
.table {{ overflow-x:auto; background:var(--surface); border-radius:24px }}
table {{ border-collapse:collapse; width:100%; font-size:13px }}
th, td {{ padding:10px 12px; text-align:left; white-space:nowrap; border-bottom:1px solid var(--raised) }}
th {{ color:var(--muted); font-weight:600; background:var(--surface) }}
.sticky {{ position:sticky; left:0; background:var(--surface); font-weight:600 }}
.js .page {{ display:none }} .js .page.on {{ display:block }}
.page + .page {{ margin-top:40px }} .js .page + .page {{ margin-top:0 }}
@media (max-width: 760px) {{
  .layout {{ grid-template-columns:1fr }}
  aside {{ position:sticky; height:auto; padding:10px 16px; display:flex; gap:6px; overflow-x:auto; z-index:1 }}
  aside .brand, aside .stats {{ display:none }}
  aside a {{ flex:none; margin:0 }}
  main {{ padding:20px 16px 40px }}
}}
</style></head><body><div class="layout">
<aside>
  <div class="brand">Tester metrics<small>Counts only, no personal data · {e(s['generated'])}</small></div>
  {''.join(nav)}
  <div class="stats"><b>{'₹' + _inr(s['tracked']['rupees']) if s['tracked']['rupees'] is not None else '—'}</b> tracked so far<br><b>{_inr(s['tracked']['payments'])}</b> payments<br><b>{s['testers']}</b> testers<br><b>{s['active_7d']}</b> reported in the last 7 days<br><b>{s['rejected_uploads']}</b> uploads rejected (target 0)</div>
</aside>
<main>{''.join(pages)}
<p class="sub">Each value pools every tester's latest daily report. Trend lines show the last 14 days.</p></main>
</div>
<script>
document.body.classList.add('js');
function show() {{
  var id = location.hash.slice(1), pages = document.querySelectorAll('.page');
  if (!document.getElementById(id)) id = pages[0].id;
  pages.forEach(function (p) {{ p.classList.toggle('on', p.id === id); }});
  document.querySelectorAll('aside a').forEach(function (a) {{ a.classList.toggle('on', a.dataset.page === id); }});
}}
window.addEventListener('hashchange', function () {{ show(); window.scrollTo(0, 0); }});
show();
</script>
</body></html>"""
