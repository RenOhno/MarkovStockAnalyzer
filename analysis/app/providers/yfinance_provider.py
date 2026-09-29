from datetime import date, timedelta

import pandas as pd
import yfinance as yf

from app.providers.base import (
    MarketDataProvider,
    PriceDataset,
    PricePoint,
)


class YFinanceProvider(MarketDataProvider):

    def fetch(
        self,
        ticker: str,
        start_date: date,
        end_date: date,
    ) -> PriceDataset:

        if not ticker:
            raise ValueError("INVALID_TICKER")

        if start_date > end_date:
            raise ValueError("INVALID_DATE_RANGE")

        # yfinance の end は排他的なので、
        # ユーザー指定の終了日を含めるため1日追加する
        yf_end_date = end_date + timedelta(days=1)

        data = yf.download(
            ticker,
            start=start_date.isoformat(),
            end=yf_end_date.isoformat(),
            interval="1d",
            auto_adjust=False,
            actions=True,
            repair=False,
            rounding=False,
            progress=False,
        )

        if data.empty:
            raise ValueError("NO_PRICE_DATA")

        data = self._normalize_columns(data, ticker)

        required_columns = {
            "Close",
            "Adj Close",
        }

        if not required_columns.issubset(data.columns):
            raise ValueError("MISSING_PRICE_COLUMNS")

        prices = []

        for index, row in data.iterrows():

            close = row["Close"]
            adjusted_close = row["Adj Close"]

            if pd.isna(close) or pd.isna(adjusted_close):
                raise ValueError("MISSING_PRICE_VALUE")

            volume = row.get("Volume")

            if pd.isna(volume):
                volume_value = None
            else:
                volume_value = int(volume)

            prices.append(
                PricePoint(
                    date=index.date(),
                    close=f"{float(close):.10f}",
                    adjusted_close=f"{float(adjusted_close):.10f}",
                    volume=volume_value,
                )
            )

        return PriceDataset(
            ticker=ticker,
            provider="YFINANCE",
            prices=prices,
        )

    @staticmethod
    def _normalize_columns(
        data: pd.DataFrame,
        ticker: str,
    ) -> pd.DataFrame:
        """
        yfinance が MultiIndex の列を返した場合でも
        単一銘柄用の通常のDataFrameへ変換する。
        """

        if not isinstance(data.columns, pd.MultiIndex):
            return data

        # yfinance のバージョンによって
        # (Price, Ticker) のようなMultiIndexになる場合がある
        if ticker in data.columns.get_level_values(-1):
            return data.xs(
                ticker,
                axis=1,
                level=-1,
                drop_level=True,
            )

        # 単一銘柄であれば先頭レベルのみ使用できる場合
        data = data.copy()
        data.columns = data.columns.get_level_values(0)

        return data