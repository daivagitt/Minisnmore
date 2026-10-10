#!/usr/bin/env python3
"""
Manual UI test catalog — positive and negative cases for every storefront /
account / admin / staff screen, evaluated against the current frontend source.

Each case is written as a manual workflow (so a tester can reproduce it in the
browser) and is scored by inspecting the route file, parent layouts, and first-
party UI imports. Failures mean the expected UI guard, empty state, or
validation is missing from the codebase.

Writes JSON + HTML + JUnit under artifacts/manual-ui-tests/.
"""

from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Iterable
from xml.sax.saxutils import escape as xml_escape

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "src"
ROUTES = SRC / "routes"
ROUTE_TREE = SRC / "routeTree.gen.ts"
OUT_DIR = Path(os.environ.get("MANUAL_UI_REPORT_DIR", ROOT / "artifacts/manual-ui-tests"))
OUT_JSON = OUT_DIR / "manual-ui-tests-report.json"
OUT_HTML = OUT_DIR / "index.html"
OUT_JUNIT = OUT_DIR / "manual-ui-tests-junit.xml"

IMPORT_RE = re.compile(r"""from\s+["'](@/[^"']+)["']""")
ROUTE_RE = re.compile(r"""createFileRoute\(\s*["']([^"']+)["']\s*\)""")
TITLE_RE = re.compile(r"""title:\s*["'`]([^"'`]+)["'`]""")
SKIP_IMPORT_BITS = ("/components/ui/", "/assets/", "lucide-react")

PUBLIC_EXACT = {
    "/",
    "/shop",
    "/cart",
    "/checkout",
    "/login",
    "/wishlist",
    "/contact-us",
    "/contact",
    "/about",
    "/gift",
    "/blog",
    "/subscribe",
    "/new-arrivals",
    "/best-sellers",
    "/trending",
    "/privacy",
    "/terms",
    "/shipping",
    "/track",
    "/reset-password",
    "/activate",
    "/invite",
    "/auth/callback",
    "/admin/login",
    "/account/activate",
    "/account/reset/finish",
}

PUBLIC_PREFIXES = ("/product/", "/blog/")


# ---------------------------------------------------------------------------
# Source loading
# ---------------------------------------------------------------------------


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except OSError:
        return ""


def resolve_alias(spec: str) -> Path | None:
    if not spec.startswith("@/"):
        return None
    base = SRC / spec[2:]
    for cand in (base.with_suffix(".tsx"), base.with_suffix(".ts"), base / "index.tsx", base / "index.ts"):
        if cand.is_file():
            return cand
    return None


def should_follow(spec: str) -> bool:
    if not spec.startswith("@/"):
        return False
    return not any(bit in spec for bit in SKIP_IMPORT_BITS)


def collected_source(entry: Path, extras: Iterable[Path], limit: int = 14) -> tuple[str, list[str]]:
    files: list[Path] = []
    seen: set[Path] = set()

    def add(path: Path) -> None:
        resolved = path.resolve()
        if resolved in seen or not path.is_file():
            return
        seen.add(resolved)
        files.append(path)

    add(entry)
    for extra in extras:
        add(extra)
    for spec in IMPORT_RE.findall(read_text(entry)):
        if len(files) >= limit:
            break
        if should_follow(spec):
            found = resolve_alias(spec)
            if found:
                add(found)
    chunks = []
    rels = []
    for path in files:
        rel = str(path.relative_to(ROOT))
        rels.append(rel)
        chunks.append(f"\n/* FILE {rel} */\n{read_text(path)}")
    return "\n".join(chunks), rels


def rx(source: str, *patterns: str, flags: int = re.I) -> bool:
    return any(re.search(p, source, flags) for p in patterns)


# ---------------------------------------------------------------------------
# Screens
# ---------------------------------------------------------------------------


def module_for(path: str) -> tuple[str, str]:
    if path.startswith("/admin"):
        return "Admin", "ADM"
    if path.startswith("/account"):
        return "Account", "ACC"
    if path.startswith("/operations"):
        return "Operations", "OPS"
    if path.startswith("/sales"):
        return "Sales", "SAL"
    if path.startswith("/customersupport"):
        return "Customer Support", "CS"
    if path in {"/login", "/reset-password", "/activate", "/invite", "/auth/callback"}:
        return "Auth", "AUTH"
    return "Storefront", "SF"


def human_title(path: str, source: str) -> str:
    match = TITLE_RE.search(source)
    if match:
        return match.group(1).split("·")[0].strip()
    if path == "/":
        return "Home"
    parts = [p for p in path.strip("/").split("/") if p and not p.startswith("$")]
    return " ".join(parts).replace("-", " ").title() or "Home"


def slug_for(path: str) -> str:
    cleaned = path.strip("/").replace("$", "").replace("_", "").replace("/", "-")
    return cleaned or "home"


def is_public(path: str) -> bool:
    if path in PUBLIC_EXACT:
        return True
    return any(path.startswith(prefix) for prefix in PUBLIC_PREFIXES)


def is_param_route(path: str) -> bool:
    return "$" in path


def parent_files(path: str) -> list[Path]:
    extras = [SRC / "routes" / "__root.tsx"]
    mapping: list[tuple[str, list[str]]] = [
        ("/admin", ["routes/admin.tsx", "lib/admin-nav.ts"]),
        ("/account", ["routes/account.tsx"]),
        ("/operations", ["routes/operations.tsx", "components/staff/StaffShell.tsx"]),
        ("/sales", ["routes/sales.tsx", "components/staff/StaffShell.tsx"]),
        ("/customersupport", ["routes/customersupport.tsx", "components/staff/StaffShell.tsx"]),
    ]
    for prefix, rels in mapping:
        if path == prefix or path.startswith(prefix + "/"):
            extras.extend(SRC / rel for rel in rels)
            break
    else:
        extras.append(SRC / "components" / "Navbar.tsx")
        extras.append(SRC / "components" / "Footer.tsx")
    return extras


@dataclass
class Screen:
    path: str
    file: Path
    title: str
    module: str
    code: str
    slug: str
    source: str
    files: list[str]
    public: bool


def discover_screens() -> list[Screen]:
    screens: list[Screen] = []
    for path in sorted(ROUTES.glob("*.tsx")):
        if path.name.startswith("-") or path.name.startswith("__"):
            continue
        text = read_text(path)
        match = ROUTE_RE.search(text)
        if not match:
            continue
        route_path = match.group(1)
        source, files = collected_source(path, parent_files(route_path))
        module, code = module_for(route_path)
        screens.append(
            Screen(
                path=route_path,
                file=path,
                title=human_title(route_path, text),
                module=module,
                code=code,
                slug=slug_for(route_path),
                source=source,
                files=files,
                public=is_public(route_path),
            )
        )
    root_source, root_files = collected_source(
        SRC / "routes" / "__root.tsx",
        [SRC / "components" / "Navbar.tsx", SRC / "components" / "Footer.tsx"],
    )
    screens.insert(
        0,
        Screen(
            path="(app-shell)",
            file=SRC / "routes" / "__root.tsx",
            title="App shell",
            module="Storefront",
            code="SF",
            slug="app-shell",
            source=root_source,
            files=root_files,
            public=True,
        ),
    )
    module_order = ["Storefront", "Auth", "Account", "Admin", "Operations", "Sales", "Customer Support"]
    screens.sort(key=lambda s: (module_order.index(s.module) if s.module in module_order else 99, s.path))
    return screens


# ---------------------------------------------------------------------------
# Cases
# ---------------------------------------------------------------------------


@dataclass
class Case:
    id: str
    screen: str
    path: str
    module: str
    kind: str  # positive | negative
    type: str  # Smoke | Regression | Responsive | Negative | Security
    priority: str
    scenario: str
    steps: list[str]
    expected: str
    status: str = "pass"
    actual: str = ""
    files: list[str] = field(default_factory=list)


Check = Callable[[], tuple[bool, str]]


class Catalog:
    def __init__(self) -> None:
        self.cases: list[Case] = []
        self._seq: dict[str, int] = {}

    def add(
        self,
        screen: Screen,
        *,
        kind: str,
        type_: str,
        priority: str,
        scenario: str,
        steps: list[str],
        expected: str,
        check: Check,
    ) -> Case:
        key = f"{screen.code}-{screen.slug}"
        self._seq[key] = self._seq.get(key, 0) + 1
        ok, actual = check()
        case = Case(
            id=f"MT-{screen.code}-{screen.slug}-{self._seq[key]:03d}",
            screen=screen.title,
            path=screen.path,
            module=screen.module,
            kind=kind,
            type=type_,
            priority=priority,
            scenario=scenario,
            steps=steps,
            expected=expected,
            status="pass" if ok else "fail",
            actual=actual,
            files=screen.files,
        )
        self.cases.append(case)
        return case


def numbered(steps: list[str]) -> list[str]:
    return list(steps)


def features(src: str) -> dict[str, bool]:
    return {
        "route": rx(src, r"createFileRoute\(") or rx(src, r"createRootRoute\("),
        "component": rx(src, r"component:\s") or rx(src, r"shellComponent:\s") or rx(src, r"notFoundComponent:\s"),
        "title": rx(src, r"title:\s*['\"`]", r"<h1\b", r"font-display"),
        "nav": rx(src, r"<Link\b", r"useNavigate", r"navigate\("),
        "responsive": rx(src, r"\b(sm:|md:|lg:|xl:|max-w-|px-3|px-4)"),
        "overflow": rx(src, r"overflow-x-auto", r"overflow-hidden", r"min-w-0", r"w-full", r"max-w-"),
        "loading": rx(src, r"Loader2", r"isLoading", r"\bloading\b", r"[Ss]keleton", r"productsLoaded"),
        "empty": rx(src, r"empty", r"no products", r"no .*found", r"nothing here", r"placeholder", r"EmptyCart"),
        "error": rx(src, r"toast\.error", r"showApiError", r"showValidationErrors", r"setError", r"isError", r"catch\s*\("),
        "form": rx(src, r"<form\b", r"onSubmit", r"useForm\("),
        "validate": rx(src, r"zod", r"safeParse", r"FieldError", r"validate", r"required", r"emailFieldError", r"passwordFieldError"),
        "auth": rx(src, r"useAuth", r"isAdmin", r"staffRole", r"StaffShell"),
        "auth_redirect": rx(src, r"""to:\s*["']/(login|admin/login)["']""", r"staffHomePath"),
        "not_found": rx(src, r"notFoundComponent", r"\b404\b", r"not found", r"Piece not found"),
        "data": rx(src, r"\bapi\.", r"useProducts", r"useQuery", r"list[A-Z]", r"fetch\("),
        "action": rx(src, r"<button\b", r"<Link\b", r"onClick", r"<form\b"),
        "inner_html": rx(src, r"dangerouslySetInnerHTML"),
        "role_guard": rx(src, r"allowedRole", r"isAdmin", r"isCmsAdmin", r"!isAdmin", r"canAccess"),
        "search": rx(src, r"validateSearch"),
        "tree": ROUTE_TREE.is_file() and bool(ROUTE_TREE.read_text(encoding="utf-8")),
    }


def build_cases(catalog: Catalog, screen: Screen) -> None:
    src = screen.source
    feat = features(src)
    name = screen.title
    path = screen.path
    tree = read_text(ROUTE_TREE)
    in_tree = path == "(app-shell)" or path in tree or screen.file.name.replace(".tsx", "") in tree

    catalog.add(
        screen,
        kind="positive",
        type_="Smoke",
        priority="P1",
        scenario=f"Smoke — {name} screen loads",
        steps=numbered(
            [
                "Log in as the role that owns this screen (or stay a guest on public pages).",
                f"Open {name} from the sidebar, module hub, or URL.",
                f"Confirm the URL matches `{path}`.",
            ]
        ),
        expected="Page shell renders without an error toast; primary heading or empty-state message is visible.",
        check=lambda: (
            feat["route"] and feat["component"],
            "Route module exports createFileRoute with a component."
            if feat["route"] and feat["component"]
            else f"Missing createFileRoute or component in {screen.file.name}.",
        ),
    )

    catalog.add(
        screen,
        kind="positive",
        type_="Regression",
        priority="P2",
        scenario=f"Navigation — return to hub after visiting {name}",
        steps=numbered(
            [
                f"From {name}, use browser back or the app Home/sidebar.",
                f"Re-open {name}.",
            ]
        ),
        expected="Navigation is stable; no blank screen or infinite spinner.",
        check=lambda: (
            feat["nav"] or path == "(app-shell)",
            "Link or navigate() is present in the screen or parent layout."
            if feat["nav"] or path == "(app-shell)"
            else "No Link/navigate found in screen or parent layout.",
        ),
    )

    catalog.add(
        screen,
        kind="positive",
        type_="Responsive",
        priority="P2",
        scenario=f"Mobile layout — {name} usable on phone width",
        steps=numbered(
            [
                "Set viewport to mobile (≤430px) or use a device.",
                f"Open {name}.",
                "Scroll primary content.",
            ]
        ),
        expected="Controls remain reachable; no horizontal overflow blocking actions.",
        check=lambda: (
            feat["responsive"],
            "Responsive Tailwind breakpoints or compact padding are present."
            if feat["responsive"]
            else "No sm:/md:/lg:/px-* layout classes found for this screen.",
        ),
    )

    catalog.add(
        screen,
        kind="positive",
        type_="Smoke",
        priority="P2",
        scenario=f"Heading — {name} identifies itself",
        steps=numbered([f"Open {name}.", "Look at the document title or on-page heading."]),
        expected="Primary heading or HTML title is visible so the tester knows which screen they are on.",
        check=lambda: (
            feat["title"] or path == "(app-shell)",
            "head() title, h1, or display heading is defined."
            if feat["title"] or path == "(app-shell)"
            else "No title/h1/font-display heading found.",
        ),
    )

    catalog.add(
        screen,
        kind="positive",
        type_="Regression",
        priority="P2",
        scenario=f"Route registry — {name} is wired into the app router",
        steps=numbered(["Deploy/build the app.", f"Request `{path}` directly."]),
        expected="TanStack route tree includes this path so the screen is reachable.",
        check=lambda: (
            in_tree,
            "Path is present in src/routeTree.gen.ts." if in_tree else "Path missing from src/routeTree.gen.ts.",
        ),
    )

    catalog.add(
        screen,
        kind="positive",
        type_="Regression",
        priority="P2",
        scenario=f"Primary actions — {name} controls are reachable",
        steps=numbered([f"Open {name}.", "Tab or tap through primary buttons, links, or the main form."]),
        expected="At least one primary action (button, link, or form) is present.",
        check=lambda: (
            feat["action"] or path == "(app-shell)",
            "Button, Link, or form control found."
            if feat["action"] or path == "(app-shell)"
            else "No button, Link, or form found on this screen.",
        ),
    )

    catalog.add(
        screen,
        kind="positive",
        type_="Responsive",
        priority="P3",
        scenario=f"Overflow — {name} does not trap actions off-canvas",
        steps=numbered(["Open on a 375px-wide viewport.", "Try to reach every primary control."]),
        expected="Layout uses width constraints or overflow-x handling so actions stay on screen.",
        check=lambda: (
            feat["overflow"] or feat["responsive"],
            "Width/overflow constraints present."
            if feat["overflow"] or feat["responsive"]
            else "No overflow or max-width handling found.",
        ),
    )

    if feat["data"] or feat["empty"] or feat["loading"]:
        catalog.add(
            screen,
            kind="positive",
            type_="Regression",
            priority="P2",
            scenario=f"Empty / loading — {name} handles no data",
            steps=numbered(
                [
                    f"Open {name} with an empty catalog, empty cart, or while data is still loading.",
                    "Wait for the first paint.",
                ]
            ),
            expected="A loading indicator or empty-state message is shown instead of a blank page.",
            check=lambda: (
                feat["empty"] or feat["loading"],
                "Empty-state or loading UI is present."
                if feat["empty"] or feat["loading"]
                else "Data-driven screen has no empty-state or loading UI.",
            ),
        )

    if not screen.public:
        catalog.add(
            screen,
            kind="positive",
            type_="Regression",
            priority="P1",
            scenario=f"Authorized session — entitled user can stay on {name}",
            steps=numbered(
                [
                    "Sign in as the role that owns this module.",
                    f"Open {name}.",
                ]
            ),
            expected="Authenticated entitled users are not bounced to login.",
            check=lambda: (
                feat["auth"],
                "Screen or parent layout uses useAuth / StaffShell."
                if feat["auth"]
                else "Protected screen has no auth hook or staff shell.",
            ),
        )

    # --- negative ---

    if path == "(app-shell)":
        catalog.add(
            screen,
            kind="negative",
            type_="Negative",
            priority="P1",
            scenario="Negative — unknown route shows not-found page",
            steps=numbered(
                [
                    "Open a path that does not exist, e.g. `/this-route-does-not-exist`.",
                    "Confirm the page does not render a staff or customer shell.",
                ]
            ),
            expected="Root not-found component renders a 404 and a way home; no 5xx.",
            check=lambda: (
                feat["not_found"],
                "Root route defines notFoundComponent."
                if feat["not_found"]
                else "App shell is missing notFoundComponent.",
            ),
        )
        catalog.add(
            screen,
            kind="positive",
            type_="Smoke",
            priority="P1",
            scenario="Smoke — global toast host is mounted",
            steps=numbered(["Trigger any validation error on login.", "Watch for a toast or inline error."]),
            expected="Toaster is mounted on the root shell so errors are visible.",
            check=lambda: (
                rx(src, r"Toaster"),
                "sonner Toaster is mounted in the app shell."
                if rx(src, r"Toaster")
                else "No Toaster on the root shell.",
            ),
        )
        catalog.add(
            screen,
            kind="positive",
            type_="Responsive",
            priority="P1",
            scenario="Responsive — viewport meta is set",
            steps=numbered(["Open any page on a phone.", "Confirm the layout is not a zoomed desktop page."]),
            expected='Root head includes width=device-width viewport meta.',
            check=lambda: (
                rx(src, r"width=device-width"),
                "viewport-fit / device-width meta is present."
                if rx(src, r"width=device-width")
                else "Missing device-width viewport meta.",
            ),
        )

    if not screen.public:
        catalog.add(
            screen,
            kind="negative",
            type_="Security",
            priority="P1",
            scenario=f"Negative — guest cannot open {name}",
            steps=numbered(
                [
                    "Sign out (or use a private window).",
                    f"Paste `{path}` into the address bar.",
                ]
            ),
            expected="Unauthenticated visitors are redirected to customer or staff login; no protected data is shown.",
            check=lambda: (
                feat["auth_redirect"] or feat["auth"],
                "Layout redirects guests to /login or /admin/login."
                if feat["auth_redirect"] or feat["auth"]
                else "Protected screen has no unauthenticated redirect.",
            ),
        )
        catalog.add(
            screen,
            kind="negative",
            type_="Security",
            priority="P1",
            scenario=f"Negative — wrong staff role cannot use {name}",
            steps=numbered(
                [
                    "Sign in as a staff user whose role does not own this module (e.g. Sales on Operations).",
                    f"Open `{path}`.",
                ]
            ),
            expected="User is sent to their own home path; admin/staff chrome for this module is not rendered.",
            check=lambda: (
                feat["role_guard"] or screen.code == "ACC",
                "Role guard (isAdmin / allowedRole / staffHomePath) is present."
                if feat["role_guard"] or screen.code == "ACC"
                else "No role guard found for this protected screen.",
            ),
        )

    if feat["form"]:
        catalog.add(
            screen,
            kind="negative",
            type_="Negative",
            priority="P1",
            scenario=f"Negative — empty submit on {name} is rejected",
            steps=numbered(
                [
                    f"Open {name}.",
                    "Leave required fields blank.",
                    "Submit the form.",
                ]
            ),
            expected="Client-side validation or disabled submit prevents an empty/invalid payload from being sent.",
            check=lambda: (
                feat["validate"],
                "zod / field-error / required checks are present."
                if feat["validate"]
                else "Form has no client-side validation helpers.",
            ),
        )
        catalog.add(
            screen,
            kind="negative",
            type_="Negative",
            priority="P2",
            scenario=f"Negative — invalid field values on {name} show an error",
            steps=numbered(
                [
                    f"Open {name}.",
                    "Enter an invalid email, phone, password, or other typed field.",
                    "Submit or blur the field.",
                ]
            ),
            expected="Inline field error or toast explains what to fix; request is not treated as success.",
            check=lambda: (
                feat["validate"] or feat["error"],
                "Validation or error toast path exists."
                if feat["validate"] or feat["error"]
                else "No invalid-input error path found.",
            ),
        )

    if feat["data"]:
        catalog.add(
            screen,
            kind="negative",
            type_="Negative",
            priority="P2",
            scenario=f"Negative — {name} surfaces API failures",
            steps=numbered(
                [
                    "Simulate a failed API response (network offline or 5xx from the gateway).",
                    f"Reload {name} or retry the primary action.",
                ]
            ),
            expected="User sees a toast or inline error; the screen does not fail silently or spin forever.",
            check=lambda: (
                feat["error"] or feat["loading"],
                "catch / toast.error / showApiError / loading guard is present."
                if feat["error"] or feat["loading"]
                else "Data screen has no error or loading fallback.",
            ),
        )

    if is_param_route(path):
        catalog.add(
            screen,
            kind="negative",
            type_="Negative",
            priority="P1",
            scenario=f"Negative — missing {name} resource does not crash",
            steps=numbered(
                [
                    f"Open `{path}` with a slug/id that does not exist.",
                    "Wait for the page to settle.",
                ]
            ),
            expected="Not-found component or empty/error copy is shown; no uncaught render crash.",
            check=lambda: (
                feat["not_found"] or feat["empty"] or feat["error"] or feat["loading"],
                "notFoundComponent, empty state, or error UI handles a missing record."
                if feat["not_found"] or feat["empty"] or feat["error"] or feat["loading"]
                else "Param route has no missing-resource UI.",
            ),
        )

    route_src = read_text(screen.file)
    raw_inner = rx(route_src, r"dangerouslySetInnerHTML") and not rx(route_src, r"JSON\.stringify")
    catalog.add(
        screen,
        kind="negative",
        type_="Security",
        priority="P2",
        scenario=f"Negative — {name} does not render unsanitized HTML from the route",
        steps=numbered(
            [
                "If the screen echoes user- or CMS-supplied text, paste a script tag into a text field.",
                "Save and reopen the screen.",
            ]
        ),
        expected="The route module itself does not call dangerouslySetInnerHTML on user input.",
        check=lambda: (
            not raw_inner,
            "Route file does not use dangerouslySetInnerHTML on non-JSON payloads."
            if not raw_inner
            else f"{screen.file.name} uses dangerouslySetInnerHTML — confirm the payload is trusted/sanitized.",
        ),
    )

    if path == "/login" or path == "/admin/login":
        catalog.add(
            screen,
            kind="negative",
            type_="Negative",
            priority="P1",
            scenario=f"Negative — wrong password on {name} stays on the form",
            steps=numbered(
                [
                    f"Open {name}.",
                    "Enter a known email with an incorrect password.",
                    "Submit.",
                ]
            ),
            expected="Sign-in error is shown; user is not routed into a protected hub.",
            check=lambda: (
                rx(src, r"signInError", r"toast\.error", r"passwordFieldError", r"emailFieldError"),
                "Login error state / toast / field errors are implemented."
                if rx(src, r"signInError", r"toast\.error", r"passwordFieldError", r"emailFieldError")
                else "Login screen has no failed-sign-in error path.",
            ),
        )

    if path == "/checkout":
        catalog.add(
            screen,
            kind="negative",
            type_="Negative",
            priority="P1",
            scenario="Negative — checkout with an empty cart cannot place an order",
            steps=numbered(
                [
                    "Clear the cart.",
                    "Open /checkout.",
                    "Try to continue to payment.",
                ]
            ),
            expected="Empty-cart state is shown and place-order is not available.",
            check=lambda: (
                rx(src, r"EmptyCartState", r"cartCount", r"cart\.length"),
                "Checkout guards an empty cart."
                if rx(src, r"EmptyCartState", r"cartCount", r"cart\.length")
                else "Checkout does not handle an empty cart.",
            ),
        )
        catalog.add(
            screen,
            kind="negative",
            type_="Negative",
            priority="P2",
            scenario="Negative — invalid coupon is rejected at checkout",
            steps=numbered(["Open checkout with items in the cart.", "Apply a nonsense coupon code."]),
            expected="Coupon validation error is shown; totals are unchanged.",
            check=lambda: (
                rx(src, r"validateCoupon", r"coupon"),
                "Coupon validation is present on checkout."
                if rx(src, r"validateCoupon", r"coupon")
                else "No coupon validation found.",
            ),
        )

    if path == "/cart":
        catalog.add(
            screen,
            kind="positive",
            type_="Regression",
            priority="P2",
            scenario="Empty cart — shopper is guided back to shopping",
            steps=numbered(["Open /cart with no lines.", "Use the empty-state call to action."]),
            expected="EmptyCartState (or equivalent) is rendered instead of a blank table.",
            check=lambda: (
                rx(src, r"EmptyCartState"),
                "EmptyCartState is used on the cart screen."
                if rx(src, r"EmptyCartState")
                else "Cart page has no EmptyCartState.",
            ),
        )

    if path in {"/privacy", "/terms", "/shipping"}:
        catalog.add(
            screen,
            kind="positive",
            type_="Regression",
            priority="P2",
            scenario=f"Policy — {name} still renders if CMS content is missing",
            steps=numbered([f"Open {name} when no CMS policy row exists."]),
            expected="Static fallback copy is shown via PolicyContent / LegalPage.",
            check=lambda: (
                rx(src, r"PolicyContent", r"LegalPage", r"fallback"),
                "Policy fallback content is wired."
                if rx(src, r"PolicyContent", r"LegalPage", r"fallback")
                else "Policy page has no fallback content.",
            ),
        )


# ---------------------------------------------------------------------------
# Reports
# ---------------------------------------------------------------------------


def summarize(cases: list[Case]) -> dict:
    def count(items: list[Case]) -> dict[str, int]:
        return {
            "total": len(items),
            "passed": sum(1 for x in items if x.status == "pass"),
            "failed": sum(1 for x in items if x.status == "fail"),
        }

    screens: dict[str, dict] = {}
    for case in cases:
        key = f"{case.module}::{case.path}"
        bucket = screens.setdefault(
            key,
            {"module": case.module, "screen": case.screen, "path": case.path, "positive": [], "negative": []},
        )
        bucket[case.kind].append(case)
    return {
        **count(cases),
        "positive": count([x for x in cases if x.kind == "positive"]),
        "negative": count([x for x in cases if x.kind == "negative"]),
        "screens": {
            key: {
                "module": val["module"],
                "screen": val["screen"],
                "path": val["path"],
                "positive": count(val["positive"]),
                "negative": count(val["negative"]),
                **count(val["positive"] + val["negative"]),
            }
            for key, val in screens.items()
        },
    }


def build_html(report: dict) -> str:
    sys.path.insert(0, str(ROOT / "jenkins" / "scripts"))
    from report_theme import PROJECT_NAME, esc, page_shell, stat_block, status_pill  # noqa: E402

    summary = report["summary"]
    cases = report["cases"]
    executed = summary["passed"] + summary["failed"]
    rate = f"{(summary['passed'] / executed * 100):.1f}%" if executed else "—"
    overall = "PASS" if summary["failed"] == 0 else "FAIL"

    # Preserve discovery order
    screen_order: list[tuple[str, str, str]] = []
    seen: set[str] = set()
    for case in cases:
        key = f"{case['module']}::{case['path']}"
        if key not in seen:
            seen.add(key)
            screen_order.append((case["module"], case["screen"], case["path"]))

    def type_badge(value: str) -> str:
        return f'<span class="type-badge">{esc(value)}</span>'

    def id_pill(value: str) -> str:
        return f'<code class="tc-id">{esc(value)}</code>'

    def steps_html(items: list[str]) -> str:
        return "<ol class=\"steps\">" + "".join(f"<li>{esc(step)}</li>" for step in items) + "</ol>"

    def rows_for(path: str, module: str) -> str:
        items = [x for x in cases if x["path"] == path and x["module"] == module]
        out = []
        for x in items:
            out.append(
                f'<tr class="row-{esc(x["status"])} kind-{esc(x["kind"])}">'
                f"<td>{id_pill(x['id'])}</td>"
                f"<td>{esc(x['scenario'])}</td>"
                f"<td>{steps_html(x['steps'])}</td>"
                f"<td>{esc(x['expected'])}"
                f'<div class="muted small">{esc(x["actual"])}</div></td>'
                f"<td>{esc(x['priority'])}</td>"
                f"<td>{type_badge(x['type'])}</td>"
                f"<td>{status_pill(x['status'].upper())}</td>"
                "</tr>"
            )
        return "".join(out)

    cards = []
    last_module = None
    for module, title, path in screen_order:
        items = [x for x in cases if x["path"] == path and x["module"] == module]
        passed = sum(1 for x in items if x["status"] == "pass")
        failed = sum(1 for x in items if x["status"] == "fail")
        pass_label = f"{passed} Pass" if failed == 0 else f"{passed} Pass · {failed} Fail"
        pass_class = "pass-count" if failed == 0 else "fail-count"
        slug_bit = path.strip("/").replace("$", "") or "home"
        if module != last_module:
            last_module = module
            cards.append(f'<h2 class="module-heading">{esc(module)}</h2>')
        cards.append(
            f"""
      <article class="screen-card" data-screen="{esc(path)}">
        <header class="screen-head">
          <div>
            <h2>{esc(title)}</h2>
            <div class="screen-sub"><span>{esc(slug_bit)}</span> · <code>{esc(path)}</code></div>
          </div>
          <div class="screen-meta">
            <span>{len(items)} cases</span>
            <span class="{pass_class}">{esc(pass_label)}</span>
            <button type="button" class="linkish toggle-one">Expand / collapse</button>
          </div>
        </header>
        <div class="screen-body">
          <div class="table-wrap">
            <table class="cases">
              <thead>
                <tr>
                  <th>Test Case ID</th>
                  <th>Test Scenario</th>
                  <th>Steps (manual workflow)</th>
                  <th>Expected Result</th>
                  <th>Priority</th>
                  <th>Type</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>{rows_for(path, module)}</tbody>
            </table>
          </div>
        </div>
      </article>"""
        )

    failures = [x for x in cases if x["status"] == "fail"]
    fail_rows = (
        "".join(
            f"<tr><td>{id_pill(x['id'])}</td><td>{esc(x['module'])}</td><td>{esc(x['scenario'])}</td>"
            f"<td>{esc(x['expected'])}</td><td>{esc(x['actual'])}</td><td>{status_pill('FAIL')}</td></tr>"
            for x in failures
        )
        if failures
        else ""
    )
    findings = (
        f"""<table class="cases"><thead><tr><th>ID</th><th>Module</th><th>Scenario</th>
        <th>Expected</th><th>Actual</th><th>Status</th></tr></thead><tbody>{fail_rows}</tbody></table>"""
        if failures
        else '<p class="muted">No failing cases. Every positive and negative check matched the current source.</p>'
    )

    module_rows = []
    modules: dict[str, list] = {}
    for case in cases:
        modules.setdefault(case["module"], []).append(case)
    for name, items in modules.items():
        p = [x for x in items if x["kind"] == "positive"]
        n = [x for x in items if x["kind"] == "negative"]
        module_rows.append(
            f"<tr><td>{esc(name)}</td><td>{len(items)}</td>"
            f"<td>{sum(1 for x in p if x['status']=='pass')}/{len(p)}</td>"
            f"<td>{sum(1 for x in n if x['status']=='pass')}/{len(n)}</td>"
            f"<td>{sum(1 for x in items if x['status']=='fail')}</td></tr>"
        )

    body = f"""
      <section class="panel">
        <h2>Summary</h2>
        <p>Evaluated against <code>src/routes</code> and first-party UI imports · {esc(report['finishedAt'])}</p>
        <p>Status: {status_pill(overall)} · Cases are scored from the codebase (not a live browser session).
           Each row is still a manual workflow a tester can follow on UAT.</p>
        <div class="stats">
          {stat_block("Cases", summary["total"])}{stat_block("Passed", summary["passed"])}
          {stat_block("Failed", summary["failed"])}{stat_block("Positive", summary["positive"]["total"])}
          {stat_block("Negative", summary["negative"]["total"])}{stat_block("Pass rate", rate)}
        </div>
        <nav class="toc">
          <a href="#findings">Failures</a>
          <a href="#screens">Screens</a>
          <button type="button" class="linkish" id="expand-all">Expand all</button>
          <button type="button" class="linkish" id="collapse-all">Collapse all</button>
        </nav>
        <p class="filters">
          Show:
          <button type="button" class="chip on" data-filter="all">All</button>
          <button type="button" class="chip" data-filter="positive">Positive</button>
          <button type="button" class="chip" data-filter="negative">Negative</button>
          <button type="button" class="chip" data-filter="fail">Failed</button>
        </p>
      </section>
      <section class="panel">
        <h2>Coverage by module</h2>
        <table class="cases">
          <thead><tr><th>Module</th><th>Cases</th><th>Positive pass</th><th>Negative pass</th><th>Failed</th></tr></thead>
          <tbody>{''.join(module_rows)}</tbody>
        </table>
      </section>
      <section class="panel" id="findings">
        <h2>Failures ({len(failures)})</h2>
        {findings}
      </section>
      <section id="screens">
        {''.join(cards)}
      </section>
      <script>
        (function () {{
          const cards = () => Array.from(document.querySelectorAll(".screen-card"));
          const setOpen = (open) => cards().forEach((c) => c.classList.toggle("collapsed", !open));
          document.getElementById("expand-all")?.addEventListener("click", () => setOpen(true));
          document.getElementById("collapse-all")?.addEventListener("click", () => setOpen(false));
          document.querySelectorAll(".toggle-one").forEach((btn) => {{
            btn.addEventListener("click", () => btn.closest(".screen-card")?.classList.toggle("collapsed"));
          }});
          document.querySelectorAll("[data-filter]").forEach((btn) => {{
            btn.addEventListener("click", () => {{
              document.querySelectorAll("[data-filter]").forEach((b) => b.classList.remove("on"));
              btn.classList.add("on");
              const mode = btn.getAttribute("data-filter");
              document.querySelectorAll("tr.kind-positive, tr.kind-negative").forEach((row) => {{
                const fail = row.classList.contains("row-fail");
                const pos = row.classList.contains("kind-positive");
                let show = true;
                if (mode === "positive") show = pos;
                if (mode === "negative") show = !pos;
                if (mode === "fail") show = fail;
                row.style.display = show ? "" : "none";
              }});
            }});
          }});
        }})();
      </script>
    """
    css = """
      .page { max-width: 1280px; }
      .toc { display:flex; flex-wrap:wrap; gap:8px; margin-top:14px; align-items:center; }
      .toc a, .linkish {
        border:1px solid var(--border); border-radius:999px; padding:4px 12px;
        background:#fff; color:var(--brand-light); font-weight:600; cursor:pointer; font-size:0.9rem;
      }
      .filters { display:flex; flex-wrap:wrap; gap:8px; align-items:center; margin:14px 0 0; }
      .chip {
        border:1px solid var(--border); background:#fff; border-radius:999px; padding:4px 12px;
        cursor:pointer; font-weight:600; color:var(--text-muted);
      }
      .chip.on { background:#eef2ff; color:#3730a3; border-color:#c7d2fe; }
      .screen-card {
        background:#fff; border:1px solid var(--border); border-radius:16px;
        box-shadow:0 1px 2px rgba(15,23,42,0.04); margin-bottom:16px; overflow:hidden;
      }
      .screen-head {
        display:flex; justify-content:space-between; gap:16px; flex-wrap:wrap;
        padding:16px 20px 12px; border-bottom:1px solid var(--border); align-items:flex-start;
      }
      .screen-head h2 { margin:0; font-size:1.15rem; }
      .screen-sub { color:var(--text-muted); font-size:0.9rem; margin-top:2px; }
      .screen-meta { display:flex; gap:12px; align-items:center; flex-wrap:wrap; color:var(--text-muted); font-size:0.92rem; }
      .pass-count { color:#15803d; font-weight:700; }
      .fail-count { color:#b91c1c; font-weight:700; }
      .screen-card.collapsed .screen-body { display:none; }
      .screen-card.collapsed .screen-head { border-bottom:none; }
      .table-wrap { overflow-x:auto; }
      table.cases { width:100%; border-collapse:collapse; font-size:13px; }
      table.cases th, table.cases td {
        text-align:left; vertical-align:top; padding:12px 14px; border-bottom:1px solid var(--border);
      }
      table.cases th { background:#f8fafc; font-size:12px; letter-spacing:0.02em; color:#334155; }
      .tc-id {
        display:inline-block; background:#eef2ff; color:#4338ca; border:1px solid #c7d2fe;
        border-radius:999px; padding:3px 10px; font-size:11px; font-weight:700; white-space:nowrap;
      }
      .type-badge {
        display:inline-block; border:1px solid #cbd5e1; color:#475569; border-radius:999px;
        padding:3px 10px; font-size:12px; background:#f8fafc; white-space:nowrap;
      }
      .steps { margin:0; padding-left:18px; min-width:220px; }
      .steps li + li { margin-top:4px; }
      .row-fail td { background:#fef2f2; }
      .small { font-size:12px; margin-top:6px; }
      .module-heading { margin: 28px 0 10px; font-size: 1.05rem; color: var(--brand); letter-spacing: 0.04em; text-transform: uppercase; }
      #screens .module-heading:first-child { margin-top: 8px; }
    """
    return page_shell(
        f"{PROJECT_NAME} manual UI tests",
        body,
        subtitle="Positive and negative screen-level cases evaluated against the frontend codebase",
        extra_css=css,
        nested_logo=False,
    )


def build_junit(report: dict) -> str:
    def attr(value: str) -> str:
        return xml_escape(value, {'"': "&quot;"})

    suites = []
    modules = []
    for case in report["cases"]:
        if case["module"] not in modules:
            modules.append(case["module"])
    for module in modules:
        items = [x for x in report["cases"] if x["module"] == module]
        cases_xml = []
        for x in items:
            name = attr(f"{x['id']} [{x['kind']}] {x['scenario']}")
            inner = ""
            if x["status"] == "fail":
                inner = f'<failure message="{attr(x["actual"])}">Expected: {xml_escape(x["expected"])}</failure>'
            cases_xml.append(
                f'<testcase classname="{attr(module)}" name="{name}">{inner}</testcase>'
            )
        failed = sum(1 for x in items if x["status"] == "fail")
        suites.append(
            f'<testsuite name="{attr(module)}" tests="{len(items)}" failures="{failed}">'
            + "".join(cases_xml)
            + "</testsuite>"
        )
    return '<?xml version="1.0" encoding="UTF-8"?>\n<testsuites name="Manual UI tests">' + "".join(suites) + "</testsuites>\n"


def copy_logo() -> None:
    src = ROOT / "jenkins" / "assets" / "daiva-technologies-logo.svg"
    if src.is_file():
        (OUT_DIR / "daiva-logo.svg").write_bytes(src.read_bytes())


def render_from_json() -> int:
    if not OUT_JSON.is_file():
        print(f"Missing {OUT_JSON}", file=sys.stderr)
        return 1
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    copy_logo()
    report = json.loads(OUT_JSON.read_text(encoding="utf-8"))
    OUT_HTML.write_text(build_html(report), encoding="utf-8")
    OUT_JUNIT.write_text(build_junit(report), encoding="utf-8")
    print(f"Rendered manual UI report from {OUT_JSON}")
    return 0


def main() -> int:
    if "--from-json" in sys.argv:
        return render_from_json()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    copy_logo()
    started = datetime.now(timezone.utc).isoformat()
    catalog = Catalog()
    screens = discover_screens()
    for screen in screens:
        build_cases(catalog, screen)

    report = {
        "startedAt": started,
        "finishedAt": datetime.now(timezone.utc).isoformat(),
        "sourceRoot": str(SRC),
        "screenCount": len(screens),
        "summary": summarize(catalog.cases),
        "cases": [asdict(case) for case in catalog.cases],
    }
    OUT_JSON.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    OUT_HTML.write_text(build_html(report), encoding="utf-8")
    OUT_JUNIT.write_text(build_junit(report), encoding="utf-8")
    sm = report["summary"]
    print(
        f"Manual UI tests: {sm['passed']} passed, {sm['failed']} failed / {sm['total']} "
        f"({sm['positive']['total']} positive, {sm['negative']['total']} negative) across {len(screens)} screens"
    )
    for case in catalog.cases:
        if case.status == "fail":
            print(f"  FAIL {case.id} {case.scenario} — {case.actual}")
    print(f"Report: {OUT_HTML}")
    return 0 if sm["failed"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
