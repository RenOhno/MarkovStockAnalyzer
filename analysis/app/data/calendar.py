from datetime import date, datetime, timedelta
from functools import lru_cache

import exchange_calendars as xcals
import pandas as pd


_MARKET = "XTKS"


@lru_cache(maxsize=1)
def get_calendar():
    """日本株の取引日カレンダーを返す。"""
    return xcals.get_calendar(_MARKET)


def sessions(start_date: date, end_date: date) -> list[date]:
    """指定した暦日範囲に含まれるXTKSの取引日を返す。"""
    if start_date > end_date:
        raise ValueError("INVALID_DATE_RANGE")

    calendar = get_calendar()
    session_labels = calendar.sessions_in_range(
        _as_timestamp(start_date),
        _as_timestamp(end_date),
    )
    return [session.date() for session in session_labels]


def previous_session(session_date: date) -> date:
    """指定日の直前にあるXTKSの取引日を返す。"""
    calendar = get_calendar()
    timestamp = _as_timestamp(session_date)

    previous_or_same = calendar.date_to_session(timestamp, direction="previous")
    if previous_or_same.date() == timestamp.date():
        previous_or_same = calendar.previous_session(previous_or_same)

    return previous_or_same.date()


def _as_timestamp(value: date) -> pd.Timestamp:
    if isinstance(value, datetime):
        return pd.Timestamp(value.date())
    return pd.Timestamp(value)
