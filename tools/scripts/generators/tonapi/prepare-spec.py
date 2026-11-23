"""Adapt a temporary OpenAPI document for the Kotlin templates, without editing its source."""

from copy import deepcopy
from pathlib import Path
import json
import re
import sys

try:
    import yaml
except ModuleNotFoundError:
    raise SystemExit(f"Install generator dependencies: python3 -m pip install -r {Path(__file__).with_name('requirements.txt')}")


class SpecLoader(yaml.SafeLoader):
    """Resolves plain scalars by YAML 1.2, as OpenAPI requires and the iOS generator reads them.

    PyYAML and the Kotlin generator's parser follow YAML 1.1, where `on`, `yes`, `0755` or `1:30`
    are not strings. The generator reads the adapted spec as JSON, which leaves it nothing to resolve.
    """


SpecLoader.yaml_implicit_resolvers = {}
for tag, pattern, first in (
    ("null", r"~|null|Null|NULL|", ["~", "n", "N", ""]),
    ("bool", r"true|True|TRUE|false|False|FALSE", "tTfF"),
    ("int", r"-?(?:0|[1-9][0-9]*)", "-0123456789"),
    ("float", r"-?(?:0|[1-9][0-9]*)(?:\.[0-9]*)?(?:[eE][-+]?[0-9]+)?", "-0123456789"),
    ("merge", r"<<", "<"),
):
    SpecLoader.add_implicit_resolver(f"tag:yaml.org,2002:{tag}", re.compile(f"^(?:{pattern})$"), list(first))


def load(text):
    return yaml.load(text, SpecLoader)

METHODS = {"get", "put", "post", "delete", "options", "head", "patch", "trace"}
CREDENTIALS = {"Authorization", "X-Wallet-Authorization", "X-TonConnect-Auth"}
HEADER_WALLET_AUTH = {"batteryapi", "earnapi"}
# Clients whose callers use enum values as identifiers (provider, category ids): a value an older app
# does not know stays itself instead of collapsing into `unknown`.
STRING_ENUMS = {"tonendpointapi"}
# Required properties the client ignores or reads leniently: a response that lacks one still decodes.
OPTIONAL = {"kandelabrapi": {
    # Hermes shares these candle models and sends partial OHLCV updates.
    "Candle": {"o", "h", "l", "c", "v"},
}, "tonendpointapi": {
    "Currency": {"code", "name", "image", "type", "symbol"},
    "StoryInfo": {"preview", "main_screen", "is_auto_show"},
    "StoryPage": {"include_wallets"},
    "StoryPageLink": {"text", "url"},
}}
# Synthetic schemas, never generated: --schema-mappings replaces each reference with the type.
# A closed object reads as a model, so a property referencing it carries no @Contextual.
SYNTHETIC = {
    "JsonAny": ("io.JsonAny", {}),
    "RawJson": ("kotlinx.serialization.json.JsonElement", {"type": "object", "additionalProperties": False}),
}


def filter_operations(document, selection, resolve):
    allowed = set(selection["operations"])
    paths = document.get("paths", {})
    for path, item in list(paths.items()):
        if "$ref" in item:
            item = paths[path] = deepcopy(resolve(item))
        for method in list(item):
            if method in METHODS and item[method].get("operationId") not in allowed:
                del item[method]
        if not METHODS.intersection(item):
            del paths[path]

    components = document.get("components", {})
    retained = {"securitySchemes": components["securitySchemes"]} if "securitySchemes" in components else {}
    pending = [value for key, value in document.items() if key != "components"]
    pending += list(retained.values())
    pending += [{"$ref": f"#/components/schemas/{name.replace('~', '~0').replace('/', '~1')}"} for name in selection.get("schemas", [])]
    seen = set()
    while pending:
        value = pending.pop()
        references = []
        if isinstance(value, dict):
            pending.extend(value.values())
            references.append(value.get("$ref"))
            discriminator = value.get("discriminator")
            mapping = discriminator.get("mapping", {}) if isinstance(discriminator, dict) else {}
            if isinstance(mapping, dict):
                for name in mapping.values():
                    if isinstance(name, str) and name in components.get("schemas", {}):
                        name = f"#/components/schemas/{name.replace('~', '~0').replace('/', '~1')}"
                    references.append(name)
        elif isinstance(value, list):
            pending.extend(value)
        for reference in references:
            if not isinstance(reference, str) or not reference.startswith("#/components/"):
                continue
            _, _, group, name, *_ = reference.split("/")
            name = name.replace("~1", "/").replace("~0", "~")
            if (group, name) not in seen:
                seen.add((group, name))
                component = components[group][name]
                retained.setdefault(group, {})[name] = component
                pending.append(component)
    # Retain source order: it affects Kotlin inline model naming and discriminator parents.
    document["components"] = {group: {name: value for name, value in values.items() if name in retained.get(group, {})}
                              for group, values in components.items() if group in retained}


def prepare(document, package, open_enums=False, operation_filter=None):
    document = deepcopy(document)

    def resolve(value):
        seen = set()
        while "$ref" in value:
            reference = value["$ref"]
            if not reference.startswith("#/") or reference in seen:
                raise ValueError(f"Expected a local OpenAPI reference: {reference}")
            seen.add(reference)
            value = document
            for part in reference[2:].split("/"):
                value = value[part.replace("~1", "/").replace("~0", "~")]
        return value

    if operation_filter is not None:
        filter_operations(document, operation_filter, resolve)
    schemas = document.setdefault("components", {}).setdefault("schemas", {})
    if taken := SYNTHETIC.keys() & schemas.keys():
        raise ValueError(f"The spec defines reserved schemas: {', '.join(sorted(taken))}")
    for name, optional in OPTIONAL.get(package, {}).items():
        if (schema := schemas.get(name)) is None:
            continue
        if missing := optional - schema.get("properties", {}).keys():
            raise ValueError(f"{name} has no properties {', '.join(sorted(missing))}")
        schema["required"] = [key for key in schema.get("required", []) if key not in optional]

    for path, item in document.get("paths", {}).items():
        for method, operation in item.items():
            if method not in METHODS:
                continue
            parameters = {}
            for parameter in item.get("parameters", []) + operation.get("parameters", []):
                parameter = deepcopy(resolve(parameter))
                parameters[(parameter.get("in"), parameter.get("name"))] = parameter
            parameters = list(parameters.values())
            if any(p.get("in") == "path" and p.get("name") == "wallet_id" for p in parameters):
                operation["x-wallet-path-parameter"] = True
            schemes = document.get("components", {}).get("securitySchemes", {})
            wallet_in_security = any(
                schemes.get(name, {}).get("x-wallet-id")
                for requirement in operation.get("security", document.get("security", []))
                for name in requirement
            )
            wallet_in_headers = any(p.get("in") == "header" and p.get("name") == "X-Wallet-ID" for p in parameters)
            if path == "/v2/pubkeys/wallets/_bulk" and method == "post" and not any(
                p.get("in") == "header" and p.get("name") == "F" for p in parameters
            ):
                parameters.append({"in": "header", "name": "F", "schema": {"type": "string"}, "required": False})
            for parameter in parameters:
                location, name = parameter.get("in"), parameter.get("name")
                if location == "path" and name == "wallet_id":
                    parameter["x-wallet-path"] = True
                # Battery and Earn authenticate their own wallet through headers; enterprise path IDs address another wallet.
                wallet_parameter = (location, name) == ("header", "X-Wallet-ID") or (
                    (location, name) == ("path", "wallet_id") and not wallet_in_headers and package != "batteryapi"
                )
                if not wallet_in_security and wallet_parameter:
                    parameter["x-wallet-id"] = True
                if package in HEADER_WALLET_AUTH and location == "header":
                    if name == "X-Wallet-ID" and not wallet_in_security:
                        operation["x-wallet-authentication"] = True
                    if name in CREDENTIALS:
                        parameter["x-credential-parameter"] = True
                if location == "header" and name == "X-Wallet-ID" and any(
                    p.get("in") == "path" and p.get("name") == "wallet_id" for p in parameters
                ):
                    parameter["x-default-wallet-id"] = True
            if parameters:
                operation["parameters"] = parameters
        # Path parameters have been expanded per operation, including referenced parameters.
        item.pop("parameters", None)

    for name, schema in schemas.items():
        if "oneOf" not in schema or "discriminator" not in schema:
            continue
        discriminator = schema["discriminator"]["propertyName"]
        schema["x-one-of-sealed"] = True
        for variant in schema["oneOf"]:
            child = resolve(variant)
            parents = [p for p in child.get("x-one-of-parents", "").split(", ") if p]
            if name not in parents:
                parents.append(name)
            child["x-one-of-parents"] = ", ".join(parents)
            child.get("properties", {}).get(discriminator, {}).pop("enum", None)

    used = set()

    def is_plain_object(schema):
        return (schema.get("type") == "object" or "properties" in schema) and not schema.keys() & {"allOf", "oneOf", "anyOf"}

    def shape(value):
        if isinstance(value, dict):
            return {k: shape(v) for k, v in value.items() if k not in ("description", "example", "examples")}
        if isinstance(value, list):
            return [shape(v) for v in value]
        return value

    def adapt(value):
        if isinstance(value, dict):
            if "oneOf" in value and "discriminator" not in value:
                value["x-one-of-union"] = True
                variants = [resolve(variant) for variant in value["oneOf"]]
                if all(is_plain_object(variant) for variant in variants):
                    # One model with every variant's properties; a property declared differently is raw JSON.
                    # The templates render every property optional; `required` keeps the generator's order.
                    properties, shapes, required = {}, {}, list(value.get("required", []))
                    for variant in variants:
                        for key, schema in variant.get("properties", {}).items():
                            properties[key] = deepcopy(schema)
                            shapes.setdefault(key, []).append(shape(schema))
                        required += [key for key in variant.get("required", []) if key not in required]
                    for key, declared in shapes.items():
                        if any(s != declared[0] for s in declared):
                            properties[key] = {"$ref": "#/components/schemas/RawJson"}
                            used.add("RawJson")
                    del value["oneOf"]
                    value.update(type="object", properties=properties)
                    if required:
                        value["required"] = required
            if value.get("additionalProperties") in (True, {}):
                value["additionalProperties"] = {"$ref": "#/components/schemas/JsonAny"}
                used.add("JsonAny")
            enum = value.get("enum")
            strings = isinstance(enum, list) and (
                value.get("type") == "string" or "type" not in value and all(isinstance(v, str) for v in enum)
            )
            if strings and package in STRING_ENUMS:
                del value["enum"]
                value.pop("x-enum-varnames", None)
                value["type"] = "string"
            elif strings and open_enums and "unknown" not in enum:
                enum.append("unknown")
                if "x-enum-varnames" in value:
                    value["x-enum-varnames"].append("unknown")
            for key, child in value.items():
                if key not in ("example", "examples"):
                    adapt(child)
        elif isinstance(value, list):
            for child in value:
                adapt(child)

    adapt(document)
    for name in sorted(used):
        schemas[name] = SYNTHETIC[name][1]
    mappings = [f"{name}=kotlinx.serialization.json.JsonObject" for name, schema in schemas.items()
                if schema.get("x-operation") or schema.get("x-operation-result")]
    mappings += [f"{name}={SYNTHETIC[name][0]}" for name in sorted(used)]
    return document, ",".join(mappings)


if __name__ == "__main__":
    source, destination, package, open_enums, *selection = sys.argv[1:]
    operation_filter = load(Path(selection[0]).read_text())["filter"] if selection and selection[0] else None
    document, mappings = prepare(load(Path(source).read_text()), package, open_enums == "true", operation_filter)
    Path(destination).write_text(json.dumps(document, ensure_ascii=False, allow_nan=False))
    print(mappings)
