import re
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from uuid import uuid4
from collections.abc import Callable

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.requests import Request

from app.api.security import APIError, InternalAPIError, load_internal_api_token
from app.api.limits import (
    DEFAULT_PROVIDER_DEADLINE_SECONDS,
    ExecutionGate,
    MAX_JSON_BYTES,
    REQUEST_BODY_TOO_LARGE,
)
from app.api.routes import router
from app.providers.base import MarketDataProvider
from app.providers.yfinance_provider import YFinanceProvider
from app.schemas import ErrorResponse


_REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")


def _request_id(value: str | None) -> str:
    if value is not None and _REQUEST_ID_PATTERN.fullmatch(value):
        return value
    return str(uuid4())


def _error_response(
    request: Request,
    status_code: int,
    code: str,
    message: str,
    details: dict[str, object] | None = None,
) -> JSONResponse:
    body = ErrorResponse(
        code=code,
        message=message,
        requestId=request.state.request_id,
        details=details or {},
    )
    return JSONResponse(
        status_code=status_code,
        content=body.model_dump(),
        headers={"X-Request-Id": request.state.request_id},
    )


def create_app(
    provider_factory: Callable[[], MarketDataProvider] = YFinanceProvider,
    fetch_deadline_seconds: float = DEFAULT_PROVIDER_DEADLINE_SECONDS,
) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application: FastAPI):
        try:
            yield
        finally:
            application.state.provider_executor.shutdown(
                wait=True,
                cancel_futures=True,
            )

    app = FastAPI(title="Markov Stock Analyzer", lifespan=lifespan)
    app.state.internal_api_token = load_internal_api_token()
    app.state.provider_factory = provider_factory
    app.state.execution_gate = ExecutionGate()
    app.state.provider_executor = ThreadPoolExecutor(
        max_workers=1,
        thread_name_prefix="provider-worker",
    )
    app.state.fetch_deadline_seconds = fetch_deadline_seconds

    @app.middleware("http")
    async def request_id_middleware(request: Request, call_next):
        request.state.request_id = _request_id(
            request.headers.get("X-Request-Id")
        )
        if request.method == "POST":
            content_length = request.headers.get("content-length")
            try:
                declared_length = (
                    int(content_length) if content_length is not None else None
                )
            except ValueError:
                declared_length = None
            if declared_length is not None and declared_length > MAX_JSON_BYTES:
                return _error_response(
                    request,
                    413,
                    REQUEST_BODY_TOO_LARGE,
                    "Request body is too large",
                    {"limit": MAX_JSON_BYTES},
                )

            body = bytearray()
            async for chunk in request.stream():
                body.extend(chunk)
                if len(body) > MAX_JSON_BYTES:
                    return _error_response(
                        request,
                        413,
                        REQUEST_BODY_TOO_LARGE,
                        "Request body is too large",
                        {"limit": MAX_JSON_BYTES},
                    )
            request._body = bytes(body)
        response = await call_next(request)
        response.headers["X-Request-Id"] = request.state.request_id
        return response

    @app.exception_handler(InternalAPIError)
    async def internal_api_error_handler(
        request: Request,
        error: InternalAPIError,
    ) -> JSONResponse:
        return _error_response(
            request,
            error.status_code,
            error.code,
            error.message,
        )

    @app.exception_handler(APIError)
    async def api_error_handler(
        request: Request,
        error: APIError,
    ) -> JSONResponse:
        return _error_response(
            request,
            error.status_code,
            error.code,
            error.message,
        )

    @app.exception_handler(RequestValidationError)
    async def request_validation_error_handler(
        request: Request,
        error: RequestValidationError,
    ) -> JSONResponse:
        fields = [
            {
                "loc": [str(part) for part in item.get("loc", ())],
                "type": item.get("type", "validation_error"),
            }
            for item in error.errors()
        ]
        is_invalid_json = any(
            item.get("type") == "json_invalid"
            for item in error.errors()
        )
        return _error_response(
            request,
            400 if is_invalid_json else 422,
            "INVALID_JSON" if is_invalid_json else "VALIDATION_ERROR",
            "Request body is invalid" if is_invalid_json else "Request validation failed",
            {"fields": fields},
        )

    @app.exception_handler(StarletteHTTPException)
    async def http_exception_handler(
        request: Request,
        error: StarletteHTTPException,
    ) -> JSONResponse:
        if error.status_code == 404:
            code = "NOT_FOUND"
            message = "Resource not found"
        else:
            code = "HTTP_ERROR"
            message = "HTTP request failed"
        return _error_response(request, error.status_code, code, message)

    app.include_router(router)
    return app


app = create_app()
