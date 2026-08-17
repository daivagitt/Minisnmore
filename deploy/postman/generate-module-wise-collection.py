#!/usr/bin/env python3
"""Generate module-wise Postman collection from OpenAPI with 2xx assertions."""

from __future__ import annotations

import argparse
import json
import re
import sys
import uuid
from pathlib import Path
from urllib.request import urlopen

HTTP_METHODS = {"get", "post", "put", "patch", "delete", "head", "options"}

TEST_SCRIPT_2XX = [
    "pm.test('Status is 2xx', function () {",
    "  pm.expect(pm.response.code).to.be.oneOf([200, 201, 202, 204]);",
    "});",
]

TEST_SCRIPT_AUTH = [
    "if (pm.response.code >= 200 && pm.response.code < 300) {",
    "  try {",
    "    const json = pm.response.json();",
    "    const token = json.id_token || json.idToken || json.token;",
    "    if (token) {",
    "      pm.environment.set('authToken', token);",
    "      pm.collectionVariables.set('authToken', token);",
    "    }",
    "  } catch (e) { /* empty body e.g. 204 */ }",
    "}",
] + TEST_SCRIPT_2XX

PUBLIC_AUTH_PATHS = (
    "/api/authenticate",
    "/api/register",
    "/api/activate",
    "/api/account/reset-password/init",
    "/api/account/reset-password/finish",
)

PUBLIC_PREFIXES = (
    "/api/v1/categories",
    "/api/v1/products",
    "/api/v1/blog-posts",
    "/api/v1/banners",
    "/api/v1/home-sections",
    "/api/v1/page-visibility",
    "/api/v1/site-settings",
)


def title_from_tag(tag: str) -> str:
    name = tag or "general"
    if name.endswith("-resource"):
        name = name[:-9]
    if name.endswith("-controller"):
        name = name[:-11]
    return " ".join(part.capitalize() for part in re.split(r"[-_\s]+", name) if part)


def ref_name(ref: str) -> str:
    return ref.rsplit("/", 1)[-1]


def example_for_schema(schemas: dict, schema: dict, name: str = "value", depth: int = 0, seen: set | None = None):
    if seen is None:
        seen = set()
    if depth > 5 or not isinstance(schema, dict):
        return None
    if "example" in schema:
        return schema["example"]
    if "default" in schema:
        return schema["default"]
    if "$ref" in schema:
        key = ref_name(schema["$ref"])
        if key in seen:
            return {}
        return example_for_schema(schemas, schemas.get(key, {}), key, depth + 1, seen | {key})
    for combinator in ("oneOf", "anyOf", "allOf"):
        if combinator in schema and schema[combinator]:
            if combinator == "allOf":
                merged = {}
                for part in schema[combinator]:
                    value = example_for_schema(schemas, part, name, depth + 1, seen)
                    if isinstance(value, dict):
                        merged.update(value)
                return merged
            return example_for_schema(schemas, schema[combinator][0], name, depth + 1, seen)
    if "enum" in schema and schema["enum"]:
        return schema["enum"][0]
    schema_type = schema.get("type")
    if isinstance(schema_type, list):
        schema_type = next((item for item in schema_type if item != "null"), schema_type[0])
    fmt = schema.get("format")
    lname = str(name).lower()
    if schema_type == "object" or "properties" in schema:
        result = {}
        for prop_name, prop_schema in (schema.get("properties", {}) or {}).items():
            if isinstance(prop_schema, dict) and prop_schema.get("readOnly"):
                continue
            result[prop_name] = example_for_schema(schemas, prop_schema, prop_name, depth + 1, seen)
        return result
    if schema_type == "array":
        return [example_for_schema(schemas, schema.get("items", {}), name, depth + 1, seen)]
    if schema_type == "integer":
        return 1
    if schema_type == "number":
        return 99.99
    if schema_type == "boolean":
        return True
    if fmt == "date-time":
        return "2026-05-23T00:00:00Z"
    if fmt == "date":
        return "2026-05-23"
    if fmt == "email" or "email" in lname:
        return "{{email}}"
    if "password" in lname:
        return "{{password}}"
    if lname in {"username", "login"}:
        return "{{username}}"
    if lname in {"id", "orderid"} or lname.endswith("id"):
        return "{{id}}"
    if "phone" in lname:
        return "9876543210"
    if "pincode" in lname:
        return "500001"
    if lname == "currency":
        return "INR"
    if "url" in lname:
        return "https://example.com/resource"
    if "code" in lname:
        return "TESTCODE"
    if "name" in lname:
        return "Sample Name"
    if "title" in lname:
        return "Sample Title"
    return "sample-" + str(name)


def example_for_param(schemas: dict, param: dict):
    schema = param.get("schema", {})
    while isinstance(schema, dict) and "$ref" in schema:
        schema = schemas.get(ref_name(schema["$ref"]), {})
    return example_for_schema(schemas, schema, param.get("name", "value"))


def normalize_path_for_postman(path: str, collection_vars: dict) -> str:
    for name in re.findall(r"\{([^}]+)\}", path):
        collection_vars.setdefault(name, "{{email}}" if name == "login" else "{{id}}")
    return re.sub(r"\{([^}]+)\}", lambda m: "{{" + m.group(1) + "}}", path)


def postman_path_parts(path: str) -> list[str]:
    return [part for part in path.strip("/").split("/") if part]


def is_public_endpoint(path: str, method: str) -> bool:
    if path.startswith(PUBLIC_AUTH_PATHS):
        return True
    if method.lower() == "get" and path.startswith(PUBLIC_PREFIXES):
        return True
    return False


def query_params(schemas: dict, operation: dict) -> list[dict]:
    params = []
    for param in operation.get("parameters", []) or []:
        if param.get("in") != "query":
            continue
        value = example_for_param(schemas, param)
        params.append(
            {
                "key": param.get("name", ""),
                "value": "" if value is None else str(value),
                "description": param.get("description", ""),
                "disabled": not param.get("required", False),
            }
        )
    return params


def request_body(schemas: dict, operation: dict):
    content = (operation.get("requestBody") or {}).get("content") or {}
    media_type = None
    media = None
    for candidate in ("application/json", "application/*+json", "*/*", "text/plain"):
        if candidate in content:
            media_type = candidate if candidate != "application/*+json" else "application/json"
            media = content[candidate if candidate != "application/*+json" else "application/json"]
            break
    if media is None and content:
        media_type = next(iter(content))
        media = content[media_type]
    if not media:
        return None, []
    example = media.get("example") or example_for_schema(schemas, media.get("schema", {}))
    if media_type and "json" in media_type:
        raw = example if isinstance(example, str) else json.dumps(example, indent=2)
        lang = "json"
    else:
        raw = example if isinstance(example, str) else str(example)
        lang = "text"
    return (
        {"mode": "raw", "raw": raw, "options": {"raw": {"language": lang}}},
        [{"key": "Content-Type", "value": media_type, "type": "text"}],
    )


def build_events(path: str, method: str) -> list[dict]:
    if path == "/api/authenticate" and method.lower() == "post":
        return [{"listen": "test", "script": {"type": "text/javascript", "exec": TEST_SCRIPT_AUTH}}]
    return [{"listen": "test", "script": {"type": "text/javascript", "exec": TEST_SCRIPT_2XX}}]


def build_request(path: str, method: str, operation: dict, schemas: dict, collection_vars: dict) -> dict:
    raw_path = normalize_path_for_postman(path, collection_vars)
    raw_url = "{{baseUrl}}" + raw_path
    qparams = query_params(schemas, operation)
    if qparams:
        enabled = [p for p in qparams if not p.get("disabled")]
        if enabled:
            raw_url += "?" + "&".join(f"{p['key']}={p['value']}" for p in enabled)
    body, body_headers = request_body(schemas, operation)
    request = {
        "method": method.upper(),
        "header": [{"key": "Accept", "value": "application/json", "type": "text"}] + body_headers,
        "url": {"raw": raw_url, "host": ["{{baseUrl}}"], "path": postman_path_parts(raw_path)},
        "description": operation.get("description") or operation.get("summary") or "",
    }
    if qparams:
        request["url"]["query"] = qparams
    if body:
        request["body"] = body
    if is_public_endpoint(path, method):
        request["auth"] = {"type": "noauth"}
    return request


def folder_sort_key(tag: str) -> tuple:
    order = {
        "authenticate-controller": 0,
        "account-resource": 1,
        "account-order-resource": 2,
        "storefront-resource": 3,
    }
    return (order.get(tag, 50), title_from_tag(tag))


def generate(spec: dict) -> tuple[dict, dict]:
    schemas = spec.get("components", {}).get("schemas", {})
    server_url = spec.get("servers", [{}])[0].get("url", "https://uat.minisnmore.com")
    collection_vars = {
        "baseUrl": server_url,
        "authToken": "",
        "username": "{{email}}",
        "password": "",
        "email": "admin@localhost",
        "id": "1",
        "login": "admin",
        "key": "",
        "activationKey": "",
        "resetKey": "",
        "newPassword": "admin",
    }

    folders: dict[str, dict] = {}
    for path, path_item in sorted(spec.get("paths", {}).items()):
        if not isinstance(path_item, dict):
            continue
        for method, operation in sorted(path_item.items()):
            if method.lower() not in HTTP_METHODS:
                continue
            tag = (operation.get("tags") or ["general"])[0]
            folder = folders.setdefault(tag, {"name": title_from_tag(tag), "item": []})
            name = operation.get("summary") or operation.get("operationId") or f"{method.upper()} {path}"
            folders[tag]["item"].append(
                {
                    "name": name,
                    "request": build_request(path, method, operation, schemas, collection_vars),
                    "event": build_events(path, method),
                }
            )

    setup_folder = {
        "name": "00 Setup (run first)",
        "description": "Authenticate to set authToken for protected modules.",
        "item": folders.get("authenticate-controller", {}).get("item", []),
    }

    module_items = [setup_folder]
    for tag in sorted(folders, key=folder_sort_key):
        if tag == "authenticate-controller":
            continue
        module_items.append(folders[tag])

    collection = {
        "info": {
            "name": "Minis n More API — Module wise (2xx tests)",
            "description": (
                "Generated from OpenAPI. Each request asserts HTTP 2xx.\n\n"
                "1. Set environment: email, password, baseUrl.\n"
                "2. Run folder **00 Setup** or POST /api/authenticate.\n"
                "3. Run other modules (Collection Runner).\n\n"
                "Note: POST/PUT/DELETE may need valid ids/data; public GET storefront "
                "endpoints work without auth."
            ),
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "auth": {
            "type": "bearer",
            "bearer": [{"key": "token", "value": "{{authToken}}", "type": "string"}],
        },
        "event": [
            {
                "listen": "prerequest",
                "script": {
                    "type": "text/javascript",
                    "exec": [
                        "// Use collection authToken if environment authToken is empty",
                        "const t = pm.environment.get('authToken') || pm.collectionVariables.get('authToken');",
                        "if (t) pm.collectionVariables.set('authToken', t);",
                    ],
                },
            }
        ],
        "variable": [{"key": k, "value": v} for k, v in sorted(collection_vars.items())],
        "item": module_items,
    }

    environment = {
        "id": str(uuid.uuid4()),
        "name": "Minis n More — API testing (UAT)",
        "values": [
            {
                "key": k,
                "value": v,
                "type": "secret" if k in {"authToken", "password", "newPassword"} else "default",
                "enabled": True,
            }
            for k, v in sorted(collection_vars.items())
        ],
        "_postman_variable_scope": "environment",
        "_postman_exported_using": "generate-module-wise-collection.py",
    }

    return collection, environment


def load_spec(source: str) -> dict:
    if source.startswith("http://") or source.startswith("https://"):
        with urlopen(source, timeout=30) as resp:
            return json.loads(resp.read().decode())
    return json.loads(Path(source).read_text())


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate module-wise Postman collection with 2xx tests")
    parser.add_argument(
        "--spec",
        default="https://uat.minisnmore.com/v3/api-docs",
        help="OpenAPI JSON URL or file path",
    )
    parser.add_argument(
        "--out-dir",
        default=Path(__file__).resolve().parent,
        help="Output directory",
    )
    args = parser.parse_args()
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    spec = load_spec(args.spec)
    collection, environment = generate(spec)

    collection_path = out_dir / "postman-collection.module-wise-2xx.json"
    env_path = out_dir / "postman-environment.api-testing.json"
    spec_path = out_dir / "openapi.snapshot.json"

    collection_path.write_text(json.dumps(collection, indent=2) + "\n", encoding="utf-8")
    env_path.write_text(json.dumps(environment, indent=2) + "\n", encoding="utf-8")
    spec_path.write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")

    folder_count = len(collection["item"])
    request_count = sum(len(f.get("item", [])) for f in collection["item"])
    print(f"Wrote {collection_path} ({folder_count} folders, {request_count} requests)")
    print(f"Wrote {env_path}")
    print(f"Wrote {spec_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
