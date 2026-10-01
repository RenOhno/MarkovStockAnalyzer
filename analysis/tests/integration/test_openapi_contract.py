import json
import importlib
from pathlib import Path


CONTRACT_PATH = (
    Path(__file__).resolve().parents[3]
    / "docs"
    / "api"
    / "openapi-internal.json"
)
EXPECTED_PATHS = {
    "/internal/v1/health": {"get"},
    "/internal/v1/prices/fetch": {"post"},
    "/internal/v1/analyze": {"post"},
    "/internal/v1/backtest": {"post"},
    "/internal/v1/series": {"post"},
}


def test_generated_openapi_matches_frozen_internal_contract(monkeypatch):
    monkeypatch.setenv("INTERNAL_API_TOKEN", "contract-test-token")
    create_app = importlib.import_module("app.main").create_app
    frozen = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
    generated = create_app().openapi()

    assert set(generated["paths"]) == EXPECTED_PATHS.keys()
    for path, methods in EXPECTED_PATHS.items():
        assert set(generated["paths"][path]) == methods

    for path, methods in EXPECTED_PATHS.items():
        for method in methods:
            expected_operation = frozen["paths"][path][method]
            actual_operation = generated["paths"][path][method]
            if "requestBody" in expected_operation:
                expected_body_name = expected_operation["requestBody"]["$ref"].split("/")[-1]
                expected_schema_ref = frozen["components"]["requestBodies"][expected_body_name]["content"]["application/json"]["schema"]["$ref"]
                assert actual_operation["requestBody"]["content"]["application/json"]["schema"]["$ref"] == expected_schema_ref
            expected_response_name = expected_operation["responses"]["200"]["$ref"].split("/")[-1]
            expected_schema_ref = frozen["components"]["responses"][expected_response_name]["content"]["application/json"]["schema"]["$ref"]
            assert actual_operation["responses"]["200"]["content"]["application/json"]["schema"]["$ref"] == expected_schema_ref

    for schema_name, contract_schema in frozen["components"]["schemas"].items():
        assert schema_name in generated["components"]["schemas"]
        assert generated["components"]["schemas"][schema_name]["required"] == contract_schema["required"]

    schemas = generated["components"]["schemas"]
    assert schemas["PricePointPayload"]["properties"]["close"]["type"] == "string"
    assert schemas["PricePointPayload"]["properties"]["adjustedClose"]["type"] == "string"
    assert schemas["PricePointPayload"]["properties"]["volume"]["anyOf"] == [
        {"type": "integer"},
        {"type": "null"},
    ]
    assert schemas["PriceDatasetPayload"]["properties"]["fetchedAt"]["format"] == "date-time"
    assert schemas["AnalysisCondition"]["properties"]["windowSize"]["type"] == "null"
    assert schemas["SeriesPointPayload"]["properties"]["state"]["enum"] == [
        "UP",
        "FLAT",
        "DOWN",
    ]
    assert schemas["BacktestPredictionPayload"]["properties"]["probabilities"]["anyOf"][-1] == {"type": "null"}
    for field in [
        "accuracy",
        "brierScore",
        "logLoss",
        "majorityAccuracy",
        "persistenceAccuracy",
    ]:
        assert {"type": "null"} in schemas["BacktestMetricsPayload"]["properties"][field]["anyOf"]
    for field in ["precision", "recall"]:
        assert {"type": "null"} in schemas["BacktestMetricsPayload"]["properties"][field]["items"]["anyOf"]

    assert generated["components"]["schemas"]["ErrorResponse"]["required"] == [
        "code",
        "message",
        "requestId",
        "details",
    ]
    assert "contract-test-token" not in json.dumps(generated)
