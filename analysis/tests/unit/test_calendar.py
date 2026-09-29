from datetime import date

import pytest

from app.data.calendar import sessions


def test_sessions_returns_trading_days():
    result = sessions(
        date(2026, 1, 1),
        date(2026, 1, 10),
    )

    assert len(result) > 0

    # 元日は取引日ではない
    assert date(2026, 1, 1) not in result

    # 全て指定範囲内
    assert all(
        date(2026, 1, 1) <= d <= date(2026, 1, 10)
        for d in result
    )


def test_invalid_date_range():
    with pytest.raises(
        ValueError,
        match="INVALID_DATE_RANGE",
    ):
        sessions(
            date(2026, 2, 1),
            date(2026, 1, 1),
        )