from fastapi import APIRouter, Depends

from app.api.security import require_internal_token
from app.schemas import ENGINE_VERSION, HealthResponse


router = APIRouter(
    prefix="/internal/v1",
    dependencies=[Depends(require_internal_token)],
)


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="UP",
        engineVersion=ENGINE_VERSION,
    )
