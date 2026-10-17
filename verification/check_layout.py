from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
a=root/'apps/MarkdownDesk';text=(a/'src/com/example/markdowndesk/MainActivity.java').read_text()
method=text.split('private void showAppearance() {',1)[1].split('public /* synthetic */ void lambda$showAppearance$60',1)[0]
assert 'appearanceScroll.addView(linearLayout, matchWrap())' in method
assert '.setView(appearanceScroll)' in method
assert '.setView(linearLayout)' not in method
assert method.index('Reset text sizes') < method.index('appearanceScroll.addView')
assert '.setPositiveButton("Done"' in method
for theme in ['Paper','Midnight','Sepia','Forest','Bubblegum','Amethyst','Black Rose','Ocean','Arcade']: assert '"'+theme+'"' in text
assert 'i >= strArr.length' in method
assert 'scrollView.addView(column, matchWrap())' in text
assert text.count('button.setForegroundGravity(Gravity.CENTER)') == 1
assert 'this.filesButton.setSingleLine(true);' in text
assert 'this.readOutlineButton = addReadTool("Outline"' in text
assert 'this.readAppearanceButton = addReadTool("Appearance"' in text
assert 'this.readTools.getChildAt(2)' not in text
assert 'this.readTools.getChildAt(3)' not in text
assert 'p.width = dp(48); p.height = dp(48)' in text
assert 'this.filesButton.setEllipsize(TextUtils.TruncateAt.END);' in text
assert 'this.helpButton.setVisibility(compact ? View.GONE : View.VISIBLE);' in text
assert 'Tap the M button' not in text
assert 'this.editorShell.addView(this.body, new LinearLayout.LayoutParams(MODE_EDIT, -1, 1.0f));' in text
assert 'this.filesRailRequested = !this.filesRailRequested;' in text
assert 'getResources().getConfiguration().screenWidthDp < 840' in text
assert 'this.filesRailRequested = false;' in text
assert 'ScrollView scroll=buildFilesNavigatorView();' in text
assert 'captureActiveTab();\n        rebuildBody();\n        applyResponsiveChrome();' in text
assert len(list((a/'assets/splash').glob('*.webp')))==25
manifest=ET.parse(a/'AndroidManifest.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
assert (manifest.get(ns+'versionCode'),manifest.get(ns+'versionName')) in {('18','2.2.5'),('19','2.2.6-rc1'),('20','2.2.6-rc2'),('21','2.2.6-rc3'),('22','2.2.7-ux')}, 'Unexpected application release identity'
print('PASS static layout guards: scrolling Appearance, all nine themes, external Done, existing scrolling gallery, centered utility icons, compact Files single-line layout and current guide, configuration callbacks, 25 splash assets, allowlisted baseline/test-candidate identity')
