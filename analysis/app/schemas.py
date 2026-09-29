from pydantic import BaseModel


ENGINE_VERSION = "msa-core-v1"


class HealthResponse(BaseModel):
    status: str
    engineVersion: str
