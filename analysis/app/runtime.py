"""Deployment wiring only: the existing app factory and calculation code are unchanged."""
import math
import os
import time
from pathlib import Path

from app.providers.fixture_provider import FixtureProvider
from app.providers.yfinance_provider import YFinanceProvider


class DelayedFixtureProvider(FixtureProvider):
    """Offline fault injection selected only in explicit fixture mode."""
    def __init__(self, fixture_path: Path, delay: float):
        super().__init__(fixture_path)
        self.delay = delay

    def fetch(self, ticker, start_date, end_date):
        time.sleep(self.delay)
        return super().fetch(ticker, start_date, end_date)


def provider_factory_from_environment():
    mode = os.environ.get("ANALYSIS_PROVIDER", "yfinance")
    if mode == "yfinance":
        return YFinanceProvider
    if mode != "fixture":
        raise RuntimeError("ANALYSIS_PROVIDER_INVALID")
    path = os.environ.get("FIXTURE_PATH")
    if not path or not Path(path).is_file():
        raise RuntimeError("FIXTURE_PATH_NOT_CONFIGURED")
    try:
        delay = float(os.environ.get("FIXTURE_FETCH_DELAY_SECONDS", "0"))
    except ValueError:
        raise RuntimeError("FIXTURE_DELAY_INVALID") from None
    if not math.isfinite(delay) or not 0 <= delay <= 60:
        raise RuntimeError("FIXTURE_DELAY_INVALID")
    return lambda: DelayedFixtureProvider(Path(path), delay) if delay else FixtureProvider(Path(path))


def create_runtime_app():
    from app.main import create_app
    return create_app(provider_factory=provider_factory_from_environment())
