FROM ghcr.io/astral-sh/uv:0.9.2 AS uv
FROM python:3.12.12-slim-bookworm AS build
COPY --from=uv /uv /uvx /usr/local/bin/
ENV UV_LINK_MODE=copy UV_COMPILE_BYTECODE=1 UV_PYTHON_DOWNLOADS=never
WORKDIR /app
COPY analysis/pyproject.toml analysis/uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv uv sync --frozen --no-dev
COPY analysis/app ./app

FROM build AS test
RUN --mount=type=cache,target=/root/.cache/uv uv sync --frozen
COPY analysis/tests ./tests
COPY docs/api/openapi-internal.json /docs/api/openapi-internal.json
CMD ["uv", "run", "--frozen", "pytest"]

FROM python:3.12.12-slim-bookworm AS runtime
COPY --from=uv /uv /uvx /usr/local/bin/
RUN groupadd --gid 10001 msa && useradd --uid 10001 --gid msa --create-home msa
WORKDIR /app
COPY --from=build --chown=msa:msa /app/.venv /app/.venv
COPY --from=build --chown=msa:msa /app/app /app/app
ENV PATH="/app/.venv/bin:$PATH" PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1
USER msa
EXPOSE 8000
CMD ["uvicorn", "app.runtime:create_runtime_app", "--factory", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
