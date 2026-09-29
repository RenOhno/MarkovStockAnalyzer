import numpy as np


def count_transitions(states, k=3):
    """
    状態列から遷移回数行列を作成する。

    状態:
    UP   = 0
    FLAT = 1
    DOWN = 2

    Parameters
    ----------
    states : array-like
        状態番号の1次元配列。
    k : int
        状態数。初期版では3。

    Returns
    -------
    numpy.ndarray
        k × k の遷移回数行列。
        行 = 現在状態
        列 = 次状態
    """

    states = np.asarray(states)

    if states.ndim != 1 or len(states) < 2:
        raise ValueError("INSUFFICIENT_STATES")

    if not np.issubdtype(states.dtype, np.integer):
        raise ValueError("INVALID_STATE_TYPE")

    if np.any((states < 0) | (states >= k)):
        raise ValueError("INVALID_STATE")

    counts = np.zeros((k, k), dtype=np.int64)

    np.add.at(
        counts,
        (states[:-1], states[1:]),
        1,
    )

    return counts


def estimate_mle(counts):
    """
    遷移回数から最尤推定で遷移確率行列を求める。

    p_ij = n_ij / sum_j(n_ij)

    Parameters
    ----------
    counts : numpy.ndarray
        遷移回数行列。

    Returns
    -------
    matrix : numpy.ndarray
        遷移確率行列。
        出発回数が0の行は NaN。

    unestimated_rows : numpy.ndarray
        推定できなかった行番号。
    """

    counts = np.asarray(counts)

    if counts.ndim != 2 or counts.shape[0] != counts.shape[1]:
        raise ValueError("INVALID_COUNTS_SHAPE")

    if (
        not np.issubdtype(counts.dtype, np.integer)
        or np.any(counts < 0)
    ):
        raise ValueError("INVALID_COUNTS")

    support = counts.sum(axis=1)

    matrix = np.full(
        counts.shape,
        np.nan,
        dtype=np.float64,
    )

    known = support > 0

    matrix[known] = (
        counts[known]
        / support[known, None]
    )

    return matrix, np.flatnonzero(~known)