import numpy as np

from app.core.state_classifier import STATE_ORDER


def predict_majority_state(training_states) -> int:
    """学習系列だけから最頻状態を返す。argmaxで固定順を保つ。"""
    states = np.asarray(training_states)
    if states.ndim != 1 or len(states) == 0:
        raise ValueError("INVALID_TRAINING_STATES")
    if not np.issubdtype(states.dtype, np.integer):
        raise ValueError("INVALID_TRAINING_STATES")
    if np.any((states < 0) | (states >= len(STATE_ORDER))):
        raise ValueError("INVALID_TRAINING_STATES")

    counts = np.bincount(states, minlength=len(STATE_ORDER))
    return int(np.argmax(counts))


def predict_persistence_state(current_state: int) -> int:
    """origin時点の状態をそのまま翌状態の予測にする。"""
    if not isinstance(current_state, (int, np.integer)):
        raise ValueError("INVALID_CURRENT_STATE")
    if not 0 <= current_state < len(STATE_ORDER):
        raise ValueError("INVALID_CURRENT_STATE")
    return int(current_state)
