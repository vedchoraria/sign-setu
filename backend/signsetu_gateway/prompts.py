import json
import re
from functools import cache
from pathlib import Path

FILE = Path(__file__).with_name("app_prompts.json")
SLOT = re.compile("§[A-Z]+§")
# A language name or the like, never a line break, so a slot cannot carry extra instructions.
FILL = "[^\n§]{1,80}"


def compile_template(text: str) -> re.Pattern[str]:
    return re.compile(FILL.join(re.escape(part) for part in SLOT.split(text)))


@cache
def templates() -> tuple[re.Pattern[str], ...]:
    """The app's own system prompts, written out by its unit test (GatewayPromptContractTest)."""
    return tuple(compile_template(t) for t in json.loads(FILE.read_text(encoding="utf-8"))["templates"])


def allowed(system: str) -> bool:
    return any(t.fullmatch(system.strip()) for t in templates())
