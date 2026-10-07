"""Generate artificial prices on the engine's exact XTKS calendar. No market data."""
import csv
from datetime import date
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

from app.data.calendar import previous_session, sessions

output = Path("/fixtures/prices.csv")
dates = [previous_session(date(2025, 1, 6)), *sessions(date(2025, 1, 6), date(2025, 6, 30))]
price = Decimal("100")
with output.open("w", encoding="utf-8", newline="") as file:
    writer = csv.writer(file)
    writer.writerow(["date", "close", "adjusted_close", "volume"])
    for index, day in enumerate(dates):
        # Sixty UP states make early forecasts unestimated; later all rows are observed.
        change = Decimal("0.01") if 0 < index <= 60 else (
            [Decimal("0"), Decimal("-0.01"), Decimal("0.01"), Decimal("0"), Decimal("0.01"), Decimal("-0.01")][(index - 61) % 6]
            if index > 60 else Decimal("0"))
        price = (price * (1 + change)).quantize(Decimal("0.0000000001"), rounding=ROUND_HALF_UP)
        writer.writerow([day.isoformat(), str(price), str(price), "1000"])
print(f"Generated {len(dates)} artificial XTKS prices (not real stock prices).")
