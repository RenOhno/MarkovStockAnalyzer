from datetime import date
from concurrent.futures import Future
from threading import BoundedSemaphore


MAX_CALENDAR_YEARS = 5
MAX_STATE_COUNT = 1300
MAX_JSON_BYTES = 5 * 1024 * 1024
STATE_COUNT_LIMIT_EXCEEDED = "STATE_COUNT_LIMIT_EXCEEDED"
REQUEST_BODY_TOO_LARGE = "REQUEST_BODY_TOO_LARGE"
DEFAULT_PROVIDER_DEADLINE_SECONDS = 12.0


class ExecutionGate:
    """単一FastAPI process内の重い処理を1件に制限する。"""

    def __init__(self):
        self._semaphore = BoundedSemaphore(1)

    def acquire(self) -> bool:
        return self._semaphore.acquire(blocking=False)

    def release(self) -> None:
        self._semaphore.release()


class ExecutionLease:
    def __init__(self, gate: ExecutionGate):
        self._gate = gate
        self._release_on_exit = True

    def retain_until(self, future: Future) -> None:
        self._release_on_exit = False
        future.add_done_callback(lambda _: self._gate.release())

    def release(self) -> None:
        if self._release_on_exit:
            self._release_on_exit = False
            self._gate.release()


def calendar_year_limit(start_date: date) -> date:
    try:
        return start_date.replace(year=start_date.year + MAX_CALENDAR_YEARS)
    except ValueError:
        return date(
            start_date.year + MAX_CALENDAR_YEARS,
            start_date.month,
            28,
        )


def validate_calendar_range(start_date: date, end_date: date) -> None:
    if end_date > calendar_year_limit(start_date):
        raise ValueError("MAX_CALENDAR_RANGE_EXCEEDED")


def validate_state_count(state_count: int) -> None:
    if state_count > MAX_STATE_COUNT:
        raise ValueError(STATE_COUNT_LIMIT_EXCEEDED)
