import numpy as np


def forecast_distribution(matrix, current_state, horizon):
    """
    遷移確率行列からnステップ後の状態分布を計算する。

    Parameters
    ----------
    matrix : array-like
        遷移確率行列 P。
    current_state : int
        現在状態。
        UP=0, FLAT=1, DOWN=2。
    horizon : int
        何ステップ後を計算するか。

    Returns
    -------
    numpy.ndarray
        nステップ後の状態確率。
        順序は UP, FLAT, DOWN。
    """

    matrix = np.asarray(matrix, dtype=np.float64)

    if matrix.ndim != 2 or matrix.shape[0] == 0:
        raise ValueError("INVALID_MATRIX_SHAPE")

    k = matrix.shape[0]

    if matrix.shape != (k, k):
        raise ValueError("INVALID_MATRIX_SHAPE")

    if not np.isfinite(matrix).all():
        raise ValueError("UNESTIMATED_MATRIX")

    if (
        np.any(matrix < 0)
        or not np.allclose(
            matrix.sum(axis=1),
            1.0,
            atol=1e-12,
            rtol=0,
        )
    ):
        raise ValueError("INVALID_PROBABILITY_MATRIX")

    if not isinstance(horizon, int) or not 0 <= horizon <= 30:
        raise ValueError("INVALID_HORIZON")

    if not 0 <= current_state < k:
        raise ValueError("INVALID_CURRENT_STATE")

    alpha0 = np.eye(k, dtype=np.float64)[current_state]

    distribution = (
        alpha0
        @ np.linalg.matrix_power(matrix, horizon)
    )

    if not np.isclose(
        distribution.sum(),
        1.0,
        atol=1e-12,
        rtol=0,
    ):
        raise ValueError("INVALID_DISTRIBUTION")

    return distribution