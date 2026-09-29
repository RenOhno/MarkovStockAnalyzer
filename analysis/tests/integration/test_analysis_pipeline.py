import numpy as np

from app.core.returns import calculate_returns
from app.core.state_classifier import classify_returns
from app.core.transition import count_transitions, estimate_mle
from app.core.markov import forecast_distribution


def test_full_analysis_pipeline():
    prices = [
        "100.0",
        "101.0",
        "101.2",
        "100.0",
        "101.0",
        "100.8",
        "99.5",
        "100.5",
        "100.6",
        "99.0",
        "100.0",
    ]

    # 1. 日次リターン
    returns = calculate_returns(prices)

    # 2. UP / FLAT / DOWN
    states = classify_returns(
        returns,
        lower="-0.005",
        upper="0.005",
    )

    # 3. 遷移回数
    counts = count_transitions(states)

    # 4. 最尤推定
    matrix, unestimated_rows = estimate_mle(counts)

    # この人工系列では全状態から遷移が存在することを確認
    assert len(unestimated_rows) == 0

    # 状態数Nなら遷移数はN-1
    assert counts.sum() == len(states) - 1

    # 遷移確率行列の各行の和は1
    np.testing.assert_allclose(
        matrix.sum(axis=1),
        np.ones(3),
    )

    # 5. 現在状態
    current_state = int(states[-1])

    # 6. 1営業日後
    forecast_1 = forecast_distribution(
        matrix,
        current_state,
        horizon=1,
    )

    assert np.isclose(forecast_1.sum(), 1.0)

    # 7. 3・5・10営業日後
    for horizon in [3, 5, 10]:
        forecast = forecast_distribution(
            matrix,
            current_state,
            horizon=horizon,
        )

        assert np.isclose(forecast.sum(), 1.0)
        assert np.all(forecast >= 0)
        assert np.all(forecast <= 1)