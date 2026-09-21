#!/usr/bin/env python3
"""
Generates Events.kt and DefaultEvents.kt from OpenAPI analytics YAML schemas.

Usage:
    python3 generate_event_delegate.py <openapi_dir> <output_dir>

Architecture:
  - Events.kt: container interface with inner interfaces per YAML file + enum classes + VERSION constant
  - DefaultEvents.kt: class with public sub-implementation fields per group
    Usage: events.dappBrowser.dappBrowserOpen(...)

Files are auto-discovered from the openapi directory. Names are derived from filenames.
Files starting with '_' and 'analytics.yaml' are skipped.
"""

import sys
import os
import re
import yaml


PACKAGE = "com.tonapps.bus.generated"
FLOWS_PACKAGE = PACKAGE + ".flows"
FLOWS_DIR = "flows"

# Aptabase rejects the whole event if any property key is longer than this.
MAX_PROP_KEY_LENGTH = 40

# Files to skip (meta-schemas, aggregators)
SKIP_FILES = {"analytics.yaml"}


def filename_to_names(filename):
    """Derive (interface_name, enum_prefix, field_name) from a YAML filename.

    install-app.yaml    -> InstallApp,    installApp
    dapp-browser.yaml   -> DappBrowser,   dappBrowser
    TransactionSent.yaml-> TransactionSent,transactionSent
    """
    base = filename.rsplit(".", 1)[0]       # remove .yaml
    parts = base.split("-")                 # split on hyphens
    pascal = "".join(p[0].upper() + p[1:] for p in parts)
    camel = parts[0][0].lower() + parts[0][1:] + "".join(p[0].upper() + p[1:] for p in parts[1:])
    return pascal, pascal, camel


def discover_event_files(openapi_dir):
    """Auto-discover all event YAML files in the directory."""
    files = []
    for f in sorted(os.listdir(openapi_dir)):
        if not f.endswith(".yaml"):
            continue
        if f.startswith("_"):
            continue
        if f in SKIP_FILES:
            continue
        interface_name, enum_prefix, field_name = filename_to_names(f)
        files.append((f, interface_name, enum_prefix, field_name))
    return files


def split_words(name):
    """Split an identifier-ish string on any non-alphanumeric separator."""
    return [p for p in re.split(r"[^A-Za-z0-9]+", name) if p]


def snake_to_camel(name):
    """Convert snake_case to camelCase."""
    parts = split_words(name)
    if not parts:
        return name
    return parts[0][0].lower() + parts[0][1:] + "".join(p[0].upper() + p[1:] for p in parts[1:])


def snake_to_pascal(name):
    """Convert snake_case to PascalCase."""
    parts = split_words(name)
    return "".join(p[0].upper() + p[1:] for p in parts)


def value_to_enum_entry(value):
    """Convert an enum string value to a PascalCase Kotlin enum entry name.

    Any character that is not a letter or a digit acts as a word separator, so
    values like "ton/mainnet/coin" or "usd.e" become valid identifiers.
    """
    parts = [p for p in re.split(r"[^A-Za-z0-9]+", value) if p]
    result = "".join(p[0].upper() + p[1:] for p in parts)
    if not result:
        return "Unknown"
    if result[0].isdigit():
        return "_" + result
    return result


def value_to_enum_entries(values):
    """Map enum values to unique entry names, preserving order.

    Values that only differ by separators ("a-b" vs "a/b") would collapse onto
    the same identifier, so collisions get a numeric suffix.
    """
    counts = {}
    entries = []
    for value in values:
        name = value_to_enum_entry(value)
        counts[name] = counts.get(name, 0) + 1
        if counts[name] > 1:
            name = f"{name}{counts[name]}"
        entries.append(name)
    return entries


def yaml_type_to_kotlin(prop):
    t = prop.get("type", "string")
    if t == "string":
        return "String"
    elif t == "integer":
        return "Int"
    elif t == "number":
        return "Double"
    elif t == "boolean":
        return "Boolean"
    else:
        return "String"


def is_nullable(prop_name, prop, required_list):
    if prop.get("nullable", False):
        return True
    if prop_name not in required_list:
        return True
    return False


def extract_event_name(schema):
    event_name_prop = schema.get("properties", {}).get("eventName", {})
    if "default" in event_name_prop:
        return event_name_prop["default"]
    if "enum" in event_name_prop:
        return event_name_prop["enum"][0]
    return None


def resolve_local_ref(prop, schemas, global_schemas=None):
    ref = prop.get("$ref")
    if not ref:
        return prop
    name = ref.rsplit("/", 1)[-1]
    for candidates in (schemas, global_schemas or {}):
        if name in candidates:
            return candidates[name]
        if (name + "Schema") in candidates:
            return candidates[name + "Schema"]
    return prop


def parse_schema(schema, schemas=None, global_schemas=None):
    schemas = schemas or {}
    event_name = extract_event_name(schema)
    if not event_name:
        return None

    required_list = schema.get("required", [])
    properties = schema.get("properties", {})
    description = schema.get("description", "")

    params = []
    for prop_name, prop in properties.items():
        if prop_name == "eventName":
            continue
        resolved = resolve_local_ref(prop, schemas, global_schemas)
        kotlin_type = yaml_type_to_kotlin(resolved)
        nullable = is_nullable(prop_name, prop, required_list)

        enum_values = None
        if resolved.get("type") == "string" and "enum" in resolved:
            enum_values = resolved["enum"]

        params.append({
            "name": snake_to_camel(prop_name),
            "original_name": prop_name,
            "type": kotlin_type,
            "nullable": nullable,
            "enum_values": enum_values,
            "enum_class_name": None,
        })

    # launch_app carries client-side feature flags that are not part of the schema.
    if event_name == "launch_app":
        params.append({
            "name": "featureFlags",
            "original_name": None,
            "type": "Map<String, Any>",
            "nullable": False,
            "enum_values": None,
            "enum_class_name": None,
            "custom_props": True,
            "default": "emptyMap()",
        })

    return {
        "event_name": event_name,
        "method_name": snake_to_camel(event_name),
        "description": description,
        "params": params,
    }


def resolve_enums(group, enum_prefix):
    """Resolve enum class names for a group. Returns list of (class_name, ordered_values)."""
    # Collect: prop_name -> {frozenset(values): [event_names]}
    prop_enum_map = {}
    for event in group["events"]:
        for param in event["params"]:
            if param["enum_values"]:
                pn = param["original_name"]
                vals = frozenset(param["enum_values"])
                prop_enum_map.setdefault(pn, {}).setdefault(vals, []).append(event["event_name"])

    # Determine class names
    enum_registry = {}  # (prop_name, frozenset(values)) -> class_name
    for prop_name, val_groups in prop_enum_map.items():
        prop_pascal = snake_to_pascal(prop_name)
        if len(val_groups) == 1:
            vals = list(val_groups.keys())[0]
            enum_registry[(prop_name, vals)] = enum_prefix + prop_pascal
        else:
            for vals, event_names in val_groups.items():
                event_pascal = snake_to_pascal(event_names[0])
                enum_registry[(prop_name, vals)] = event_pascal + prop_pascal

    # Assign to params and collect definitions
    enum_definitions = []
    seen = set()
    for event in group["events"]:
        for param in event["params"]:
            if param["enum_values"]:
                vals = frozenset(param["enum_values"])
                class_name = enum_registry[(param["original_name"], vals)]
                param["enum_class_name"] = class_name
                if class_name not in seen:
                    seen.add(class_name)
                    enum_definitions.append((class_name, param["enum_values"]))

    return enum_definitions


def get_param_type_str(param):
    if param["enum_class_name"]:
        base = param["enum_class_name"]
    else:
        base = param["type"]
    return base + "?" if param["nullable"] else base


def get_param_decl_str(param, with_defaults):
    decl = f"{param['name']}: {get_param_type_str(param)}"
    default = param.get("default")
    if with_defaults and default:
        decl += f" = {default}"
    return decl


def format_method_signature(event, indent, with_defaults=False):
    lines = []
    params = event["params"]
    method_name = event["method_name"]

    if len(params) == 0:
        lines.append(f"{indent}fun {method_name}()")
    elif len(params) <= 3:
        param_strs = [get_param_decl_str(p, with_defaults) for p in params]
        sig = f"{indent}fun {method_name}({', '.join(param_strs)})"
        if len(sig) <= 100:
            lines.append(sig)
        else:
            lines.append(f"{indent}fun {method_name}(")
            for j, p in enumerate(params):
                comma = "," if j < len(params) - 1 else ""
                lines.append(f"{indent}    {get_param_decl_str(p, with_defaults)}{comma}")
            lines.append(f"{indent})")
    else:
        lines.append(f"{indent}fun {method_name}(")
        for j, p in enumerate(params):
            comma = "," if j < len(params) - 1 else ""
            lines.append(f"{indent}    {get_param_decl_str(p, with_defaults)}{comma}")
        lines.append(f"{indent})")

    return lines


# ---------------------------------------------------------------------------
# Interface generation
# ---------------------------------------------------------------------------

def generate_interface(groups, version):
    lines = []
    lines.append(f"package {PACKAGE}")
    lines.append("")
    lines.append("/**")
    lines.append(" * Auto-generated from OpenAPI analytics schemas.")
    lines.append(" * Do not edit manually — re-run the generator instead.")
    lines.append(" */")
    lines.append("interface Events {")
    lines.append("")
    lines.append("    companion object {")
    lines.append(f'        const val VERSION = "{version}"')
    lines.append("    }")

    for gi, group in enumerate(groups):
        if gi > 0:
            lines.append("")

        lines.append("")
        lines.append(f"    interface {group['interface_name']} {{")

        # Enum classes
        for enum_name, enum_values in group["enum_definitions"]:
            lines.append("")
            lines.append(f"        enum class {enum_name}(val key: String) {{")
            entry_names = value_to_enum_entries(enum_values)
            for vi, val in enumerate(enum_values):
                entry_name = entry_names[vi]
                comma = "," if vi < len(enum_values) - 1 else ""
                lines.append(f'            {entry_name}("{val}"){comma}')
            lines.append("        }")

        # Methods
        for event in group["events"]:
            lines.append("")
            lines.append(f"        /** {event['event_name']} */")
            lines.extend(format_method_signature(event, "        ", with_defaults=True))

        lines.append("    }")

    lines.append("}")
    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Implementation generation
# ---------------------------------------------------------------------------

def param_value_expr(param):
    """Expression to extract the value for the hashMap."""
    if param["enum_class_name"]:
        return f"{param['name']}.key"
    return param["name"]


def generate_impl_method(event, indent):
    lines = []
    params = event["params"]
    event_name = event["event_name"]

    custom_params = [p for p in params if p.get("custom_props")]
    schema_params = [p for p in params if not p.get("custom_props")]
    required_params = [p for p in schema_params if not p["nullable"]]
    nullable_params = [p for p in schema_params if p["nullable"]]

    # Signature
    if event["description"]:
        lines.append(f"{indent}/**")
        lines.append(f"{indent} * {event_name}")
        lines.append(f"{indent} *")
        lines.append(f"{indent} * {event['description']}")
        lines.append(f"{indent} */")
    else:
        lines.append(f"{indent}/** {event_name} */")
    lines.append(f"{indent}@AnyThread")
    sig_lines = format_method_signature(event, indent)
    for i, sl in enumerate(sig_lines):
        sig_lines[i] = sl.replace("fun ", "override fun ", 1)
    sig_lines[-1] += " {"
    lines.extend(sig_lines)

    # Body
    if custom_params:
        lines.append(f'{indent}    val props = mutableMapOf<String, Any>()')
        for p in custom_params:
            lines.append(f'{indent}    {p["name"]}.forEach {{ (key, value) -> props[key.take({MAX_PROP_KEY_LENGTH})] = value }}')
        for p in required_params:
            lines.append(f'{indent}    props["{p["original_name"]}"] = {param_value_expr(p)}')
        for p in nullable_params:
            val = "it.key" if p["enum_class_name"] else "it"
            lines.append(f'{indent}    {p["name"]}?.let {{ props["{p["original_name"]}"] = {val} }}')
        lines.append(f'{indent}    trackEvent("{event_name}", props)')
        lines.append(f"{indent}}}")
        return lines

    if len(params) == 0:
        lines.append(f'{indent}    trackEvent("{event_name}", emptyMap())')
    elif len(nullable_params) == 0:
        if len(required_params) <= 2:
            entries = ", ".join(
                f'"{p["original_name"]}" to {param_value_expr(p)}' for p in required_params
            )
            lines.append(f'{indent}    trackEvent("{event_name}", hashMapOf({entries}))')
        else:
            lines.append(f'{indent}    val props = hashMapOf(')
            for j, p in enumerate(required_params):
                comma = "," if j < len(required_params) - 1 else ""
                lines.append(f'{indent}        "{p["original_name"]}" to {param_value_expr(p)}{comma}')
            lines.append(f'{indent}    )')
            lines.append(f'{indent}    trackEvent("{event_name}", props)')
    elif len(required_params) == 0:
        lines.append(f'{indent}    val props = mutableMapOf<String, Any>()')
        for p in nullable_params:
            val = f"it.key" if p["enum_class_name"] else "it"
            lines.append(f'{indent}    {p["name"]}?.let {{ props["{p["original_name"]}"] = {val} }}')
        lines.append(f'{indent}    trackEvent("{event_name}", props)')
    else:
        if len(required_params) <= 2:
            entries = ", ".join(
                f'"{p["original_name"]}" to {param_value_expr(p)}' for p in required_params
            )
            lines.append(f'{indent}    val props = hashMapOf<String, Any>({entries})')
        else:
            lines.append(f'{indent}    val props = hashMapOf<String, Any>(')
            for j, p in enumerate(required_params):
                comma = "," if j < len(required_params) - 1 else ""
                lines.append(f'{indent}        "{p["original_name"]}" to {param_value_expr(p)}{comma}')
            lines.append(f'{indent}    )')
        for p in nullable_params:
            val = "it.key" if p["enum_class_name"] else "it"
            lines.append(f'{indent}    {p["name"]}?.let {{ props["{p["original_name"]}"] = {val} }}')
        lines.append(f'{indent}    trackEvent("{event_name}", props)')

    lines.append(f"{indent}}}")
    return lines


def impl_class_name(group):
    return group["interface_name"] + "Impl"


def generate_flow_implementation(group):
    """Generate one top-level `<Group>Impl` file for the flows package."""
    lines = []
    lines.append(f"package {FLOWS_PACKAGE}")
    lines.append("")
    lines.append("import androidx.annotation.AnyThread")
    lines.append("import com.tonapps.bus.core.contract.EventExecutor")
    lines.append(f"import {PACKAGE}.Events")
    enum_imports = [
        f"import {PACKAGE}.Events.{group['interface_name']}.{class_name}"
        for class_name, _ in group["enum_definitions"]
    ]
    lines.extend(sorted(enum_imports))
    lines.append("")
    lines.append("/**")
    lines.append(" * Auto-generated from OpenAPI analytics schemas.")
    lines.append(" * Do not edit manually — re-run the generator instead.")
    lines.append(" */")
    lines.append(f"class {impl_class_name(group)}(")
    lines.append("    private val eventExecutor: EventExecutor,")
    lines.append(f") : Events.{group['interface_name']} {{")
    lines.append("")
    lines.append("    private fun trackEvent(name: String, params: Map<String, Any>) {")
    lines.append("        eventExecutor.trackEvent(name, params)")
    lines.append("    }")

    for event in group["events"]:
        lines.append("")
        lines.extend(generate_impl_method(event, "    "))

    lines.append("}")
    lines.append("")
    return "\n".join(lines)


def generate_implementation(groups):
    """Generate DefaultEvents.kt — the aggregator wiring one field per flow."""
    lines = []
    lines.append(f"package {PACKAGE}")
    lines.append("")
    imports = ["import com.tonapps.bus.core.contract.EventExecutor"]
    imports += [f"import {FLOWS_PACKAGE}.{impl_class_name(g)}" for g in groups]
    lines.extend(sorted(imports))
    lines.append("")
    lines.append("/**")
    lines.append(" * Auto-generated from OpenAPI analytics schemas.")
    lines.append(" * Do not edit manually — re-run the generator instead.")
    lines.append(" */")
    lines.append("class DefaultEvents(")
    lines.append("    private val eventExecutor: EventExecutor,")
    lines.append(") {")
    lines.append("")

    for group in groups:
        lines.append(f"    val {group['field_name']} = {impl_class_name(group)}(eventExecutor)")

    lines.append("}")
    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Loading
# ---------------------------------------------------------------------------

def read_version(openapi_dir):
    """Read schema version from analytics.yaml."""
    analytics_path = os.path.join(openapi_dir, "_AnalyticsEventMobileNative.yaml")
    if not os.path.exists(analytics_path):
        return None
    with open(analytics_path, "r") as f:
        doc = yaml.safe_load(f)
    return doc.get("info", {}).get("version")


def load_schemas_grouped(openapi_dir):
    groups = []
    event_files = discover_event_files(openapi_dir)

    file_schemas = {}
    global_schemas = {}
    for filename, _, _, _ in event_files:
        with open(os.path.join(openapi_dir, filename), "r") as f:
            doc = yaml.safe_load(f)
        schemas = doc.get("components", {}).get("schemas", {})
        file_schemas[filename] = schemas
        for schema_name, schema in schemas.items():
            global_schemas.setdefault(schema_name, schema)

    for filename, interface_name, enum_prefix, field_name in event_files:
        schemas = file_schemas[filename]
        events = []
        for schema_name, schema in schemas.items():
            event = parse_schema(schema, schemas, global_schemas)
            if event:
                events.append(event)

        if events:
            group = {
                "filename": filename,
                "interface_name": interface_name,
                "enum_prefix": enum_prefix,
                "field_name": field_name,
                "events": events,
            }
            group["enum_definitions"] = resolve_enums(group, enum_prefix)
            groups.append(group)

    return groups


def main():
    if len(sys.argv) < 3:
        print(f"Usage: {sys.argv[0]} <openapi_dir> <output_dir>", file=sys.stderr)
        sys.exit(1)

    openapi_dir = sys.argv[1]
    output_dir = sys.argv[2]

    if not os.path.isdir(openapi_dir):
        print(f"Error: {openapi_dir} is not a directory", file=sys.stderr)
        sys.exit(1)

    version = read_version(openapi_dir)
    if not version:
        print("Error: could not read version from analytics.yaml", file=sys.stderr)
        sys.exit(1)

    groups = load_schemas_grouped(openapi_dir)
    total_events = sum(len(g["events"]) for g in groups)
    total_enums = sum(len(g["enum_definitions"]) for g in groups)
    print(f"Schema version {version}: {total_events} events, {total_enums} enums in {len(groups)} groups", file=sys.stderr)

    os.makedirs(output_dir, exist_ok=True)

    interface_path = os.path.join(output_dir, "Events.kt")
    with open(interface_path, "w") as f:
        f.write(generate_interface(groups, version))
    print(f"Generated {interface_path}", file=sys.stderr)

    impl_path = os.path.join(output_dir, "DefaultEvents.kt")
    with open(impl_path, "w") as f:
        f.write(generate_implementation(groups))
    print(f"Generated {impl_path}", file=sys.stderr)

    flows_dir = os.path.join(output_dir, FLOWS_DIR)
    os.makedirs(flows_dir, exist_ok=True)

    written = set()
    for group in groups:
        filename = impl_class_name(group) + ".kt"
        written.add(filename)
        with open(os.path.join(flows_dir, filename), "w") as f:
            f.write(generate_flow_implementation(group))
    print(f"Generated {len(written)} flows in {flows_dir}", file=sys.stderr)

    # Drop impls whose schema file no longer exists — they would not compile.
    for stale in sorted(set(os.listdir(flows_dir)) - written):
        if stale.endswith("Impl.kt"):
            os.remove(os.path.join(flows_dir, stale))
            print(f"Removed stale {stale}", file=sys.stderr)


if __name__ == "__main__":
    main()
