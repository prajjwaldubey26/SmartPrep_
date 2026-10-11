"""Each app page must load the design system, a navbar, and the theme toggle."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1] / "frontend"
PAGES = [
    "index.html",
    "login.html",
    "register.html",
    "dashboard.html",
    "interview.html",
    "sessions.html",
    "results.html",
    "chatbot.html",
]
failures = []
for name in PAGES:
    text = (ROOT / name).read_text(encoding="utf-8")
    if "css/design-system.css" not in text:
        failures.append(f"{name}: missing design-system.css")
    if "<nav" not in text and "studio-nav-wrap" not in text:
        failures.append(f"{name}: missing navbar")
    if "js/ui-core.js" not in text:
        failures.append(f"{name}: missing theme toggle (ui-core.js)")

if failures:
    print("Page check failed:")
    print("\n".join(failures))
    sys.exit(1)
print(f"Page check passed for {len(PAGES)} pages.")
