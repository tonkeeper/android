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
import yaml


PACKAGE = "com.tonapps.bus.generated"

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


def snake_to_camel(name):
    """Convert snake_case to camelCase."""
    parts = name.split("_")
    return parts[0] + "".join(p[0].upper() + p[1:] for p in parts[1:] if p)


def snake_to_pascal(name):
    """Convert snake_case to PascalCase."""
    parts = name.split("_")
    return "".join(p[0].upper() + p[1:] for p in parts if p)


def value_to_enum_entry(value):
    """Convert an enum string value to a PascalCase Kotlin enum entry name."""
    normalized = value.replace("-", "_").replace(" ", "_").replace(":", "_")
    parts = normalized.split("_")
    result = ""
    for p in parts:
        if not p:
            continue
        result += p[0].upper() + p[1:]
    return result


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


def parse_schema(schema):
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
        kotlin_type = yaml_type_to_kotlin(prop)
        nullable = is_nullable(prop_name, prop, required_list)

        enum_values = None
        if prop.get("type") == "string" and "enum" in prop:
            enum_values = prop["enum"]

        params.append({
            "name": snake_to_camel(prop_name),
            "original_name": prop_name,
            "type": kotlin_type,
            "nullable": nullable,
            "enum_values": enum_values,
            "enum_class_name": None,
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


def format_method_signature(event, indent):
    lines = []
    params = event["params"]
    method_name = event["method_name"]

    if len(params) == 0:
        lines.append(f"{indent}fun {method_name}()")
    elif len(params) <= 3:
        param_strs = [f"{p['name']}: {get_param_type_str(p)}" for p in params]
        sig = f"{indent}fun {method_name}({', '.join(param_strs)})"
        if len(sig) <= 100:
            lines.append(sig)
        else:
            lines.append(f"{indent}fun {method_name}(")
            for j, p in enumerate(params):
                comma = "," if j < len(params) - 1 else ""
                lines.append(f"{indent}    {p['name']}: {get_param_type_str(p)}{comma}")
            lines.append(f"{indent})")
    else:
        lines.append(f"{indent}fun {method_name}(")
        for j, p in enumerate(params):
            comma = "," if j < len(params) - 1 else ""
            lines.append(f"{indent}    {p['name']}: {get_param_type_str(p)}{comma}")
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
            for vi, val in enumerate(enum_values):
                entry_name = value_to_enum_entry(val)
                comma = "," if vi < len(enum_values) - 1 else ""
                lines.append(f'            {entry_name}("{val}"){comma}')
            lines.append("        }")

        # Methods
        for event in group["events"]:
            lines.append("")
            lines.append(f"        /** {event['event_name']} */")
            lines.extend(format_method_signature(event, "        "))

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

    required_params = [p for p in params if not p["nullable"]]
    nullable_params = [p for p in params if p["nullable"]]

    # Signature
    if event["description"]:
        lines.append(f"{indent}/**")
        lines.append(f"{indent} * {event_name}")
        lines.append(f"{indent} *")
        lines.append(f"{indent} * {event['description']}")
        lines.append(f"{indent} */")
    else:
        lines.append(f"{indent}/** {event_name} */")
    lines.append(f"{indent}@UiThread")
    sig_lines = format_method_signature(event, indent)
    for i, sl in enumerate(sig_lines):
        sig_lines[i] = sl.replace("fun ", "override fun ", 1)
    sig_lines[-1] += " {"
    lines.extend(sig_lines)

    # Body
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


def generate_implementation(groups):
    lines = []
    lines.append(f"package {PACKAGE}")
    lines.append("")
    lines.append("import androidx.annotation.UiThread")
    lines.append("import com.tonapps.bus.core.contract.EventExecutor")
    # Import enum types from inner interfaces
    for group in groups:
        if group["enum_definitions"]:
            lines.append(f"import {PACKAGE}.Events.{group['interface_name']}.*")
    lines.append("")
    lines.append("/**")
    lines.append(" * Auto-generated from OpenAPI analytics schemas.")
    lines.append(" * Do not edit manually — re-run the generator instead.")
    lines.append(" */")
    lines.append("class DefaultEvents(")
    lines.append("    private val eventExecutor: EventExecutor,")
    lines.append(") {")
    lines.append("")

    # Public fields
    for group in groups:
        impl_class = group["interface_name"] + "Impl"
        lines.append(f"    val {group['field_name']} = {impl_class}(eventExecutor)")
    lines.append("")

    # Sub-implementation classes
    for gi, group in enumerate(groups):
        if gi > 0:
            lines.append("")

        impl_class = group["interface_name"] + "Impl"
        iface = f"Events.{group['interface_name']}"
        lines.append(f"    class {impl_class}(")
        lines.append(f"        private val eventExecutor: EventExecutor,")
        lines.append(f"    ) : {iface} {{")
        lines.append("")
        lines.append(f"        private fun trackEvent(name: String, params: Map<String, Any>) {{")
        lines.append(f"            eventExecutor.trackEvent(name, params)")
        lines.append(f"        }}")

        for event in group["events"]:
            lines.append("")
            lines.extend(generate_impl_method(event, "        "))

        lines.append("    }")

    lines.append("}")
    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Loading
# ---------------------------------------------------------------------------

def read_version(openapi_dir):
    """Read schema version from analytics.yaml."""
    analytics_path = os.path.join(openapi_dir, "analytics.yaml")
    if not os.path.exists(analytics_path):
        return None
    with open(analytics_path, "r") as f:
        doc = yaml.safe_load(f)
    return doc.get("info", {}).get("version")


def load_schemas_grouped(openapi_dir):
    groups = []
    event_files = discover_event_files(openapi_dir)

    for filename, interface_name, enum_prefix, field_name in event_files:
        filepath = os.path.join(openapi_dir, filename)

        with open(filepath, "r") as f:
            doc = yaml.safe_load(f)

        schemas = doc.get("components", {}).get("schemas", {})
        events = []
        for schema_name, schema in schemas.items():
            event = parse_schema(schema)
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

    interface_code = generate_interface(groups, version)
    impl_code = generate_implementation(groups)

    os.makedirs(output_dir, exist_ok=True)

    interface_path = os.path.join(output_dir, "Events.kt")
    with open(interface_path, "w") as f:
        f.write(interface_code)
    print(f"Generated {interface_path}", file=sys.stderr)

    impl_path = os.path.join(output_dir, "DefaultEvents.kt")
    with open(impl_path, "w") as f:
        f.write(impl_code)
    print(f"Generated {impl_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
