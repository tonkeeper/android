import json
import tempfile
import time
import unittest
from pathlib import Path

import locales


def strings(path, lines):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(f"{line}\n" for line in lines), encoding="utf-8")


class IosTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.res = self.root / "Locales"
        self.context = self.root / "context.json"
        strings(self.res / "en.lproj/Localizable.strings",
                ['"a" = "A";', "// MARK: - B", '"b" = "B %@";', '"c" = "C";'])
        strings(self.res / "de.lproj/Localizable.strings", ['"c" = "C de";', '"a" = "A de";'])

    def changes(self, value):
        # Keys with no context yet take it with their first translation.
        value.setdefault("context", {key: {"usages": [], "note": None, "split": None}
                                     for items in value.get("translations", {}).values() for key in items})
        path = self.root / "changes.json"
        path.write_text(json.dumps(value), encoding="utf-8")
        return json.loads(path.read_text(encoding="utf-8"))

    def de(self):
        return (self.res / "de.lproj/Localizable.strings").read_text(encoding="utf-8")

    def test_a_new_key_goes_after_the_one_before_it_in_english(self):
        locales.apply(locales.Ios, self.res, self.context, self.changes({"translations": {"de": {"b": "B %@ de"}}}))
        self.assertEqual(self.de(), '"c" = "C de";\n"a" = "A de";\n"b" = "B %@ de";\n')

    def test_a_key_with_no_english_key_before_it_goes_first(self):
        strings(self.res / "de.lproj/Localizable.strings", ['"c" = "C de";'])
        locales.apply(locales.Ios, self.res, self.context, self.changes({"translations": {"de": {"a": "A de"}}}))
        self.assertEqual(self.de(), '"a" = "A de";\n"c" = "C de";\n')

    def test_an_existing_key_is_replaced_in_place(self):
        locales.apply(locales.Ios, self.res, self.context, self.changes({"translations": {"de": {"a": "A neu"}}}))
        self.assertEqual(self.de(), '"c" = "C de";\n"a" = "A neu";\n')

    def test_an_unescaped_quote_writes_nothing(self):
        with self.assertRaises(ValueError):
            locales.apply(locales.Ios, self.res, self.context,
                          self.changes({"translations": {"de": {"a": 'A "de"', "b": "B %@"}}}))
        self.assertEqual(self.de(), '"c" = "C de";\n"a" = "A de";\n')

    def test_a_line_that_is_not_a_statement_fails_to_load(self):
        strings(self.res / "de.lproj/Localizable.strings", ['"a" = "A de";', '"b" = "B', 'de";'])
        self.assertEqual(len(locales.validate(locales.Ios, self.res, set())), 1)

    def test_a_broken_line_after_a_long_gap_fails_at_once(self):
        started = time.monotonic()
        with self.assertRaisesRegex(locales.StringsError, "line 2"):
            locales.Ios.statements('"a" = "A";\n' + " " * 60 + "x\n")
        self.assertLess(time.monotonic() - started, 1)

    def test_a_broken_english_file_is_reported_not_raised(self):
        strings(self.res / "en.lproj/Localizable.strings", ['"a" = "A"; // kept', '"b" = "B', '%@";'])
        (problem,) = locales.validate(locales.Ios, self.res, set())
        self.assertRegex(problem, r"^en: not a valid strings file: .*line 2")

    def test_a_key_with_no_context_takes_it_with_its_translation(self):
        with self.assertRaises(ValueError):
            locales.apply(locales.Ios, self.res, self.context,
                          self.changes({"context": {}, "translations": {"de": {"b": "B %@ de"}}}))
        self.assertEqual(self.de(), '"c" = "C de";\n"a" = "A de";\n')

    def test_a_percent_sign_is_not_a_placeholder(self):
        self.assertEqual(locales.placeholders(locales.Ios, "50% off, %@ and %.2f%%"), ["%1$@", "%2$.2f"])

    def test_positional_specifiers_may_reorder_the_english_ones(self):
        strings(self.res / "en.lproj/Localizable.strings", ['"a" = "%@ + %ld NFT";'])
        strings(self.res / "de.lproj/Localizable.strings", ['"a" = "%2$ld NFT + %1$@";'])
        self.assertEqual(locales.validate(locales.Ios, self.res, set()), [])

    def test_apply_refuses_a_russian_the_plan_did_not_ask_for(self):
        strings(self.res / "ru.lproj/Localizable.strings", ['"a" = "А";'])
        self.context.write_text(json.dumps({"ios": {"a": {"source": "A", "usages": [], "written": "А машинное"}}}), encoding="utf-8")
        with self.assertRaises(ValueError):
            locales.apply(locales.Ios, self.res, self.context, self.changes({"context": {}, "translations": {"ru": {"a": "А2"}}}))

    def test_apply_lets_the_run_fix_its_own_russian(self):
        strings(self.res / "ru.lproj/Localizable.strings", ['"b" = "50% %@";'])
        self.context.write_text(json.dumps({"ios": {"b": {"source": "B %@", "usages": [], "written": "50% %@"}}}), encoding="utf-8")
        locales.apply(locales.Ios, self.res, self.context, self.changes({"context": {}, "translations": {"ru": {"b": "50%% %@"}}}))
        self.assertIn('"b" = "50%% %@";', (self.res / "ru.lproj/Localizable.strings").read_text(encoding="utf-8"))

    def test_a_lone_percent_fails_in_every_string_english_included(self):
        strings(self.res / "en.lproj/Localizable.strings", ['"a" = "5% off";', '"b" = "B %@";', '"c" = "C";'])
        strings(self.res / "de.lproj/Localizable.strings", ['"a" = "5%% weniger";', '"c" = "5 % C";'])
        self.assertEqual(locales.validate(locales.Ios, self.res, set()),
                         ["en/a: a lone %, write %% for a percent sign", "de/c: a lone %, write %% for a percent sign"])

    def test_swapped_non_positional_placeholders_fail(self):
        strings(self.res / "en.lproj/Localizable.strings", ['"a" = "%@ + %ld NFT";'])
        strings(self.res / "de.lproj/Localizable.strings", ['"a" = "%ld NFT + %@";'])
        self.assertEqual(len(locales.validate(locales.Ios, self.res, set())), 1)

    def test_validate_reports_a_lost_placeholder_and_a_duplicate(self):
        strings(self.res / "de.lproj/Localizable.strings", ['"a" = "A de";', '"a" = "A de";', '"b" = "B de";'])
        self.assertEqual(locales.validate(locales.Ios, self.res, set()),
                         ["de/a: defined more than once", "de/b: placeholders [], expected ['%@']"])

    def test_changed_english_is_retranslated_everywhere_and_a_bare_source_gets_context(self):
        self.context.write_text(json.dumps({"ios": {"a": {"source": "Old A"}, "c": {"source": "C"}}}), encoding="utf-8")
        keys = {item["key"]: item for item in locales.plan(locales.Ios, self.res, self.context, False)["keys"]}
        self.assertEqual((keys["a"]["reasons"], keys["a"]["translate"], keys["a"]["write_context"]), (["changed"], ["de"], True))
        self.assertEqual((keys["b"]["reasons"], keys["b"]["write_context"]), (["missing", "no_context"], True))
        self.assertNotIn("c", keys)

    def test_changed_english_keeps_a_russian_written_by_hand(self):
        strings(self.res / "ru.lproj/Localizable.strings", ['"a" = "А по-своему";', '"b" = "Б %@";'])
        self.context.write_text(json.dumps({"ios": {
            "a": {"source": "Old A", "written": "А машинное"},
            "b": {"source": "Old B %@"},
        }}), encoding="utf-8")
        keys = {item["key"]: item for item in locales.plan(locales.Ios, self.res, self.context, False)["keys"]}
        self.assertEqual((keys["a"]["translate"], keys["a"]["kept"]), (["de"], ["ru"]))
        self.assertEqual((keys["b"]["translate"], keys["b"]["kept"]), (["de"], ["ru"]))

    def test_a_hand_written_russian_with_other_placeholders_than_the_new_english_is_retranslated(self):
        strings(self.res / "ru.lproj/Localizable.strings", ['"b" = "Б без плейсхолдера";'])
        self.context.write_text(json.dumps({"ios": {"b": {"source": "Old B"}}}), encoding="utf-8")
        keys = {item["key"]: item for item in locales.plan(locales.Ios, self.res, self.context, False)["keys"]}
        self.assertEqual((keys["b"]["translate"], keys["b"]["kept"]), (["de", "ru"], []))

    def test_changed_english_retranslates_the_russian_this_script_wrote(self):
        strings(self.res / "ru.lproj/Localizable.strings", ['"a" = "А машинное";'])
        self.context.write_text(json.dumps({"ios": {"a": {"source": "Old A", "written": "А машинное"}}}), encoding="utf-8")
        keys = {item["key"]: item for item in locales.plan(locales.Ios, self.res, self.context, False)["keys"]}
        self.assertEqual((keys["a"]["translate"], keys["a"]["kept"]), (["de", "ru"], []))

    def test_apply_refuses_to_overwrite_a_russian_written_by_hand(self):
        strings(self.res / "ru.lproj/Localizable.strings", ['"a" = "А по-своему";'])
        self.context.write_text(json.dumps({"ios": {"a": {"source": "Old A"}}}), encoding="utf-8")
        context = {"a": {"usages": [], "note": None, "split": None}}
        with self.assertRaises(ValueError):
            locales.apply(locales.Ios, self.res, self.context,
                          self.changes({"context": context, "translations": {"de": {"a": "A2"}, "ru": {"a": "А2"}}}))
        locales.apply(locales.Ios, self.res, self.context, self.changes({"context": context, "translations": {"de": {"a": "A2"}}}))
        recorded = json.loads(self.context.read_text(encoding="utf-8"))["ios"]["a"]
        self.assertEqual((recorded["source"], "written" in recorded), ("A", False))

    def test_apply_records_the_russian_it_wrote(self):
        strings(self.res / "ru.lproj/Localizable.strings", ['"c" = "В";'])
        self.context.write_text(json.dumps({"ios": {"a": {"source": "A", "usages": [], "written": "old"}}}), encoding="utf-8")
        locales.apply(locales.Ios, self.res, self.context, self.changes({"context": {}, "translations": {"ru": {"a": "А"}}}))
        recorded = json.loads(self.context.read_text(encoding="utf-8"))["ios"]["a"]
        self.assertEqual(recorded, {"source": "A", "usages": [], "written": "А"})


class AndroidTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.res = self.root / "res"
        self.context = self.root / "context.json"
        self.xml("values", '<plurals name="days"><item quantity="one">%d day</item><item quantity="other">%d days</item></plurals>')
        self.xml("values-de", '<plurals name="days"><item quantity="one">%d Tag</item><item quantity="other">%d Tage</item></plurals>')

    def xml(self, locale, body):
        path = self.res / locale / "strings.xml"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    {body}\n</resources>\n', encoding="utf-8")

    def kept(self, written):
        entry = {"source": {"one": "%d old day", "other": "%d old days"}, "usages": []}
        if written is not None:
            entry["written"] = written
        self.context.write_text(json.dumps({"android": {"plurals:days": entry}}), encoding="utf-8")
        (item,) = locales.plan(locales.Android, self.res, self.context, False)["keys"]
        return item["translate"], item["kept"]

    def test_a_plural_russian_this_script_wrote_is_retranslated(self):
        self.xml("values-ru", '<plurals name="days"><item quantity="one">%d день</item><item quantity="other">%d дней</item></plurals>')
        self.assertEqual(self.kept({"one": "%d день", "other": "%d дней"}), (["values-de", "values-ru"], []))

    def test_a_plural_russian_written_by_hand_is_kept(self):
        self.xml("values-ru", '<plurals name="days"><item quantity="one">%d сутки</item><item quantity="other">%d суток</item></plurals>')
        self.assertEqual(self.kept({"one": "%d день", "other": "%d дней"}), (["values-de"], ["values-ru"]))
        self.assertEqual(self.kept(None), (["values-de"], ["values-ru"]))


if __name__ == "__main__":
    unittest.main()
