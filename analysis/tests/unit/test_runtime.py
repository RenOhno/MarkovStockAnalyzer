from datetime import date

import pytest

from app.providers.fixture_provider import FixtureProvider
from app.providers.yfinance_provider import YFinanceProvider
from app.runtime import DelayedFixtureProvider, provider_factory_from_environment


def test_default_deployment_keeps_yfinance(monkeypatch):
    monkeypatch.delenv("ANALYSIS_PROVIDER", raising=False)
    assert provider_factory_from_environment() is YFinanceProvider


def test_explicit_fixture_uses_existing_offline_provider(monkeypatch, tmp_path):
    path = tmp_path / "prices.csv"
    path.write_text("date,close,adjusted_close,volume\n2025-01-06,100,100,1\n", encoding="utf-8")
    monkeypatch.setenv("ANALYSIS_PROVIDER", "fixture")
    monkeypatch.setenv("FIXTURE_PATH", str(path))
    monkeypatch.delenv("FIXTURE_FETCH_DELAY_SECONDS", raising=False)
    provider = provider_factory_from_environment()()
    assert isinstance(provider, FixtureProvider)
    assert provider.fetch("TEST", date(2025, 1, 6), date(2025, 1, 6)).provider == "FIXTURE"


def test_fixture_delay_does_not_change_price_values(monkeypatch, tmp_path):
    path = tmp_path / "prices.csv"
    path.write_text("date,close,adjusted_close,volume\n2025-01-06,100,100,1\n", encoding="utf-8")
    monkeypatch.setenv("ANALYSIS_PROVIDER", "fixture")
    monkeypatch.setenv("FIXTURE_PATH", str(path))
    monkeypatch.setenv("FIXTURE_FETCH_DELAY_SECONDS", "13")
    calls = []
    monkeypatch.setattr("app.runtime.time.sleep", calls.append)
    provider = provider_factory_from_environment()()
    assert isinstance(provider, DelayedFixtureProvider)
    assert provider.fetch("TEST", date(2025, 1, 6), date(2025, 1, 6)).prices[0].close == "100"
    assert calls == [13]


def test_unknown_provider_is_rejected(monkeypatch):
    monkeypatch.setenv("ANALYSIS_PROVIDER", "unknown")
    with pytest.raises(RuntimeError, match="ANALYSIS_PROVIDER_INVALID"):
        provider_factory_from_environment()


def test_fixture_mode_requires_a_file(monkeypatch):
    monkeypatch.setenv("ANALYSIS_PROVIDER", "fixture")
    monkeypatch.delenv("FIXTURE_PATH", raising=False)
    with pytest.raises(RuntimeError, match="FIXTURE_PATH_NOT_CONFIGURED"):
        provider_factory_from_environment()


@pytest.mark.parametrize("delay", ["-1", "nan", "inf", "61", "invalid"])
def test_invalid_fixture_delay_is_rejected(monkeypatch, tmp_path, delay):
    path = tmp_path / "prices.csv"
    path.write_text("", encoding="utf-8")
    monkeypatch.setenv("ANALYSIS_PROVIDER", "fixture")
    monkeypatch.setenv("FIXTURE_PATH", str(path))
    monkeypatch.setenv("FIXTURE_FETCH_DELAY_SECONDS", delay)
    with pytest.raises(RuntimeError, match="FIXTURE_DELAY_INVALID"):
        provider_factory_from_environment()
