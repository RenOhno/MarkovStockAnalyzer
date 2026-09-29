from abc import ABC, abstractmethod
from dataclasses import dataclass
from datetime import date
from typing import Optional


@dataclass(frozen=True)
class PricePoint:
    date: date
    close: str
    adjusted_close: str
    volume: Optional[int]


@dataclass(frozen=True)
class PriceDataset:
    ticker: str
    provider: str
    prices: list[PricePoint]


class MarketDataProvider(ABC):
    @abstractmethod
    def fetch(
        self,
        ticker: str,
        start_date: date,
        end_date: date,
    ) -> PriceDataset:
        """
        指定した期間の株価データを取得する。

        start_date と end_date は両端を含む。
        """
        raise NotImplementedError