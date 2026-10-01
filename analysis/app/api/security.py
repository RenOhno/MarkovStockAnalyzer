import os
import secrets

from fastapi import Depends, Request
from fastapi.security import APIKeyHeader


INTERNAL_TOKEN_HEADER = "X-Internal-Token"
internal_token_header = APIKeyHeader(
    name=INTERNAL_TOKEN_HEADER,
    auto_error=False,
)


class InternalAPIError(Exception):
    def __init__(self, code: str, message: str, status_code: int):
        self.code = code
        self.message = message
        self.status_code = status_code
        super().__init__(message)


class APIError(Exception):
    def __init__(self, code: str, message: str, status_code: int):
        self.code = code
        self.message = message
        self.status_code = status_code
        super().__init__(message)


def load_internal_api_token() -> str:
    token = os.environ.get("INTERNAL_API_TOKEN")
    if not token:
        raise RuntimeError("INTERNAL_API_TOKEN_NOT_CONFIGURED")
    return token


def require_internal_token(
    request: Request,
    internal_token: str | None = Depends(internal_token_header),
) -> None:
    configured_token = request.app.state.internal_api_token
    if (
        internal_token is None
        or not secrets.compare_digest(internal_token, configured_token)
    ):
        raise InternalAPIError(
            code="INTERNAL_AUTH_FAILED",
            message="Internal authentication failed",
            status_code=401,
        )
