# Local integration setup

This application is for research and education, not investment advice. It does
not implement trading, profit evaluation, login or cloud deployment.

## Requirements

- Git
- Docker Desktop with Linux containers and the WSL2 backend on Windows
- Docker Compose v2 or newer (included with Docker Desktop)
- Node.js 22 or newer only when running frontend/Playwright tests on the host

## Configure and start

From the repository root, copy `.env.example` to `.env` locally:

```powershell
Copy-Item .env.example .env
```

Edit `.env`. Supply three independent random secrets for `MYSQL_PASSWORD`,
`MYSQL_ROOT_PASSWORD` and `INTERNAL_API_TOKEN`; do not reuse the example blanks.
Generate them with a trusted local password generator. Never commit `.env` or
paste secrets into logs/issues. `MYSQL_DATABASE` and `MYSQL_USER` identify the
application account, not the MySQL root account.

`MARKET_CALENDAR_VERSION` must match `exchange-calendars` in `analysis/uv.lock`
(currently `4.13.2`). Updating that dependency also requires updating this value.

```powershell
docker compose up --build -d
docker compose ps
```

Compose waits for authenticated FastAPI health and an application-user MySQL
query before starting Spring Boot. Spring Boot then applies Flyway migrations,
validates the JPA mappings and reads the seeded stocks. Backend health is
`GET /api/stocks`, which exercises the public HTTP/DB read path.

Open **http://127.0.0.1:8080/**. The four pages are `/index.html`,
`/analysis.html`, `/backtest.html`, `/history.html`. `GET /api/stocks` should
contain the registered stocks `7203` and `9001`. `BACKEND_PORT` may be changed
if 8080 is already occupied.

```powershell
docker compose logs --tail 100 backend
docker compose logs --tail 100 analysis
docker compose logs --tail 100 mysql
```

Only the backend port is bound to host loopback. MySQL and FastAPI are not
published to the host. Python has no database configuration and does not join
the database network. Frontend is copied from its sole source `frontend/` by
Maven and served by Spring Boot; there is no frontend container.

The default provider is YFinance. Fetching actual prices is an **explicit manual
operation** through the analysis form, never part of the automated tests.

## Stop and retain data

```powershell
docker compose down
```

Normal stopping **does not delete the named MySQL volume**. Start again with the
same `.env` and the saved conditions/results/history remain available. Changing
the initial MySQL passwords in `.env` does not change passwords already stored
inside an initialized database volume.

Only when you intentionally want to delete all saved data:

```powershell
docker compose down -v
```

## Offline browser E2E

The E2E runner creates a uniquely named Compose project, uses loopback port
8081, and generates temporary secrets only in process/container environments.
It does not create `.env`. First it verifies normal-provider startup without
requesting market prices. Then it retains the MySQL volume, starts the explicit
`compose.e2e.yaml` overlay and uses the existing `FixtureProvider`.

The overlay disables outbound network access for the analysis network. The
synthetic CSV uses the engine's exact XTKS calendar, includes a preceding
session, and exercises both estimated/unestimated rows and scored/skipped
backtest days. Its provider is displayed as `FIXTURE`, not YFinance.

```powershell
cd tests/e2e
npm ci
npx playwright install chromium
npm run test:integration
```

The runner leaves its isolated offline stack running for inspection. The project
name and URL are in `tests/e2e/artifacts/run-summary.json`, with no secret values.
To rerun tests against that same running synthetic stack, use `node run.mjs
--reuse`. It reads credentials only from its own project containers into memory;
it does not export them. Add `--rebuild` when application sources changed.
To stop that last E2E stack without deleting its volume:

```powershell
node run.mjs --stop
```

To deliberately delete only its synthetic test volume as well:

```powershell
node run.mjs --stop --remove-test-volume
```

E2E secrets are intentionally ephemeral; the next integration run uses a new
project/volume. Production data and the normal Compose project are not touched.
Do not try to reuse a stopped synthetic test volume with newly generated
credentials. `E2E_PORT` can override 8081.

Playwright runs a real Chromium browser with the actual Spring/FastAPI/MySQL
API path. Tests cover success, partial results, saved GET-only views, backtest,
history/paging, backend restart, mobile width and errors. The provider timeout
uses a delayed offline fixture. The browser timeout test holds a POST at the
browser boundary and advances its 35-second clock; it does not fabricate a
successful backend response or automatically retry the POST.

Screenshots, traces and HTML/JSON reports live under `tests/e2e/artifacts/` and
are ignored by Git. Screenshots include analysis success, partial analysis,
backtest result and history. They are review artifacts, not pixel comparisons.

## Regression tests

```powershell
cd analysis
uv run pytest
cd ../backend
.\mvnw.cmd test
cd ../frontend
npm ci
npm test
```

Java Testcontainers tests require Docker Desktop. No external price provider is
used by the Python, Java, frontend or E2E test suites. The Python tests can also
run in their dedicated Docker build target without installing host Python/uv:

```powershell
docker build -f analysis/Dockerfile --target test -t markov-stock-analyzer-analysis-tests:local .
docker run --rm markov-stock-analyzer-analysis-tests:local uv run --frozen pytest
```

The executable schema remains exclusively in
`backend/src/main/resources/db/migration/`. No schema copy or redesign is part
of this integration setup.
