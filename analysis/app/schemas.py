from pydantic import BaseModel


ENGINE_VERSION = "msa-core-v1"


class HealthResponse(BaseModel):
    status: str
    engineVersion: str


class ErrorResponse(BaseModel):
    code: str
    message: str
    requestId: str
    details: dict[str, object]
