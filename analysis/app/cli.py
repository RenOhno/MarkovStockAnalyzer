from datetime import date

from app.providers.yfinance_provider import YFinanceProvider


def main():
    provider = YFinanceProvider()

    dataset = provider.fetch(
        ticker="7203.T",
        start_date=date(2026, 1, 1),
        end_date=date(2026, 3, 31),
    )

    print("=== Stock Data ===")
    print(f"Ticker   : {dataset.ticker}")
    print(f"Provider : {dataset.provider}")
    print(f"Rows     : {len(dataset.prices)}")
    print()

    print("=== First 5 Rows ===")

    for price in dataset.prices[:5]:
        print(
            f"{price.date} | "
            f"Close: {price.close} | "
            f"Adjusted Close: {price.adjusted_close} | "
            f"Volume: {price.volume}"
        )


if __name__ == "__main__":
    main()