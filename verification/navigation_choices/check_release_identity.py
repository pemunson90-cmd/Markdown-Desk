from pathlib import Path
import ast, unittest

class ReleaseIdentity(unittest.TestCase):
    def test_exact_pairs(self):
        for name in ('check_layout.py', 'check_live_appearance.py'):
            tree=ast.parse((Path(__file__).parents[1]/name).read_text())
            pairs=[ast.literal_eval(n) for n in ast.walk(tree) if isinstance(n,ast.Set)]
            allowed=next(p for p in pairs if ('26','2.2.11-downloads-contrast1') in p)
            with self.subTest(gate=name):
                self.assertIn(('27','2.2.12-native-picker1'),allowed)
                self.assertIn(('28','2.3.0-navigation1'),allowed)
                self.assertIn(('29','2.3.1-readerlinks1'),allowed)
                self.assertNotIn(('28','2.2.12-native-picker1'),allowed)
                self.assertNotIn(('999','arbitrary'),allowed)
if __name__=='__main__': unittest.main()
