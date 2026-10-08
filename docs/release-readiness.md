# STEP 11 verification record

Verified on 2026-10-07–08, Windows / Docker Desktop Linux containers. This records
completed local verification and successful GitHub Actions CI on the main branch.
The reviewed changes have been committed and pushed. No release tag or cloud
deployment has been performed; unrelated third-party PC setup remains unverified.

## Regression results

| Check | Result | Evidence |
|---|---|---|
| Python | PASS: 183, failures/skips 0; core coverage 90.31% | Test-image `uv sync --locked`; `uv run pytest --cov=app/core --cov-report=term --cov-fail-under=90` |
| Python syntax | PASS | Test-image `uv run python -m compileall -q app tests` |
| Java | PASS: 431, failures/errors/skips 0; BUILD SUCCESS | `backend/.\mvnw.cmd verify`, Surefire reports, `scripts/check-java-reports.mjs` |
| MySQL/Flyway | PASS | `MySqlPersistenceTests`, `MySqlRepositoryAdapterTests`: eight tables, FK/UNIQUE/CHECK/PK, UTC, seed, mapping, rollback, restart |
| Frontend | PASS: 35, failures/skips 0 | `npm test`; lock-based `npm ci --ignore-scripts` |
| Playwright | PASS: 14, failures/skips/flaky 0 | Existing A–L maintained; M database outage/recovery, N public JSON boundary |
| Compose | PASS | Fresh normal-provider startup without market fetch; fresh offline deployment; all three healthy, only backend loopback published |
| Git/data safety | PASS for current files | Ignore probes, known credential signatures/local credential values, only artificial CSV and canonical Flyway SQL tracked |
| E2E artifact safety | PASS | Project credential byte scan, including decompressed ZIP members when present; synthetic screenshots visually reviewed |
| GitHub Actions execution | PASS | main branch; `python-contract`, `java-mysql`, `frontend-security`, `offline-e2e` all succeeded; Workflow Status: Success |

Python ran inside its disposable Docker test image because the host's existing
Python venv references an unavailable base interpreter. This is an environment
limitation, not a skipped test. The image uses the committed uv lock, tests and
frozen internal OpenAPI. No external market provider is used by regression/CI.
No Python lint tool was configured; compileall is a syntax check, not a lint substitute.

The only added automated cases are 18 Java request-boundary cases and two real
E2E cases. Existing numerical models, Python code, schema and frozen contract
were retained. Float comparison uses the existing design tolerance, 1e-9.

## Explicit real-provider check (once only)

`scripts/manual-yfinance-check.mjs --run-once` used registered `7203 / 7203.T`
over 2025-05-01–2025-06-30, through the real FastAPI internal fetch/analyze
handlers and existing YFinanceProvider. This check used TestClient for HTTP
routing; it did not call Java persistence. Full Java/FastAPI TCP integration is
separately covered by the offline Compose E2E.

- Provider calls: exactly **1**, without manual retry.
- Provider shape: **42 rows**, Close and Adj Close present.
- prices/fetch: **200**, 2.067 seconds including application initialization.
- XTKS 4.13.2, Asia/Tokyo, exact sessions and preceding session: verified.
- Positive, ten-decimal adjustedClose: verified.
- analyze: **200**, **41 states / 40 transitions**, AVAILABLE, msa-core-v1.
- Download timeout argument: **5 seconds**; FastAPI fetch deadline: **12 seconds**.
- Real price values stayed in memory in a disposable container. No file, host
  mount, Java dataset/result save, screenshot or repository price dump was made.

This proves one successful connection, not ongoing provider availability,
point-in-time correctness or redistribution permission.

## Abnormal-case review

| Case | Evidence retained / added |
|---|---|
| Invalid stock/condition, dataset mismatch, old series engine | Java controller/service/client tests: 404/409/422; no replacement provider fetch |
| ±0.5% boundaries, insufficient states | Python classifier/returns/API tests: exact boundaries FLAT; fewer than 30 states rejected |
| DATA_GAP, preceding price, zero rows / UNAVAILABLE | Preprocess/analyze tests; E2E partial display; null row and empty forecasts preserved |
| Provider failure / timeout / overload | Python/Java integration and fake HTTP tests: 502/504/429; actual offline delayed-provider E2E |
| Stopped FastAPI | Transport tests and real E2E J: safe 503 |
| Stopped MySQL | Real E2E M: safe PERSISTENCE_SERVICE_UNAVAILABLE 503; saved result unchanged after recovery |
| Invalid paging | Existing history/backtest tests: 400, empty and large-offset pages |
| >5 years / >1300 states / >1000 candidates | Existing Java/Python limits and resource tests; actual E2E 422 |
| >5MiB / non-JSON / external origin / hostile Host | Python limits; 18 new Java filter cases, E2E N verifies 413/415/403 before save |
| Credential / path / SQL / stack disclosure | Existing mapper/error tests, safe DB 503 E2E, textContent tests, repository/artifact checks |
| Duplicate submit / browser timeout / POST retries | Frontend tests and real E2E L: waiting abort, one attempt, no automatic retry |

## MVP acceptance (design chapter 28)

| ID | Condition | Status | Evidence |
|---|---|---|---|
| AC01 | Select a registered stock, period and thresholds | PASS | E2E A/B; condition form and server validation |
| AC02 | Explain basis, sessions and preceding price | PASS | README/UI, calendar/preprocess tests, real-provider check |
| AC03 | Three states/counts/matrix match hand calculation | PASS | Python transition/Markov and pipeline tests |
| AC04 | 1/3/5/10 horizons; explicit stop on unestimated rows | PASS | Python/Java invariants, E2E B/C |
| AC05 | Trace current state, matrix and probabilities in UI | PASS | Synthetic analysis-success/partial screenshots and E2E |
| AC06 | Next-day backtest without future leakage | PASS | Future-price-modification tests, E2E E predictions |
| AC07 | Accuracy, state precision, confusion, coverage | PASS | Metrics tests and E2E E |
| AC08 | Baselines and probability metrics | PASS | Same-target baseline/metrics tests, saved backtest UI |
| AC09 | Persist condition/price version; history after restart | PASS | MySQL adapters/transactions, E2E G and M |
| AC10 | Java owns public analysis; Python has no DB connection | PASS | Compose topology/config review and real offline API integration |
| AC11 | Invalid stock, gaps, zero rows, stopped services | PASS | Abnormal-case tests above, E2E C/I/J/K/M |
| AC12 | No .env or real-market dumps in Git candidates | PASS | Current Git inventory, ignore rules, local secret/signature and artifact checks |
| AC13 | Another person can start via README | NOT VERIFIED | GitHub clone, fresh DB, normal startup/health and offline E2E verified on this PC; unrelated third-party PC not exercised |
| AC14 | Disclaimers, model limits, future features | PASS | README and four-screen review |

**MVP criteria: PASS 13 / FAIL 0 / NOT VERIFIED 1.**

## Publication boundary / remaining work

The local MVP is verified. The reviewed changes have been published, and GitHub
Actions CI has succeeded on the main branch. The remaining final acceptance
check is for an unrelated third party to reproduce the README/setup procedures.
Current-file scanning is not a full historical secret audit or a guarantee that
all possible secret formats are detected.

Internet service deployment remains outside scope: authentication, ownership,
TLS, abuse controls, separated migration/runtime DB permissions and market-data
usage rights need their own review. The software MIT license does not license
market data. An accuracy/profit target is not an acceptance criterion.

Detailed local logs, Surefire reports and E2E screenshots/reports are ignored
artifacts, not committed attachments. Ordinary shutdown retains MySQL data;
the volume-deleting example is tested only against a newly created disposable
verification project. The normal and offline verification stacks are stopped
at the end, with their data volumes retained.
