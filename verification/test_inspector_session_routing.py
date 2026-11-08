#!/usr/bin/env python3
"""Source contract for read-only JSON Inspector tabs. Physical Android behavior is separate."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "apps/MarkdownDesk/src/com/example/markdowndesk"
INDEX = APP / "workspace/UnifiedTabIndex.java"
TABS = APP / "UnifiedTabs.java"
INSPECTOR = APP / "JsonExplorerActivity.java"
ROUTER = APP / "workspace/WorkspaceRouter.java"

class InspectorSessionRouting(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.index = INDEX.read_text(encoding="utf-8")
        cls.tabs = TABS.read_text(encoding="utf-8")
        cls.inspector = INSPECTOR.read_text(encoding="utf-8")
        cls.router = ROUTER.read_text(encoding="utf-8")

    def test_three_distinct_kinds_and_legacy_api(self):
        self.assertIn("enum Kind { MARKDOWN, ARCHIVE, INSPECT }", self.index)
        self.assertIn("return merge(markdown,archive,null)", self.index)
        self.assertIn("append(all,known,inspector,Kind.INSPECT)", self.index)
        self.assertIn("if(count>=12)break", self.index)

    def test_persist_inspector_identity_without_source_bytes_or_grants(self):
        t = self.tabs
        self.assertIn('JS="unified_inspector_tabs_v1"', t)
        self.assertIn('static boolean validInspectorSource(Uri uri)',t)
        self.assertIn('raw.length()<=8192',t)
        self.assertIn('"content".equalsIgnoreCase(uri.getScheme())',t)
        self.assertIn('uri.getHost()!=null&&!uri.getHost().isEmpty()',t)
        self.assertIn('uri.getFragment()==null',t)
        self.assertIn('if(context==null||!validInspectorSource(uri))return false',t)
        self.assertIn('if(!validInspectorSource(uri)){',t)
        self.assertIn("if(next.size()>=12)return false", t)
        self.assertIn("next.add(new UnifiedTabIndex.Entry(UnifiedTabIndex.Kind.INSPECT,id,title,true,false))", t)
        self.assertIn("read(context,JS,UnifiedTabIndex.Kind.INSPECT)", t)
        self.assertNotIn("takePersistableUriPermission", t)
        self.assertNotIn("openOutputStream", t)

    def test_differentiated_reopen_and_explicit_close(self):
        t = self.tabs
        self.assertIn("entry.kind==UnifiedTabIndex.Kind.INSPECT?", t)
        self.assertIn('"com.example.markdowndesk.JsonExplorerActivity"', t)
        self.assertIn("intent.setData(uri)", t)
        self.assertIn("showInspectorClose(current)", t)
        self.assertIn("closeInspector(current,uri)", t)
        self.assertIn("if(wasActive && !next.isEmpty())", t)

    def test_picker_reopen_and_process_restore_register_without_eviction(self):
        s = self.inspector
        self.assertIn("!UnifiedTabs.openInspector(this,document)", s)
        self.assertIn("!UnifiedTabs.openInspector(this,next)", s)
        self.assertIn("!UnifiedTabs.openInspector(this,chosen)", s)
        self.assertIn("showFailure(\"Twelve JSON Inspector tabs", s)
        self.assertIn("state.putString(\"jsonDocumentUri\",document.toString())", s)
        self.assertIn("if(!openedFromSearch)closeInspectorTabForSwitch()", s)
        self.assertIn("finish();",s)
        self.assertNotIn("closeInspectorTabForSwitch();\n        worker.shutdownNow()", s)

    def test_bounded_per_document_navigation_restore(self):
        s=self.inspector
        t=self.tabs
        self.assertIn("private void saveInspectorPosition()",s)
        self.assertIn("private void restoreInspectorPosition(Uri uri)",s)
        self.assertIn('saved.put("steps",steps)',s)
        self.assertIn('saved.put("offset",Math.max(0,offset))',s)
        self.assertIn('saved.put("scroll",entryScroll==null?0:Math.max(0,entryScroll.getScrollY()))',s)
        self.assertIn('saved.put("filter",f)',s)
        self.assertIn("if(steps==null||steps.length()>64)return",s)
        self.assertIn("if(state==null)restoreInspectorPosition(document)",s)
        self.assertIn("restoreInspectorPosition(next);loadPage()",s)
        self.assertIn("restoreInspectorPosition(chosen)",s)
        self.assertIn("if(token==revision)entryScroll.scrollTo(0,y)",s)
        self.assertIn('remove("json_inspector_position_v1:"+id).apply()',t)

    def test_only_explicit_switch_and_close_retires_inspector(self):
        r = self.router
        self.assertIn("if(from==Workspace.INSPECT)", r)
        self.assertIn("closeInspectorTabForSwitch()", r)
        self.assertIn("if(launch(current,target)){", r)
        self.assertIn("if(!closeCurrent)", r)

if __name__ == "__main__":
    unittest.main()
