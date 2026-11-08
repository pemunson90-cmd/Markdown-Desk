"""Planner failure modes: prevent silent skips and needless app runs."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('selector', Path(__file__).with_name('select_checks.py'))
selector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selector)


class SelectionCheck(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.source = 'apps/MarkdownDesk/src/Reader.java'
        self.helper = 'shared/Anchor.java'
        for name, text in [(self.source, 'class Reader { Anchor anchor; }'),
                           (self.helper, 'class Anchor {}'),
                           ('verification/reader/run.sh', 'exit 0')]:
            p = self.root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(text)
        self.catalog = {'checks': [{'id': 'reader', 'family': 'verification/reader',
            'command': ['bash', 'verification/reader/run.sh'], 'question': 'Does reading retain its anchor?'}],
            'families': {'verification/reader': {'inputs': [self.source, self.helper],
            'test_paths': ['verification/reader/run.sh']}}}

    def test_docs_do_not_trigger_application_checks(self):
        p = selector.plan(self.root, self.catalog, ['README.md'])
        self.assertTrue(p['documentation_only'])
        self.assertEqual([], p['checks'])
        self.assertEqual([], p['unmapped'])

    def test_production_input_selects_owned_check_and_compile(self):
        p = selector.plan(self.root, self.catalog, [self.source])
        self.assertEqual(['reader'], [c['id'] for c in p['checks']])
        self.assertTrue(p['native_compile_required'])

    def test_shared_dependency_reaches_consumer(self):
        affected = selector.source_dependents(self.root, [self.helper])
        self.assertIn(self.source, affected)

    def test_unknown_source_is_not_a_green_empty_plan(self):
        p = selector.plan(self.root, self.catalog, ['apps/MarkdownDesk/src/NewReader.java'])
        self.assertEqual(['apps/MarkdownDesk/src/NewReader.java'], p['unmapped'])

    def test_new_fixture_invalidates_existing_family(self):
        p = selector.plan(self.root, self.catalog, ['verification/reader/NewCheck.java'])
        self.assertEqual(1, len(p['checks']))
        self.assertEqual([], p['unmapped'])

    def test_stale_inputs_require_inventory_repair(self):
        (self.root / self.helper).unlink()
        self.assertTrue(selector.validate(self.root, self.catalog))

    def test_duplicate_commands_are_reported(self):
        self.catalog['checks'].append(dict(self.catalog['checks'][0]))
        self.assertIn('duplicate command', '\n'.join(selector.validate(self.root, self.catalog)))

    def test_no_changes_is_not_documentation_or_release_acceptance(self):
        p = selector.plan(self.root, self.catalog, [])
        self.assertFalse(p['documentation_only'])
        self.assertEqual([], p['checks'])


if __name__ == '__main__':
    unittest.main()
