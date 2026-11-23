#!/usr/bin/env python3
"""Finds what dev has to translate and checks the translations, for Android and iOS.

The English strings are the source: Android's `values/strings.xml`, iOS's `en.lproj/Localizable.strings`.
The context file keeps, per key, the English text the context was written for (`source`), so a key
whose English changed since is retranslated. Keys sit under their platform
({"android": {"<key>": ...}, "ios": {...}}): iOS and Android name keys differently.
An entry with `source` alone records the English its translations were made for, with no context
yet: the context is written when the key is next translated.

Russian is often written by hand, from the design, and need not follow the English word for word.
The entry keeps the Russian this script last wrote (`written`); when the English changes, a Russian
text that differs from it, or a key with no `written`, is a person's and is kept: the plan lists
the key under `kept` instead of retranslating it. A missing Russian text is always translated.

  plan      keys to (re)translate and to write context for, as JSON; with --regenerate-context
            --regenerated-since <ref>, keys whose context changed since that commit are done; with
            --leave-to-release <checkout>, keys with the same English there are left to that release branch
  report    Markdown summary of missing translations, changed English and missing context
  validate  placeholders and duplicate keys; on Android also <annotation> tags, apostrophes,
            plural forms and key order
  apply     writes the agent's context and translations (JSON) into the context file and locales
  sort      Android: puts the keys of every locale in alphabetical order

Android locales keep their keys in alphabetical order: a key added on dev and one added on a release
branch land in different places, and the back-merge brings them together without a conflict. An iOS
locale gets a new key after the one before it in the English file.

The agent never edits the strings files itself: it hands `apply` a JSON file

  {"context": {"<key>": {"usages": [{"where": "...", "role": "..."}], "note": "...", "split": null}},
   "translations": {"<locale>": {"<key>": "text", "plurals:<key>": {"one": "...", "other": "..."}}}}

with texts written as the strings file holds them: on Android (locales `values-ru`…) tags kept,
& < > and apostrophes escaped; on iOS (locales `ru`…) " and line breaks escaped as \\" and \\n.
"""

import argparse
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import escape

DEFAULT_CONTEXT = "shared/localization/translation-context.json"


class Entry:
    def __init__(self, name, kind, forms, translatable=True):
        self.name = name
        self.kind = kind
        # A string and a plural may share a name.
        self.key = f"plurals:{name}" if kind == "plurals" else name
        self.translatable = translatable
        self.forms = forms

    @property
    def text(self):
        if self.kind == "plurals":
            return {quantity: self.forms[quantity] for quantity in sorted(self.forms)}
        return self.forms[""]


class Android:
    name = "android"
    title = "Android"
    res = "android/apps/wallet/localization/src/main/res"
    source_locale = "values"
    manual_locale = "values-ru"
    # getString(id) without arguments returns the text as it is: only a formatted string has to escape %.
    formats_every_string = False
    ParseError = ET.ParseError

    # One token at a time, so "%%1$s" reads as a literal percent and "1$s", not as a placeholder.
    FORMAT_TOKEN = re.compile(r"%%|%(?:\d+\$)?[sdf]|%")
    UNESCAPED_APOSTROPHE = re.compile(r"(?<!\\)'")
    LOCALE_DIRECTORY = re.compile(r"values-[a-z]{2,3}(-r[A-Z]{2})?")
    # One top-level entry with its indentation and line break.
    ELEMENT = re.compile(r'^[ \t]*<(string|plurals) name="([^"]+)"[^>]*>.*?</\1>[ \t]*\n?', re.M | re.S)
    OPENING = re.compile(r"<resources\b[^>]*>\n?")

    @staticmethod
    def path(res, locale):
        return Path(res) / locale / "strings.xml"

    @classmethod
    def locales(cls, res):
        # Only language qualifiers: values-ldrtl and the like hold overrides, not translations.
        return sorted(
            directory.name for directory in Path(res).glob("values-*")
            if cls.LOCALE_DIRECTORY.fullmatch(directory.name) and (directory / "strings.xml").exists()
        )

    @staticmethod
    def inner(element):
        """The element's content as written, with its tags: what a translator has to keep."""
        parts = [escape(element.text or "")]
        for child in element:
            parts.append(ET.tostring(child, encoding="unicode"))
        return "".join(parts).strip()

    @classmethod
    def entries(cls, path):
        entries = []
        for element in ET.parse(path).getroot():
            if element.tag not in ("string", "plurals") or not element.get("name"):
                continue
            if element.tag == "plurals":
                forms = {item.get("quantity"): cls.inner(item) for item in element.findall("item")}
            else:
                forms = {"": cls.inner(element)}
            entries.append(Entry(element.get("name"), element.tag, forms, element.get("translatable") != "false"))
        return entries

    @staticmethod
    def order(kind, name):
        return name, kind

    @classmethod
    def sorted_text(cls, text):
        opening = cls.OPENING.search(text)
        end = text.rindex("</resources>")
        body = text[opening.end():end]
        elements = list(cls.ELEMENT.finditer(body))
        # Comments and anything else between the entries are dropped: sorted, they would mark the wrong keys.
        rest = cls.ELEMENT.sub("", body)
        leftover = re.sub(r"<!--.*?-->", "", rest, flags=re.S).strip()
        if leftover:
            raise ValueError(f"not a string or plurals entry: {leftover[:80]}")
        blocks = [match.group(0).rstrip() + "\n"
                  for match in sorted(elements, key=lambda match: cls.order(match.group(1), match.group(2)))]
        return text[:opening.end()].rstrip("\n") + "\n" + "".join(blocks) + text[end:]

    @classmethod
    def out_of_order(cls, text):
        keys = [cls.order(match.group(1), match.group(2)) for match in cls.ELEMENT.finditer(text)]
        return [current[0] for previous, current in zip(keys, keys[1:]) if current < previous]

    @classmethod
    def sort(cls, res):
        for locale in cls.locales(res):
            path = cls.path(res, locale)
            text = cls.sorted_text(path.read_text(encoding="utf-8"))
            ET.fromstring(text)
            path.write_text(text, encoding="utf-8")

    @staticmethod
    def element_xml(name, kind, value):
        if kind == "plurals":
            items = "".join(f'\n        <item quantity="{quantity}">{text}</item>' for quantity, text in value.items())
            return f'    <plurals name="{name}">{items}\n    </plurals>'
        return f'    <string name="{name}">{value}</string>'

    @classmethod
    def locale_text(cls, path, english, translations):
        text = path.read_text(encoding="utf-8")
        for key, value in translations.items():
            name, kind = english[key].name, english[key].kind
            if (kind == "plurals") != isinstance(value, dict):
                raise ValueError(f"{path.parent.name}/{key}: expected a {kind}")
            block = cls.element_xml(name, kind, value)
            existing = re.compile(rf'^[ \t]*<{kind} name="{re.escape(name)}"[^>]*>.*?</{kind}>', re.M | re.S)
            if existing.search(text):
                text = existing.sub(lambda _: block, text, count=1)
            else:
                following = next((match for match in cls.ELEMENT.finditer(text)
                                  if cls.order(match.group(1), match.group(2)) > cls.order(kind, name)), None)
                if following:
                    text = text[:following.start()] + block + "\n" + text[following.start():]
                else:
                    end = text.rindex("</resources>")
                    text = text[:end].rstrip("\n") + "\n" + block + "\n" + text[end:]
        ET.fromstring(text)  # A translation that breaks the XML fails here, before any file is written.
        return text

    @classmethod
    def problems(cls, locale, path, english, translated):
        problems = []
        unordered = cls.out_of_order(path.read_text(encoding="utf-8"))
        if unordered:
            problems.append(f"{locale}: keys out of alphabetical order from {unordered[0]}, run locales.py sort")
        for key, entry in translated.items():
            source = english[key]
            where = f"{locale}/{key}"
            if entry.kind == "plurals" and "other" not in entry.forms:
                problems.append(f"{where}: no 'other' plural form")
            reference = source.forms.get("other", source.forms.get("", ""))
            for quantity, text in entry.forms.items():
                label = f"{where}[{quantity}]" if quantity else where
                expected = source.forms.get(quantity, reference)
                problems += format_problems(cls, label, text, expected)
                if annotations(text) != annotations(expected):
                    problems.append(f"{label}: <annotation> tags differ from the English")
                if cls.UNESCAPED_APOSTROPHE.search(text):
                    problems.append(f"{label}: unescaped apostrophe, write \\'")
        return problems


class StringsError(ValueError):
    pass


class Ios:
    name = "ios"
    title = "iOS"
    res = "ios/LocalPackages/TKLocalize/Sources/TKLocalize/Resources/Locales"
    source_locale = "en"
    manual_locale = "ru"
    # TKLocales.tr passes every string through String(format:), with or without arguments: a lone %
    # eats the text after it ("5% off" shows "50ff"), so every string escapes it as %%.
    formats_every_string = True
    ParseError = StringsError

    # %@, %d, %ld, %1$@, %.2f… A % that is none of them, "50% off" included, is a lone % that
    # String(format:) garbles.
    FORMAT_TOKEN = re.compile(r"%%|%(?:\d+\$)?[-+#0]*\d*(?:\.\d+)?(?:hh|h|ll|l|q|z|t|j|L)?[@dDiuUxXoOfFeEgGcCsSpaA]|%")
    # One "key" = "value"; per line, as Localizable.strings is written: no line break inside a value.
    # A // comment may follow it.
    STATEMENT = re.compile(r'^[ \t]*"((?:[^"\\\n]|\\.)*)"[ \t]*=[ \t]*"((?:[^"\\\n]|\\.)*)"[ \t]*;[ \t]*(?://[^\n]*)?$', re.M)
    # Linear: whitespace between comments, never a repeated group of repeated whitespace, which
    # backtracks exponentially over a long run of blank lines before a broken one.
    GAP = re.compile(r"\s*(?:(?:/\*.*?\*/|//[^\n]*)\s*)*", re.S)
    UNESCAPED_QUOTE = re.compile(r'(?<!\\)"')

    @staticmethod
    def path(res, locale):
        return Path(res) / f"{locale}.lproj" / "Localizable.strings"

    @classmethod
    def locales(cls, res):
        return sorted(
            directory.name.removesuffix(".lproj") for directory in Path(res).glob("*.lproj")
            if directory.name != f"{cls.source_locale}.lproj" and (directory / "Localizable.strings").exists()
        )

    @classmethod
    def statements(cls, text, where=""):
        """Each "key" = "value"; of the file; anything but comments and blank lines between them fails."""
        result, position = [], 0
        for match in [*cls.STATEMENT.finditer(text), None]:
            gap = text[position:match.start() if match else len(text)]
            if not cls.GAP.fullmatch(gap):
                line = text.count("\n", 0, position + len(gap) - len(gap.lstrip())) + 1
                raise StringsError(f'{where}line {line}: not a "key" = "value"; line: {gap.strip()[:80]}')
            if match:
                result.append(match)
                position = match.end()
        return result

    @classmethod
    def entries(cls, path):
        text = path.read_text(encoding="utf-8")
        return [Entry(match.group(1), "string", {"": match.group(2)})
                for match in cls.statements(text, f"{path.parent.name}/{path.name}: ")]

    @classmethod
    def locale_text(cls, path, english, translations):
        text = path.read_text(encoding="utf-8")
        locale = path.parent.name.removesuffix(".lproj")
        order = list(english)
        for key, value in translations.items():
            if not isinstance(value, str):
                raise ValueError(f"{locale}/{key}: expected a string")
            if cls.UNESCAPED_QUOTE.search(value) or "\n" in value:
                raise ValueError(f'{locale}/{key}: write " as \\" and a line break as \\n')
            line = f'"{key}" = "{value}";'
            found = {match.group(1): match for match in cls.statements(text, f"{locale}: ")}
            if key in found:
                match = found[key]
                text = text[:match.start()] + line + text[match.end():]
                continue
            previous = next((found[before] for before in reversed(order[:order.index(key)]) if before in found), None)
            if previous:
                text = text[:previous.end()] + "\n" + line + text[previous.end():]
            elif found:
                first = min(found.values(), key=lambda match: match.start())
                text = text[:first.start()] + line + "\n" + text[first.start():]
            else:
                text = text.rstrip("\n") + ("\n" if text.strip() else "") + line + "\n"
        cls.statements(text, f"{locale}: ")  # A translation that breaks the file fails here, before any file is written.
        return text

    @classmethod
    def problems(cls, locale, path, english, translated):
        problems = []
        for key, entry in translated.items():
            problems += format_problems(cls, f"{locale}/{key}", entry.text, english[key].text)
        return problems


PLATFORMS = {platform.name: platform for platform in (Android, Ios)}


def load(platform, path):
    return {entry.key: entry for entry in platform.entries(path)}


def english_of(platform, res):
    path = platform.path(res, platform.source_locale)
    return {key: entry for key, entry in load(platform, path).items() if entry.translatable}


def duplicates(platform, path):
    # Two branches that translate the same key differently each insert it: git may merge both.
    keys = [entry.key for entry in platform.entries(path)]
    return sorted({key for key in keys if keys.count(key) > 1})


def read_file(path):
    file = Path(path)
    return json.loads(file.read_text(encoding="utf-8")) if file.exists() else {}


def read_context(platform, path):
    return read_file(path).get(platform.name, {})


def context_at(platform, ref, path):
    path = Path(path)
    shown = subprocess.run(["git", "-C", str(path.parent), "show", f"{ref}:./{path.name}"],
                           capture_output=True, text=True, encoding="utf-8", check=True)
    return json.loads(shown.stdout).get(platform.name, {})


def release_english(platform, root):
    """English of a release branch checkout, by key."""
    return {key: entry.text for key, entry in english_of(platform, Path(root) / platform.res).items()}


def plan(platform, res, context_path, regenerate_context, regenerated_since=None, release=None):
    english = english_of(platform, res)
    release = release or {}
    context = read_context(platform, context_path)
    # A regeneration cut short continues: the keys it already rewrote differ from the context at its start.
    before = context_at(platform, regenerated_since, context_path) if regenerate_context and regenerated_since else None
    translated = {locale: load(platform, platform.path(res, locale)) for locale in platform.locales(res)}

    keys = []
    for key, entry in english.items():
        recorded = context.get(key)
        # The release branch translates a key with the same English, or already has: the back-merge
        # brings its translations and context. Only a regeneration still rewrites the context here.
        left = release.get(key) == entry.text
        changed = not left and recorded is not None and recorded.get("source") != entry.text
        missing = [] if left else [locale for locale, entries in translated.items() if key not in entries]
        kept = changed and kept_by_hand(platform, key, recorded, translated, english)
        # A changed English text is retranslated in every locale but a Russian written by hand;
        # otherwise only where it is missing.
        translate = [locale for locale in translated if not (kept and locale == platform.manual_locale)] if changed else missing
        reasons = []
        if missing:
            reasons.append("missing")
        if changed:
            reasons.append("changed")
        if kept:
            reasons.append("kept_by_hand")
        if recorded is None and not left:
            reasons.append("no_context")
        if regenerate_context and recorded is not None and (before is None or before.get(key) == recorded):
            reasons.append("regenerate_context")
        if not reasons:
            continue
        # An entry with `source` alone gets its context once the key is translated again.
        bare = recorded is not None and "usages" not in recorded
        keys.append({
            "key": key,
            "kind": entry.kind,
            "source": entry.text,
            "translate": translate,
            "write_context": recorded is None or changed or regenerate_context or (bare and bool(translate)),
            "kept": [platform.manual_locale] if kept else [],
            "reasons": reasons,
        })
    stale = sorted(key for key in context if key not in english)
    return {"locales": list(translated), "keys": keys, "stale_context": stale}


def placeholders_differ(platform, entry, source):
    reference = source.forms.get("other", source.forms.get("", ""))
    return any(placeholders(platform, text) != placeholders(platform, source.forms.get(quantity, reference))
               for quantity, text in entry.forms.items())


def kept_by_hand(platform, key, recorded, translated, english):
    """Whether the key's Russian text is a person's: not the one this script last wrote there.

    A Russian text whose placeholders no longer match the English is retranslated all the same:
    kept, it would fail validation, and on iOS crash the formatting."""
    manual = translated.get(platform.manual_locale, {})
    if key not in manual:
        return False
    if placeholders_differ(platform, manual[key], english[key]):
        return False
    written = (recorded or {}).get("written")
    return written is None or written != manual[key].text


def report(platform, result):
    lines = [f"## {platform.title} translations", ""]
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
    kept = [item["key"] for item in keys if item.get("kept")]
    no_context = [item["key"] for item in keys if "no_context" in item["reasons"]]
    for title, items in (
        ("English changed since the last translation", changed),
        (f"English changed, {platform.manual_locale.removeprefix('values-')} written by hand is kept: check it", kept),
        ("Keys without context", no_context),
        ("Context of removed keys", result["stale_context"]),
    ):
        if items:
            lines += ["", f"<details><summary>{title}: {len(items)}</summary>", ""]
            lines += [f"- `{key}`" for key in items]
            lines += ["", "</details>"]
    return "\n".join(lines)


def write_context(platform, context_path, english, updates, written):
    context = read_context(platform, context_path)
    for key, value in updates.items():
        entry = {
            "source": english[key].text,
            "usages": value.get("usages", []),
            "note": value.get("note"),
            "split": value.get("split"),
        }
        if "written" in context.get(key, {}):
            entry["written"] = context[key]["written"]
        context[key] = entry
    # Only into an entry that exists: a made-up one would hide that the key has no context yet.
    for key, text in written.items():
        if key in context:
            context[key]["written"] = text
    # Removed keys take their context with them.
    platforms = read_file(context_path)
    platforms[platform.name] = {key: context[key] for key in sorted(context) if key in english}
    Path(context_path).write_text(json.dumps(platforms, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def apply(platform, res, context_path, changes):
    english = english_of(platform, res)
    known = set(platform.locales(res))
    unknown = [key for key in changes.get("context", {}) if key not in english]
    for locale, translations in changes.get("translations", {}).items():
        if locale not in known:
            raise ValueError(f"unknown locale {locale}")
        unknown += [f"{locale}/{key}" for key in translations if key not in english]
    if unknown:
        raise ValueError(f"not translatable keys of the English strings: {', '.join(unknown)}")

    # Writing the context records the current English as translated: a key whose English changed
    # needs its new translations in the same changes, or the change would be lost.
    recorded = read_context(platform, context_path)
    translations = changes.get("translations", {})
    manual = platform.manual_locale
    current = {manual: load(platform, platform.path(res, manual))} if manual in known else {}
    changed = {key for key in english if key in recorded and recorded[key].get("source") != english[key].text}
    kept = {key for key in changed if kept_by_hand(platform, key, recorded[key], current, english)}
    # A Russian written by hand is never replaced, whether its English changed or not; the run's own
    # text may be, to fix what validate reports.
    overwritten = sorted(key for key in translations.get(manual, {})
                         if kept_by_hand(platform, key, recorded.get(key), current, english))
    if overwritten:
        raise ValueError(f"{manual} is not to be retranslated (written by hand, or English unchanged): {', '.join(overwritten)}")
    incomplete = []
    for key in changes.get("context", {}):
        required = known - ({manual} if key in kept else set())
        absent = sorted(locale for locale in required if key not in translations.get(locale, {}))
        if key in changed and absent:
            incomplete.append(f"{key} ({', '.join(absent)})")
    if incomplete:
        raise ValueError(f"English changed, translate into every locale with the context: {'; '.join(incomplete)}")
    # Without the context its `source` keeps the old English, and every later plan retranslates the key.
    contextless = sorted({
        key for locale in translations for key in translations[locale]
        if key in recorded and recorded[key].get("source") != english[key].text and key not in changes.get("context", {})
    })
    if contextless:
        raise ValueError(f"English changed, send the context with the translations: {', '.join(contextless)}")
    # The plan asks for it once: translated without it, the key would never get its context.
    uncontexted = sorted({
        key for locale in translations for key in translations[locale]
        if "usages" not in recorded.get(key, {}) and key not in changes.get("context", {})
    })
    if uncontexted:
        raise ValueError(f"no context yet, send it with the translations: {', '.join(uncontexted)}")

    # Every locale is built and checked before anything is written, so a bad translation writes nothing;
    # the context goes last. A run killed between the writes leaves the Russian looking hand-written.
    texts = {locale: platform.locale_text(platform.path(res, locale), english, items)
             for locale, items in translations.items()}
    for locale, text in texts.items():
        platform.path(res, locale).write_text(text, encoding="utf-8")
    # As the file holds it, so the next plan compares like with like.
    written = {}
    if translations.get(manual):
        entries = load(platform, platform.path(res, manual))
        written = {key: entries[key].text for key in translations[manual]}
    write_context(platform, context_path, english, changes.get("context", {}), written)


POSITIONAL = re.compile(r"%\d+\$")


def placeholders(platform, text):
    tokens = [token for token in platform.FORMAT_TOKEN.findall(text) if token not in ("%", "%%")]
    if not platform.formats_every_string:
        return sorted(tokens)
    # String(format:) takes the n-th non-positional specifier as argument n: swapping %@ and %ld
    # crashes, while %2$ld … %1$@ is the same as %@ … %ld. Each specifier is named by its argument.
    numbered, position = [], 0
    for token in tokens:
        if POSITIONAL.match(token):
            numbered.append(token)
        else:
            position += 1
            numbered.append(f"%{position}${token[1:]}")
    return sorted(numbered)


def formatted(platform, text):
    return any(token != "%" for token in platform.FORMAT_TOKEN.findall(text))


def format_problems(platform, label, text, expected):
    problems = []
    if placeholders(platform, text) != placeholders(platform, expected):
        # As written, not numbered: the numbering is only how they are compared.
        def written(value):
            return [token for token in platform.FORMAT_TOKEN.findall(value) if token not in ("%", "%%")]
        problems.append(f"{label}: placeholders {written(text)}, expected {written(expected)}")
    # Formatting throws, or prints garbage, on a % that is neither %% nor a conversion.
    if (platform.formats_every_string or formatted(platform, expected)) and "%" in platform.FORMAT_TOKEN.findall(text):
        problems.append(f"{label}: a lone %, write %% for a percent sign")
    return problems


def annotations(text):
    return sorted(re.findall(r"<annotation\b[^>]*>", text))


def validate(platform, res, only_keys):
    source = platform.path(res, platform.source_locale)
    try:
        english = english_of(platform, res)
    except platform.ParseError as error:
        # Nothing else can be checked against an English file that does not load.
        return [f"{platform.source_locale}: not a valid strings file: {error}"]
    problems = [f"{platform.source_locale}/{key}: defined more than once" for key in duplicates(platform, source)]
    if platform.formats_every_string:
        problems += [f"{platform.source_locale}/{key}: a lone %, write %% for a percent sign"
                     for key, entry in english.items() if "%" in platform.FORMAT_TOKEN.findall(entry.text)]
    for locale in platform.locales(res):
        path = platform.path(res, locale)
        try:
            translated = load(platform, path)
        except platform.ParseError as error:
            problems.append(f"{locale}: not a valid strings file: {error}")
            continue
        problems += [f"{locale}/{key}: defined more than once" for key in duplicates(platform, path)]
        checked = {key: entry for key, entry in translated.items()
                   if key in english and (not only_keys or key in only_keys)}
        problems += platform.problems(locale, path, english, checked)
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--platform", choices=sorted(PLATFORMS), required=True)
    parser.add_argument("--res", help="the directory of the locales; the platform's own by default")
    parser.add_argument("--context", default=DEFAULT_CONTEXT)
    commands = parser.add_subparsers(dest="command", required=True)
    plan_command = commands.add_parser("plan")
    plan_command.add_argument("--regenerate-context", action="store_true")
    plan_command.add_argument("--regenerated-since", metavar="REF",
                              help="with --regenerate-context: skip keys whose context changed since this commit")
    plan_command.add_argument("--leave-to-release", metavar="CHECKOUT",
                              help="leave keys with the same English in this release branch checkout to it")
    commands.add_parser("report")
    commands.add_parser("sort")
    validate_command = commands.add_parser("validate")
    validate_command.add_argument("--keys", nargs="*", default=[], help="only these keys; all when omitted")
    apply_command = commands.add_parser("apply")
    apply_command.add_argument("changes", help="JSON file with the context and translations")
    args = parser.parse_args()
    platform = PLATFORMS[args.platform]
    res = args.res or platform.res

    if args.command == "plan":
        release = release_english(platform, args.leave_to_release) if args.leave_to_release else None
        result = plan(platform, res, args.context, args.regenerate_context, args.regenerated_since, release)
        json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
        print()
    elif args.command == "report":
        print(report(platform, plan(platform, res, args.context, regenerate_context=False)))
    elif args.command == "sort":
        if platform is not Android:
            parser.error("sort is Android's: an iOS locale keeps the order of the English file")
        platform.sort(res)
    elif args.command == "apply":
        apply(platform, res, args.context, json.loads(Path(args.changes).read_text(encoding="utf-8")))
    else:
        problems = validate(platform, res, set(args.keys))
        for problem in problems:
            print(problem)
        sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
