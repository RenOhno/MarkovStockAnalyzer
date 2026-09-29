from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from typing import Annotated, Literal

from pydantic import (
    BaseModel,
    BeforeValidator,
    ConfigDict,
    Field,
    StrictFloat,
    StrictInt,
    StrictStr,
    model_validator,
)


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


def _decimal_input(value: object) -> Decimal:
    if isinstance(value, bool) or isinstance(value, str):
        raise ValueError("threshold must be a JSON number")
    try:
        return Decimal(str(value))
    except (InvalidOperation, ValueError, TypeError):
        raise ValueError("threshold must be a finite decimal") from None


Threshold = Annotated[Decimal, BeforeValidator(_decimal_input)]


class AnalysisCondition(StrictBaseModel):
    startDate: date
    endDate: date
    lowerThreshold: Threshold
    upperThreshold: Threshold
    stateCount: Literal[3]
    estimator: Literal["MLE_STRICT"]
    windowMode: Literal["FULL"]
    windowSize: None = None
    horizons: list[Literal[1, 3, 5, 10]]

    @model_validator(mode="after")
    def validate_condition(self):
        if self.startDate > self.endDate:
            raise ValueError("startDate must be on or before endDate")
        for threshold in (self.lowerThreshold, self.upperThreshold):
            if not threshold.is_finite():
                raise ValueError("threshold must be finite")
            if max(0, -threshold.as_tuple().exponent) > 10:
                raise ValueError("threshold supports at most 10 decimals")
        if not (-1 < self.lowerThreshold <= 0):
            raise ValueError("lowerThreshold is out of range")
        if not (0 <= self.upperThreshold < 1):
            raise ValueError("upperThreshold is out of range")
        if self.lowerThreshold >= self.upperThreshold:
            raise ValueError("lowerThreshold must be below upperThreshold")
        if self.horizons != [1, 3, 5, 10]:
            raise ValueError("horizons must be [1, 3, 5, 10]")
        return self


class AnalyzeInput(StrictBaseModel):
    requestId: StrictStr = Field(
        min_length=1,
        max_length=128,
        pattern=r"^[A-Za-z0-9._:-]+$",
    )
    engineVersion: StrictStr = Field(min_length=1, max_length=64)
    condition: AnalysisCondition
    dataset: PriceDatasetPayload


class BacktestEvaluation(StrictBaseModel):
    testStart: date
    testEnd: date
    minTrainStates: StrictInt = Field(ge=30)
    trainingMode: Literal["EXPANDING"]
    windowSize: None = None
    horizon: Literal[1]

    @model_validator(mode="after")
    def validate_evaluation_range(self):
        if self.testStart > self.testEnd:
            raise ValueError("testStart must be on or before testEnd")
        return self


class BacktestInput(StrictBaseModel):
    requestId: StrictStr = Field(
        min_length=1,
        max_length=128,
        pattern=r"^[A-Za-z0-9._:-]+$",
    )
    engineVersion: StrictStr = Field(min_length=1, max_length=64)
    condition: AnalysisCondition
    dataset: PriceDatasetPayload
    evaluation: BacktestEvaluation


class SeriesInput(AnalyzeInput):
    requiredEngineVersion: StrictStr = Field(min_length=1, max_length=64)


class ForecastPayload(StrictBaseModel):
    horizon: Literal[1, 3, 5, 10]
    probabilities: list[float]


class AnalysisWarning(StrictBaseModel):
    code: StrictStr
    states: list[StrictStr]


class BacktestPredictionPayload(StrictBaseModel):
    originDate: date
    targetDate: date
    trainStart: date
    trainEnd: date
    actualState: Literal["UP", "FLAT", "DOWN"]
    predictedState: Literal["UP", "FLAT", "DOWN"] | None
    probabilities: list[float] | None
    majorityState: Literal["UP", "FLAT", "DOWN"] | None
    persistenceState: Literal["UP", "FLAT", "DOWN"] | None
    status: Literal["SCORED", "SKIPPED"]
    skipCode: StrictStr | None


class BacktestMetricsPayload(StrictBaseModel):
    accuracy: float | None
    precision: list[float | None]
    recall: list[float | None]
    confusionMatrix: list[list[StrictInt]]
    brierScore: float | None
    logLoss: float | None
    majorityAccuracy: float | None
    persistenceAccuracy: float | None
    tieCount: StrictInt
    skipReasons: dict[str, StrictInt]


class BacktestSummary(StrictBaseModel):
    testStart: date
    testEnd: date
    horizon: Literal[1]
    eligibleCount: StrictInt
    predictedCount: StrictInt
    correctCount: StrictInt
    skippedCount: StrictInt
    coverage: float
    metrics: BacktestMetricsPayload


class CalculatedBacktest(StrictBaseModel):
    summary: BacktestSummary
    predictions: list[BacktestPredictionPayload]
    engineVersion: StrictStr
    runtime: dict[str, object]


class SeriesPointPayload(StrictBaseModel):
    date: date
    close: StrictStr
    adjustedClose: StrictStr
    returnValue: StrictFloat
    state: Literal["UP", "FLAT", "DOWN"]


class CalculatedSeries(StrictBaseModel):
    priceBasis: Literal["PROVIDER_ADJUSTED_CLOSE"]
    points: list[SeriesPointPayload]
    engineVersion: StrictStr


class CalculatedAnalysis(StrictBaseModel):
    stateOrder: list[Literal["UP", "FLAT", "DOWN"]]
    asOfDate: date
    currentState: Literal["UP", "FLAT", "DOWN"]
    sampleCount: StrictInt
    transitionCount: StrictInt
    transitionCounts: list[list[StrictInt]]
    transitionMatrix: list[list[float | None]]
    predictionStatus: Literal["AVAILABLE", "UNAVAILABLE"]
    forecasts: list[ForecastPayload]
    warnings: list[AnalysisWarning]
    engineVersion: StrictStr
    runtime: dict[str, object]
