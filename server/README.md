# Backend (categorise)

Suggests up to 3 categories for a payee the app couldn't place. It only picks from the list the app sends,
never returns a number, and never receives SMS text. Logs hold counts and timings only.

## Run on your laptop

```sh
cd server
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
# Without a key it runs in fake mode (no cost) - good for building and testing.
FAKE_AI=1 TESTER_KEY=local-test .venv/bin/uvicorn app:app --host 127.0.0.1 --port 8000
curl http://127.0.0.1:8000/health
```

Real AI: set the key **in the server's environment only** (never in the app, never in Git):

```sh
export ANTHROPIC_API_KEY=...        # from console.anthropic.com
export TESTER_KEY=some-long-random-string
.venv/bin/uvicorn app:app --host 0.0.0.0 --port 8000
```

`CATEGORISER_MODEL` picks the model (default `claude-haiku-5-5`, fast and cheap; `claude-opus-5-5` to compare quality).

## Point the app at it

In `local.properties` (not committed):

```
ai.baseUrl=http://localhost:8000      # emulator: also run `adb reverse tcp:8000 tcp:8000`
ai.testerKey=local-test               # must match TESTER_KEY
```

Rebuild (`./gradlew :app:installFast`). Profile > tap "Version" 7 times > Developer shows "AI categoriser: on".
Leave `ai.baseUrl` empty to turn AI off.

## Deploy for testers

Any host that runs a Python web app works (Render, Railway, Fly.io). Start command:
`uvicorn app:app --host 0.0.0.0 --port $PORT`. Set `ANTHROPIC_API_KEY` and `TESTER_KEY` as the host's
secret env vars, then use the HTTPS URL as `ai.baseUrl` in the tester build.

## Deploy on Vercel (server + dashboard)

Vercel's disk is wiped between requests, so reports go to Postgres (Neon) when `DATABASE_URL` is set.
On your laptop nothing changes: SQLite in `metrics.db`.

1. `cd server && vercel link` (project `expense-tracker-metrics`).
2. Vercel dashboard > project > **Storage > Create > Neon (free)** > connect to all environments.
   This sets `DATABASE_URL` for you.
3. Settings > Environment Variables: `ANTHROPIC_API_KEY`, `TESTER_KEY` (same as `ai.testerKey`),
   `DASHBOARD_PASSWORD` (long and random: the dashboard is now on the internet).
4. `vercel deploy --prod`, then open `https://<project>.vercel.app/dashboard`.
5. In `local.properties` set `ai.baseUrl=https://<project>.vercel.app`, rebuild, send the build through Firebase.

## Tester metrics and the team dashboard (MVP M24)

Every tester phone that agreed sends **one counts-only report a day** (plus, after consent v2, one all-time ₹ total that the dashboard only shows summed across at least 3 testers) to `POST /metrics` (scrambled tester ID, same across reinstalls,
app version, day, and the counts listed in `metrics.py`). Unknown fields are rejected and counted, so SMS text,
names or amounts can't arrive even by mistake. Reports are kept in SQLite (`METRICS_DB`, default `server/metrics.db`).

Open **`/dashboard`** in a browser (any user name, password = `DASHBOARD_PASSWORD`) to see:
- every success criterion from MVP.md M24 across all testers: value, target, pass / below target, number of testers, 14-day trend;
- a table with one row per tester (`T-xxxxxx`), same criteria;
- the same criteria split by app version.

`/dashboard.json` gives the same numbers as JSON. Every number is plain Python over the counts, never AI.

```sh
export DASHBOARD_PASSWORD=pick-a-team-password
FAKE_AI=1 TESTER_KEY=local-test DASHBOARD_PASSWORD=$DASHBOARD_PASSWORD .venv/bin/uvicorn app:app --host 127.0.0.1 --port 8000
open http://127.0.0.1:8000/dashboard
```

The phone uploads to `metrics.baseUrl` in `local.properties` (defaults to `ai.baseUrl`), with the same `ai.testerKey`.
Phone over USB: `adb reverse tcp:8000 tcp:8000`, then Profile › Version 7× › Developer › **Send now**.

**For real testers** the server must be online all the time: deploy it (see "Deploy for testers"), add
`DASHBOARD_PASSWORD` to the host's secret env vars, and use a **persistent disk** for `METRICS_DB`
(free hosts often wipe local files on restart). Share the `/dashboard` link and password with the team.
To add a criterion: add its count to `Counts` in `metrics.py` and `buildCounts` in the app's
`metrics/MetricsUpload.kt` together, then add a line to `CRITERIA`.

## AI evaluation on the golden dataset

The dashboard's **AI evaluation** page scores the categoriser on the 25 Feature 1 cases of Golden Dataset v1.0
(`evals/golden_f1_v1.0.json`, extracted from the team workbook). Two layers, as the dataset asks:

1. **AI layer** (`evals/run_f1.py`): each case goes to `/categorise` exactly as the app sends it.
2. **App layer** (`GoldenF1AppLayerTest.kt`): the AI's real answers go through the app's own `Categoriser`
   (shop words, thresholds), recording whether the app files a category, offers guesses, or asks.

```sh
cd server
BASE=https://expense-tracker-metrics.vercel.app TESTER_KEY=... DASHBOARD_PASSWORD=... evals/run_all.sh
```

The run is uploaded to `POST /evals/runs` (team password) and appears on the dashboard with every case, the
critical failures and the run log. Targets are proposed until the team approves them (`TARGETS` in `evals.py`).
`/health` reports the prompt version, so runs before and after a prompt change can be told apart.
Run `GOLDEN_STRICT=1 ./gradlew :app:testDebugUnitTest` to make a critical failure fail the build.
