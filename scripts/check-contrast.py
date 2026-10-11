"""Fail if SMARTPREP text tokens miss WCAG AA against background tokens.

Reads frontend/css/design-system.css. Checks :root (dark) and
html[data-theme="light"] independently. Every text token is paired with
every solid background token, and with translucent surfaces composited
over --bg-0.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CSS = ROOT / "frontend" / "css" / "design-system.css"
MIN_RATIO = 4.5
TEXT_TOKENS = ("--text", "--text-muted", "--text-faint", "--danger")
BG_TOKENS = ("--bg-0", "--bg-1", "--surface", "--surface-2", "--surface-glass", "--glass", "--input-bg")


def channel(value: float) -> float:
    value = value / 255
    if value <= 0.04045:
        return value / 12.92
    return ((value + 0.055) / 1.055) ** 2.4


def luminance(rgb: tuple[float, float, float]) -> float:
    r, g, b = rgb
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def contrast(a: tuple[float, float, float], b: tuple[float, float, float]) -> float:
    hi, lo = sorted((luminance(a), luminance(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def parse_color(raw: str) -> tuple[float, float, float, float] | None:
    raw = raw.strip().lower()
    hex_match = re.fullmatch(r"#([0-9a-f]{6})", raw)
    if hex_match:
        h = hex_match.group(1)
        return tuple(int(h[i : i + 2], 16) for i in (0, 2, 4)) + (1.0,)  # type: ignore[return-value]
    rgba = re.fullmatch(
        r"rgba\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*,\s*([0-9.]+)\s*,\s*([0-9.]+)\s*\)",
        raw,
    )
    if rgba:
        r, g, b, a = (float(n) for n in rgba.groups())
        return r, g, b, a
    return None


def composite(fg: tuple[float, float, float, float], bg: tuple[float, float, float]) -> tuple[float, float, float]:
    r, g, b, a = fg
    br, bgc, bb = bg
    return (
        r * a + br * (1 - a),
        g * a + bgc * (1 - a),
        b * a + bb * (1 - a),
    )


def block_vars(css: str, selector: str) -> dict[str, str]:
    pattern = re.compile(re.escape(selector) + r"\s*\{([^}]*)\}", re.S)
    match = pattern.search(css)
    if not match:
        raise SystemExit(f"Missing {selector} in {CSS}")
    found: dict[str, str] = {}
    for name, value in re.findall(r"(--[a-z0-9-]+)\s*:\s*([^;]+);", match.group(1)):
        found[name] = value.strip()
    return found


def resolve(name: str, table: dict[str, str], seen: set[str] | None = None) -> str:
    seen = seen or set()
    if name in seen:
        raise SystemExit(f"Token cycle at {name}")
    value = table[name]
    if value.startswith("var("):
        inner = value[4:-1].strip()
        seen.add(name)
        return resolve(inner, table, seen)
    return value


def main() -> int:
    css = CSS.read_text(encoding="utf-8")
    dark = block_vars(css, ":root")
    light = {**dark, **block_vars(css, 'html[data-theme="light"]')}
    failures: list[str] = []

    for label, table in (("dark", dark), ("light", light)):
        colors = {name: parse_color(resolve(name, table)) for name in TEXT_TOKENS + BG_TOKENS}
        missing = [name for name, color in colors.items() if color is None]
        if missing:
            failures.append(f"{label}: unparsed tokens {', '.join(missing)}")
            continue
        page = colors["--bg-0"]
        assert page is not None
        page_rgb = page[:3]
        for text_name in TEXT_TOKENS:
            text = colors[text_name]
            assert text is not None
            text_rgb = text[:3]
            for bg_name in BG_TOKENS:
                bg = colors[bg_name]
                assert bg is not None
                bg_rgb = bg[:3] if bg[3] >= 0.999 else composite(bg, page_rgb)
                ratio = contrast(text_rgb, bg_rgb)
                if ratio < MIN_RATIO:
                    failures.append(
                        f"{label}: {text_name} on {bg_name} is {ratio:.2f}:1 (need {MIN_RATIO}:1)"
                    )

    if failures:
        print("Contrast check failed:")
        for line in failures:
            print(f"  {line}")
        return 1
    print(f"Contrast check passed ({MIN_RATIO}:1) for dark and light text/background tokens.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
