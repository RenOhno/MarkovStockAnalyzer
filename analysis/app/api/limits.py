from datetime import date


MAX_CALENDAR_YEARS = 5
MAX_STATE_COUNT = 1300
MAX_JSON_BYTES = 5 * 1024 * 1024
STATE_COUNT_LIMIT_EXCEEDED = "STATE_COUNT_LIMIT_EXCEEDED"
REQUEST_BODY_TOO_LARGE = "REQUEST_BODY_TOO_LARGE"


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
