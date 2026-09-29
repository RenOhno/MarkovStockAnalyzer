from fastapi import APIRouter

from app.schemas import ENGINE_VERSION, HealthResponse


router = APIRouter(prefix="/internal/v1")


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="UP",
        engineVersion=ENGINE_VERSION,
    )
