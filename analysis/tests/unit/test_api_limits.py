from datetime import date

import pytest

from app.api.limits import (
    MAX_CALENDAR_YEARS,
    MAX_JSON_BYTES,
    MAX_STATE_COUNT,
    calendar_year_limit,
    validate_calendar_range,
    validate_state_count,
)


def test_limits_are_design_values():
    assert MAX_CALENDAR_YEARS == 5
    assert MAX_STATE_COUNT == 1300
    assert MAX_JSON_BYTES == 5 * 1024 * 1024


def test_five_calendar_year_boundary_includes_anniversary():
    start = date(2020, 2, 29)
    end = calendar_year_limit(start)

    assert end == date(2025, 2, 28)
    validate_calendar_range(start, end)


def test_calendar_range_over_five_years_is_rejected():
    with pytest.raises(ValueError, match="MAX_CALENDAR_RANGE_EXCEEDED"):
        validate_calendar_range(date(2020, 1, 1), date(2025, 1, 2))


def test_state_limit_includes_1300_and_rejects_1301():
    validate_state_count(1300)

    with pytest.raises(ValueError, match="STATE_COUNT_LIMIT_EXCEEDED"):
        validate_state_count(1301)
