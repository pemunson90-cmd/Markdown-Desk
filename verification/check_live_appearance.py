from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
java=(root/'apps/MarkdownDesk/src/com/example/markdowndesk/MainActivity.java').read_text()
palette=(root/'apps/MarkdownDesk/src/com/example/markdowndesk/UiTheme.java').read_text()
view=java.split('private void showAppearance() {',1)[1].split('public /* synthetic */ void lambda$showAppearance$60',1)[0]
assert '.setCustomTitle(appearanceTitle)' in view
assert 'MainActivity.this.refreshAppearance(ui);' in view
assert 'appearanceScroll.addView(linearLayout, matchWrap())' in view
assert 'appearanceButton(ui,' in view
assert 'ui.themeButtons[i] = buttonCreateButton;' in view
assert 'appearanceDialog.show();' in view
assert 'this.buttons.remove(button)' in java
refresh=java.split('private void refreshAppearance(AppearanceUi ui) {',1)[1].split('private void showAppearance() {',1)[0]
for needle in ['ui.title.setText("Appearance · " + colors.name)', 'ui.title.setTextColor(colors.text)', 'ui.description.setTextColor(colors.muted)', 'ui.sourceLabel.setTextColor(colors.muted)', 'ui.readLabel.setTextColor(colors.muted)', 'button.setTextColor(colors.text)', 'selected ? colors.accentSoft : colors.surface', 'styleDialog(ui.dialog)', 'ui.linesButton.setText', 'ui.typewriterButton.setText', 'ui.splashButton.setText']:
    assert needle in refresh, needle

def luminance(h):
    rgb=[int(h[i:i+2],16)/255 for i in (1,3,5)]
    rgb=[c/12.92 if c <= .04045 else ((c+.055)/1.055)**2.4 for c in rgb]
    return sum(a*b for a,b in zip((.2126,.7152,.0722),rgb))
def contrast(a,b):
    l1,l2=luminance(a),luminance(b)
    return (max(l1,l2)+.05)/(min(l1,l2)+.05)
records=re.findall(r'new Palette\("([^"]+)", (true|false), (.*?)\);',palette)
assert len(records)==9
for name, dark, fields in records:
    a=re.findall(r'"(#[0-9A-F]{6})"',fields)
    assert len(a)==10,(name,a)
    canvas,surface,raised,text,muted,accent,soft,border,code,danger=a
    for label,fg,bg in [('title',text,raised),('button',text,surface),('selected',text,soft),('description',muted,raised)]:
        score=contrast(fg,bg)
        assert score>=4.5, (name,label,score)
manifest=ET.parse(root/'apps/MarkdownDesk/AndroidManifest.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
assert manifest.get(ns+'versionCode')=='18'
assert manifest.get(ns+'versionName')=='2.2.5'
print('PASS live Appearance regression guards: all 9 theme/title/control redraw paths, retained scroll, 4.5:1+ text contrast, v2.2.5 identity')
