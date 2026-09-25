from decimal import Decimal, localcontext


def calculate_returns(price_strings):
    """
    価格系列から日次リターンを計算する。

    r_t = (C_t - C_{t-1}) / C_{t-1}

    Parameters
    ----------
    price_strings : list
        正の有限値からなる価格系列。

    Returns
    -------
    list[Decimal]
        隣接する価格から計算した日次リターン。
    """

    prices = [Decimal(str(value)) for value in price_strings]

    if len(prices) < 2:
        raise ValueError("INVALID_PRICES")

    if any(not price.is_finite() or price <= 0 for price in prices):
        raise ValueError("INVALID_PRICES")

    with localcontext() as context:
        context.prec = 50

        return [
            (current - previous) / previous
            for previous, current in zip(prices[:-1], prices[1:])
        ]