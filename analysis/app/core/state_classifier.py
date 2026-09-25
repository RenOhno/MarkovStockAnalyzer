from decimal import Decimal

import numpy as np


STATE_ORDER = ("UP", "FLAT", "DOWN")


def classify_returns(returns, lower="-0.005", upper="0.005"):
    """
    日次リターンを UP / FLAT / DOWN の3状態に分類する。

    UP   : return > upper
    FLAT : lower <= return <= upper
    DOWN : return < lower

    Parameters
    ----------
    returns : iterable
        日次リターン。
    lower : str or Decimal
        下側閾値。初期値は -0.005 (-0.5%)。
    upper : str or Decimal
        上側閾値。初期値は 0.005 (+0.5%)。

    Returns
    -------
    numpy.ndarray
        UP=0, FLAT=1, DOWN=2 の整数配列。
    """

    values = [Decimal(str(value)) for value in returns]
    lower = Decimal(str(lower))
    upper = Decimal(str(upper))

    if any(not value.is_finite() for value in values):
        raise ValueError("INVALID_RETURNS")

    if not (lower.is_finite() and upper.is_finite()):
        raise ValueError("INVALID_THRESHOLDS")

    if not (-1 < lower <= 0 <= upper < 1 and lower < upper):
        raise ValueError("INVALID_THRESHOLDS")

    return np.array(
        [
            0 if value > upper
            else 2 if value < lower
            else 1
            for value in values
        ],
        dtype=np.int64,
    )