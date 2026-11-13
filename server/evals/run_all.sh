#!/bin/sh
# Golden dataset, Feature 1: AI layer -> app layer -> upload to the dashboard's AI evaluation page.
#   cd server && BASE=https://expense-tracker-metrics.vercel.app TESTER_KEY=... DASHBOARD_PASSWORD=... evals/run_all.sh
set -e
cd "$(dirname "$0")/.."
.venv/bin/python evals/run_f1.py
( cd .. && JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home} \
    ./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --tests '*GoldenF1AppLayerTest*' -q )
.venv/bin/python evals/upload.py
