import json
from dataclasses import dataclass, asdict
from typing import List, Dict, Any

DAY_MS = 24 * 60 * 60 * 1000

@dataclass
class SrsState:
    itemId: str
    ease: float = 2.5
    intervalDays: int = 0
    repetitions: int = 0
    lapses: int = 0
    dueAtMs: int = 0

def _clamp(x, lo, hi):
    return max(lo, min(hi, x))

def update_state(state_json: str, is_correct: bool, now_ms: int, response_ms: int) -> str:
    """
    Recebe estado (json), resposta (certa/errada), timestamp e tempo de resposta.
    Retorna novo estado (json).
    """
    s = SrsState(**json.loads(state_json))

    # Penaliza "muito lento" levemente
    slow_penalty = 0.05 if response_ms > 4000 else 0.0

    if is_correct:
        s.repetitions += 1
        # Ajuste de ease: sobe um pouco se rápido, desce se lento
        delta = 0.06 - slow_penalty
        s.ease = _clamp(s.ease + delta, 1.3, 2.9)

        if s.repetitions == 1:
            s.intervalDays = 1
        elif s.repetitions == 2:
            s.intervalDays = 3
        else:
            s.intervalDays = max(1, int(round(s.intervalDays * s.ease)))

    else:
        s.lapses += 1
        s.repetitions = 0
        s.ease = _clamp(s.ease - (0.20 + slow_penalty), 1.3, 2.9)
        s.intervalDays = 1

    s.dueAtMs = now_ms + (s.intervalDays * DAY_MS)
    return json.dumps(asdict(s))

def pick_due_items(items_json: str, now_ms: int, limit: int) -> str:
    """
    items_json: lista de dicts {itemId, dueAtMs, lapses}
    Retorna lista de itemIds priorizados.
    """
    items = json.loads(items_json)

    def score(it: Dict[str, Any]) -> float:
        due = it.get("dueAtMs", 0)
        lapses = it.get("lapses", 0)
        overdue = max(0, now_ms - due)
        return overdue + (lapses * 0.25 * DAY_MS)

    # maior score primeiro
    items_sorted = sorted(items, key=score, reverse=True)
    out = [it["itemId"] for it in items_sorted[:limit]]
    return json.dumps(out)
