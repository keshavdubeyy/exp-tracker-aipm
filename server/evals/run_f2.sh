#!/bin/sh
# Golden dataset, Feature 2 (Insights): /explain on the 15 cases -> checks + judge -> dashboard's AI evaluation page.
#   cd server && BASE=https://expense-tracker-metrics.vercel.app TESTER_KEY=... DASHBOARD_PASSWORD=... evals/run_f2.sh
set -e
cd "$(dirname "$0")/.."
.venv/bin/python evals/run_f2.py
