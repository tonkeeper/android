#!/usr/bin/env python3
"""Finds what dev has to translate and checks the translations.

The English `values/strings.xml` is the source. The context file keeps, per key, the English
text the context was written for (`source`), so a key whose English changed since is retranslated.
Keys sit under their platform ({"android": {"<key>": ...}}): iOS and Android name keys
differently, so the file can later hold both without one platform's keys clashing with the other's.

  plan      keys to (re)translate and to write context for, as JSON; with --regenerate-context
            --regenerated-since <ref>, keys whose context changed since that commit are done
  report    Markdown summary of missing translations, changed English and missing context
  validate  placeholders, <annotation> tags, apostrophes, plural forms and duplicate keys
  apply     writes the agent's context and translations (JSON) into the context file and locales

The agent never edits strings.xml itself: it hands `apply` a JSON file

  {"context": {"<key>": {"usages": [{"where": "...", "role": "..."}], "note": "...", "split": null}},
   "translations": {"values-ru": {"<key>": "text", "plurals:<key>": {"one": "...", "other": "..."}}}}

with texts written as in strings.xml: tags kept, & < > and apostrophes escaped.
"""

import argparse
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import escape

DEFAULT_RES = "android/apps/wallet/localization/src/main/res"
DEFAULT_CONTEXT = "shared/localization/translation-context.json"
PLATFORM = "android"

# One token at a time, so "%%1$s" reads as a literal percent and "1$s", not as a placeholder.
FORMAT_TOKEN = re.compile(r"%%|%(?:\d+\$)?[sdf]|%")
UNESCAPED_APOSTROPHE = re.compile(r"(?<!\\)'")


class Entry:
    def __init__(self, element):
        self.name = element.get("name")
        self.kind = element.tag
        # A string and a plural may share a name.
        self.key = f"plurals:{self.name}" if self.kind == "plurals" else self.name
        self.translatable = element.get("translatable") != "false"
        if self.kind == "plurals":
            self.forms = {item.get("quantity"): inner(item) for item in element.findall("item")}
        else:
            self.forms = {"": inner(element)}

    @property
    def text(self):
        if self.kind == "plurals":
            return {quantity: self.forms[quantity] for quantity in sorted(self.forms)}
        return self.forms[""]


def inner(element):
    """The element's content as written, with its tags: what a translator has to keep."""
    parts = [escape(element.text or "")]
    for child in element:
        parts.append(ET.tostring(child, encoding="unicode"))
    return "".join(parts).strip()


def load(path):
    entries = {}
    for element in ET.parse(path).getroot():
        if element.tag in ("string", "plurals") and element.get("name"):
            entry = Entry(element)
            entries[entry.key] = entry
    return entries


def duplicates(path):
    # Two branches that translate the same key each append it: git merges both without a conflict.
    keys = [Entry(element).key for element in ET.parse(path).getroot()
            if element.tag in ("string", "plurals") and element.get("name")]
    return sorted({key for key in keys if keys.count(key) > 1})


LOCALE_DIRECTORY = re.compile(r"values-[a-z]{2,3}(-r[A-Z]{2})?")


def locales(res):
    # Only language qualifiers: values-ldrtl and the like hold overrides, not translations.
    return sorted(
        directory.name for directory in Path(res).glob("values-*")
        if LOCALE_DIRECTORY.fullmatch(directory.name) and (directory / "strings.xml").exists()
    )


def read_file(path):
    file = Path(path)
    return json.loads(file.read_text(encoding="utf-8")) if file.exists() else {}


def read_context(path):
    return read_file(path).get(PLATFORM, {})


def context_at(ref, path):
    path = Path(path)
    shown = subprocess.run(["git", "-C", str(path.parent), "show", f"{ref}:./{path.name}"],
                           capture_output=True, text=True, encoding="utf-8", check=True)
    return json.loads(shown.stdout).get(PLATFORM, {})


def plan(res, context_path, regenerate_context, regenerated_since=None):
    english = {key: entry for key, entry in load(Path(res) / "values" / "strings.xml").items() if entry.translatable}
    context = read_context(context_path)
    # A regeneration cut short continues: the keys it already rewrote differ from the context at its start.
    before = context_at(regenerated_since, context_path) if regenerate_context and regenerated_since else None
    translated = {locale: load(Path(res) / locale / "strings.xml") for locale in locales(res)}

    keys = []
    for key, entry in english.items():
        recorded = context.get(key)
        changed = recorded is not None and recorded.get("source") != entry.text
        missing = [locale for locale, entries in translated.items() if key not in entries]
        reasons = []
        if missing:
            reasons.append("missing")
        if changed:
            reasons.append("changed")
        if recorded is None:
            reasons.append("no_context")
        if regenerate_context and recorded is not None and (before is None or before.get(key) == recorded):
            reasons.append("regenerate_context")
        if not reasons:
            continue
        keys.append({
            "key": key,
            "kind": entry.kind,
            "source": entry.text,
            # A changed English text is retranslated in every locale; otherwise only where it is missing.
            "translate": list(translated) if changed else missing,
            "write_context": recorded is None or changed or regenerate_context,
            "reasons": reasons,
        })
    stale = sorted(key for key in context if key not in english)
    return {"locales": list(translated), "keys": keys, "stale_context": stale}


def report(result):
    lines = ["## Android translations", ""]
    keys = result["keys"]
    to_translate = [item for item in keys if item["translate"]]
    if not to_translate and not any(item["write_context"] for item in keys):
        lines.append("Every key is translated and has context.")
        return "\n".join(lines)

    lines += ["| Locale | To translate |", "|---|---|"]
    for locale in result["locales"]:
        pending = [item["key"] for item in to_translate if locale in item["translate"]]
        lines.append(f"| {locale.removeprefix('values-')} | {len(pending)} |")

    changed = [item["key"] for item in keys if "changed" in item["reasons"]]
    no_context = [item["key"] for item in keys if "no_context" in item["reasons"]]
    for title, items in (
        ("English changed since the last translation", changed),
        ("Keys without context", no_context),
        ("Context of removed keys", result["stale_context"]),
    ):
        if items:
            lines += ["", f"<details><summary>{title}: {len(items)}</summary>", ""]
            lines += [f"- `{key}`" for key in items]
            lines += ["", "</details>"]
    return "\n".join(lines)


def write_context(context_path, english, updates):
    context = read_context(context_path)
    for key, value in updates.items():
        context[key] = {
            "source": english[key].text,
            "usages": value.get("usages", []),
            "note": value.get("note"),
            "split": value.get("split"),
        }
    # Removed keys take their context with them.
    platforms = read_file(context_path)
    platforms[PLATFORM] = {key: context[key] for key in sorted(context) if key in english}
    Path(context_path).write_text(json.dumps(platforms, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def element_xml(name, kind, value):
    if kind == "plurals":
        items = "".join(f'\n        <item quantity="{quantity}">{text}</item>' for quantity, text in value.items())
        return f'    <plurals name="{name}">{items}\n    </plurals>'
    return f'    <string name="{name}">{value}</string>'


def locale_text(path, english, translations):
    text = path.read_text(encoding="utf-8")
    for key, value in translations.items():
        name, kind = english[key].name, english[key].kind
        if (kind == "plurals") != isinstance(value, dict):
            raise ValueError(f"{path.parent.name}/{key}: expected a {kind}")
        block = element_xml(name, kind, value)
        existing = re.compile(rf'^[ \t]*<{kind} name="{re.escape(name)}"[^>]*>.*?</{kind}>', re.M | re.S)
        if existing.search(text):
            text = existing.sub(lambda _: block, text, count=1)
        else:
            end = text.rindex("</resources>")
            text = text[:end].rstrip("\n") + "\n" + block + "\n" + text[end:]
    ET.fromstring(text)  # A translation that breaks the XML fails here, before any file is written.
    return text


def apply(res, context_path, changes):
    english = {key: entry for key, entry in load(Path(res) / "values" / "strings.xml").items() if entry.translatable}
    known = set(locales(res))
    unknown = [key for key in changes.get("context", {}) if key not in english]
    for locale, translations in changes.get("translations", {}).items():
        if locale not in known:
            raise ValueError(f"unknown locale {locale}")
        unknown += [f"{locale}/{key}" for key in translations if key not in english]
    if unknown:
        raise ValueError(f"not translatable keys of values/strings.xml: {', '.join(unknown)}")

    # Writing the context records the current English as translated: a key whose English changed
    # needs its new translations in the same changes, or the change would be lost.
    recorded = read_context(context_path)
    translations = changes.get("translations", {})
    incomplete = [
        f"{key} ({', '.join(sorted(known - {locale for locale in translations if key in translations[locale]}))})"
        for key in changes.get("context", {})
        if key in recorded and recorded[key].get("source") != english[key].text
        and any(key not in translations.get(locale, {}) for locale in known)
    ]
    if incomplete:
        raise ValueError(f"English changed, translate into every locale with the context: {'; '.join(incomplete)}")
    # Without the context its `source` keeps the old English, and every later plan retranslates the key.
    contextless = sorted({
        key for locale in translations for key in translations[locale]
        if key in recorded and recorded[key].get("source") != english[key].text and key not in changes.get("context", {})
    })
    if contextless:
        raise ValueError(f"English changed, send the context with the translations: {', '.join(contextless)}")

    # Every locale is built and checked first, and the context goes last: a failure leaves nothing half-written.
    texts = {locale: locale_text(Path(res) / locale / "strings.xml", english, items) for locale, items in translations.items()}
    for locale, text in texts.items():
        (Path(res) / locale / "strings.xml").write_text(text, encoding="utf-8")
    write_context(context_path, english, changes.get("context", {}))


def placeholders(text):
    return sorted(token for token in FORMAT_TOKEN.findall(text) if token not in ("%", "%%"))


def formatted(text):
    return any(token != "%" for token in FORMAT_TOKEN.findall(text))


def annotations(text):
    return sorted(re.findall(r"<annotation\b[^>]*>", text))


def validate(res, only_keys):
    english = {key: entry for key, entry in load(Path(res) / "values" / "strings.xml").items() if entry.translatable}
    problems = []
    for locale in locales(res):
        try:
            translated = load(Path(res) / locale / "strings.xml")
        except ET.ParseError as error:
            problems.append(f"{locale}: not valid XML: {error}")
            continue
        problems += [f"{locale}/{key}: defined more than once" for key in duplicates(Path(res) / locale / "strings.xml")]
        for key, entry in translated.items():
            if key not in english or (only_keys and key not in only_keys):
                continue
            source = english[key]
            where = f"{locale}/{key}"
            if entry.kind == "plurals" and "other" not in entry.forms:
                problems.append(f"{where}: no 'other' plural form")
            reference = source.forms.get("other", source.forms.get("", ""))
            for quantity, text in entry.forms.items():
                label = f"{where}[{quantity}]" if quantity else where
                expected = source.forms.get(quantity, reference)
                if placeholders(text) != placeholders(expected):
                    problems.append(f"{label}: placeholders {placeholders(text)}, expected {placeholders(expected)}")
                # getString(id, args) throws on a % that is neither %% nor a conversion.
                if formatted(expected) and "%" in FORMAT_TOKEN.findall(text):
                    problems.append(f"{label}: a lone %, write %% for a percent sign")
                if annotations(text) != annotations(expected):
                    problems.append(f"{label}: <annotation> tags differ from the English")
                if UNESCAPED_APOSTROPHE.search(text):
                    problems.append(f"{label}: unescaped apostrophe, write \\'")
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--res", default=DEFAULT_RES)
    parser.add_argument("--context", default=DEFAULT_CONTEXT)
    commands = parser.add_subparsers(dest="command", required=True)
    plan_command = commands.add_parser("plan")
    plan_command.add_argument("--regenerate-context", action="store_true")
    plan_command.add_argument("--regenerated-since", metavar="REF",
                              help="with --regenerate-context: skip keys whose context changed since this commit")
    commands.add_parser("report")
    validate_command = commands.add_parser("validate")
    validate_command.add_argument("--keys", nargs="*", default=[], help="only these keys; all when omitted")
    apply_command = commands.add_parser("apply")
    apply_command.add_argument("changes", help="JSON file with the context and translations")
    args = parser.parse_args()

    if args.command == "plan":
        json.dump(plan(args.res, args.context, args.regenerate_context, args.regenerated_since), sys.stdout, ensure_ascii=False, indent=2)
        print()
    elif args.command == "report":
        print(report(plan(args.res, args.context, regenerate_context=False)))
    elif args.command == "apply":
        apply(args.res, args.context, json.loads(Path(args.changes).read_text(encoding="utf-8")))
    else:
        problems = validate(args.res, set(args.keys))
        for problem in problems:
            print(problem)
        sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
