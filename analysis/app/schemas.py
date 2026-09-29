from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, StrictBool, StrictInt, StrictStr, model_validator


ENGINE_VERSION = "msa-core-v1"


class HealthResponse(BaseModel):
    status: str
    engineVersion: str


class ErrorResponse(BaseModel):
    code: str
    message: str
    requestId: str
    details: dict[str, object]


class StrictBaseModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class FetchPricesRequest(StrictBaseModel):
    requestId: StrictStr = Field(
        min_length=1,
        max_length=128,
        pattern=r"^[A-Za-z0-9._:-]+$",
    )
    ticker: StrictStr = Field(
        min_length=1,
        max_length=32,
        pattern=r"^[A-Za-z0-9.^_-]+$",
    )
    exchange: Literal["XTKS"]
    timeZone: Literal["Asia/Tokyo"]
    startDate: date
    endDate: date
    includePreviousSession: Literal[True]
    priceBasis: Literal["PROVIDER_ADJUSTED_CLOSE"]
    provider: Literal["YFINANCE"]

    @model_validator(mode="after")
    def validate_date_range(self):
        if self.startDate > self.endDate:
            raise ValueError("startDate must be on or before endDate")
        return self


class PricePointPayload(StrictBaseModel):
    date: date
    close: StrictStr
    adjustedClose: StrictStr
    volume: StrictInt | None


class PriceDatasetMetadata(StrictBaseModel):
    schemaVersion: StrictInt
    normalizationVersion: StrictStr
    calendarName: StrictStr
    calendarVersion: StrictStr
    roundingMode: StrictStr
    scale: StrictInt
    fetchOptions: dict[str, object]
    qualityFlags: list[StrictStr]
    ticker: StrictStr
    exchange: StrictStr
    timeZone: StrictStr
    priceBasis: StrictStr
    provider: StrictStr
    providerVersion: StrictStr


class PriceDatasetPayload(StrictBaseModel):
    ticker: StrictStr
    exchange: StrictStr
    timeZone: StrictStr
    priceBasis: StrictStr
    provider: StrictStr
    providerVersion: StrictStr
    adjustmentPolicy: StrictStr
    fetchedAt: datetime
    coverageStart: date
    coverageEnd: date
    contentSha256: StrictStr = Field(pattern=r"^[0-9a-f]{64}$")
    metadata: PriceDatasetMetadata
    prices: list[PricePointPayload]
