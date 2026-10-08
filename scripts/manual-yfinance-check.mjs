// Explicit manual check only. Never invoked by CI, pytest, npm test or E2E.
import { randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';

if (!process.argv.includes('--run-once')) throw new Error('Use --run-once for an explicitly authorized real-provider request');
const stockResponse = await fetch(`${process.env.MANUAL_BASE_URL ?? 'http://127.0.0.1:8081'}/api/stocks`);
if (!stockResponse.ok) throw new Error('Registered stock catalog must be available before the manual check');
const stocks = await stockResponse.json();
if (!stocks.some(stock => stock.id === '7203')) throw new Error('Registered stock 7203 is missing');
const code = String.raw`
import contextlib, io, json, os, time
from datetime import date
from decimal import Decimal
from fastapi.testclient import TestClient
from app.main import create_app
from app.data.calendar import sessions, previous_session
from app.providers.yfinance_provider import YFinanceProvider
import yfinance as yf

start, end = date(2025, 5, 1), date(2025, 6, 30)
request_id = "manual-yfinance-step11"
report = {"manualOnly": True, "ticker": "7203.T", "startDate": str(start), "endDate": str(end),
          "providerCalls": 0, "downloadTimeoutSeconds": 5, "fetchDeadlineSeconds": 12,
          "priceValuesWritten": False}
download = yf.download
def observe_download(*args, **kwargs):
    report["providerCalls"] += 1
    assert kwargs["timeout"] == 5 and kwargs["auto_adjust"] is False
    frame = download(*args, **kwargs)
    labels = set(str(value) for value in frame.columns.get_level_values(0))
    report["providerShape"] = {"rows": len(frame), "hasClose": "Close" in labels, "hasAdjustedClose": "Adj Close" in labels}
    return frame
yf.download = observe_download
headers = {"X-Internal-Token": os.environ["INTERNAL_API_TOKEN"], "X-Request-Id": request_id}
payload = {"requestId": request_id, "ticker": "7203.T", "exchange": "XTKS", "timeZone": "Asia/Tokyo",
           "startDate": str(start), "endDate": str(end), "includePreviousSession": True,
           "priceBasis": "PROVIDER_ADJUSTED_CLOSE", "provider": "YFINANCE"}
began = time.monotonic()
# Provider diagnostics and response prices stay in memory, never stdout or mounted files.
with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
    with TestClient(create_app(provider_factory=YFinanceProvider)) as client:
        response = client.post("/internal/v1/prices/fetch", headers=headers, json=payload)
        report["fetchStatus"] = response.status_code
        report["elapsedSeconds"] = round(time.monotonic() - began, 3)
        if response.status_code == 200:
            dataset = response.json()
            expected = [previous_session(start), *sessions(start, end)]
            assert [date.fromisoformat(point["date"]) for point in dataset["prices"]] == expected
            assert dataset["metadata"]["calendarName"] == "XTKS"
            assert dataset["timeZone"] == "Asia/Tokyo" and dataset["provider"] == "YFINANCE"
            assert all(Decimal(point["adjustedClose"]) > 0 and len(point["adjustedClose"].split(".")[1]) == 10 for point in dataset["prices"])
            report.update({"calendarVerified": True, "previousSessionVerified": True, "adjustedCloseVerified": True,
                           "pricePointCount": len(dataset["prices"]), "calendarVersion": dataset["metadata"]["calendarVersion"]})
            condition = {"startDate": str(start), "endDate": str(end), "lowerThreshold": -0.005, "upperThreshold": 0.005,
                         "stateCount": 3, "estimator": "MLE_STRICT", "windowMode": "FULL", "windowSize": None, "horizons": [1,3,5,10]}
            calculated = client.post("/internal/v1/analyze", headers=headers,
                json={"requestId": request_id, "engineVersion": "msa-core-v1", "condition": condition, "dataset": dataset})
            report["analyzeStatus"] = calculated.status_code
            if calculated.status_code == 200:
                result = calculated.json()
                report.update({"sampleCount": result["sampleCount"], "transitionCount": result["transitionCount"],
                               "predictionStatus": result["predictionStatus"], "engineVersion": result["engineVersion"]})
            else: report["errorCode"] = calculated.json()["code"]
        else:
            report["errorCode"] = response.json()["code"]
assert report["providerCalls"] == 1, "Manual check must issue exactly one provider request"
print(json.dumps(report))
`;
const result = spawnSync('docker', ['run', '--rm', '-i', '-e', 'INTERNAL_API_TOKEN',
  'markov-stock-analyzer-analysis-tests:local', 'uv', 'run', 'python', '-'],
  { input: code, encoding: 'utf8', timeout: 60_000, env: { ...process.env, INTERNAL_API_TOKEN: randomBytes(32).toString('hex') } });
if (result.error || result.status !== 0) throw new Error('Manual check execution failed; inspect locally without publishing response prices');
const report = JSON.parse(result.stdout.trim());
console.log(JSON.stringify(report, null, 2));
if (report.fetchStatus !== 200 || report.analyzeStatus !== 200) process.exitCode = 2;
