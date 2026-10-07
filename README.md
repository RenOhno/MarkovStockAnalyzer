# MarkovStockAnalyzer
A web application that analyzes stock price trends using Markov chains.

Research/education only; predictions and backtest accuracy are not investment
advice or guarantees of trading profit. Trading, login and cloud deployment are
outside this MVP.

See [local setup and offline browser E2E](docs/setup.md) for Docker Desktop/WSL2,
secret configuration, startup, health checks, screenshots and regression tests.
The normal stack serves the frontend at `http://127.0.0.1:8080/` using
`docker compose up --build -d`. Normal `docker compose down` retains saved data;
use `down -v` only when intentionally deleting it.
