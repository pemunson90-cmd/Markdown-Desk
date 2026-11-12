from pathlib import Path
from check_current_source import current_release_pair
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
a=root/'apps/MarkdownDesk';text=(a/'src/com/example/markdowndesk/MainActivity.java').read_text()
method=text.split('private void showAppearanceSection(String section) {',1)[1].split('public /* synthetic */ void lambda$showAppearance$60',1)[0]
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
assert 'this.topBar.addView(this.findButton' in text
assert 'Tap the M button' not in text
assert 'this.editorShell.addView(this.body, new LinearLayout.LayoutParams(MODE_EDIT, -1, 1.0f));' in text
assert 'this.filesRailRequested = !this.filesRailRequested;' in text
assert 'getResources().getConfiguration().screenWidthDp < 840' in text
assert 'this.filesRailRequested = false;' in text
assert 'showFilesDialog("Files",buildFilesNavigatorView()' in text
assert 'heightPixels-dp(36)' not in text
assert 'this.formatScroll.setVisibility(View.GONE);' in text
assert 'this.readToolsScroll.setVisibility(View.GONE);' in text
assert 'this.tabs.size()>1?View.VISIBLE:View.GONE' in text
assert 'showFilesCollection(true)' in text and 'showFilesCollection(false)' in text
assert 'captureActiveTab();\n        rebuildBody();\n        applyResponsiveChrome();' in text
assert len(list((a/'assets/splash').glob('*.webp')))==25
manifest=ET.parse(a/'AndroidManifest.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
assert (manifest.get(ns+'versionCode'),manifest.get(ns+'versionName')) in {('18','2.2.5'),('19','2.2.6-rc1'),('20','2.2.6-rc2'),('21','2.2.6-rc3'),('22','2.2.7-ux'),('23','2.2.8-tabfix1'),('24','2.2.9-tabfix2'),('25','2.2.10-downloads1'),('26','2.2.11-downloads-contrast1'),('27','2.2.12-native-picker1'),('28','2.3.0-navigation1'),('29','2.3.1-readerlinks1'),('30','2.3.2-emaillinks1'),('43','2.5.1-beta4')} or (manifest.get(ns+'versionCode'),manifest.get(ns+'versionName')) == current_release_pair(root), 'Unexpected application release identity'
print('PASS static layout guards: scrolling Appearance, all nine themes, external Done, existing scrolling gallery, centered utility icons, compact Files single-line layout and current guide, configuration callbacks, 25 splash assets, allowlisted baseline/test-candidate identity')
