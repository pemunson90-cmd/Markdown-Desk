public final class LabelBehaviorTest {
    static int checks, failures;
    static void check(String name, boolean condition) {
        checks++;
        if (!condition) { failures++; System.err.println("FAIL " + name); }
    }
    public static void main(String[] args) {
        LabelHarness harness=new LabelHarness();
        String[] topText={"M", "?", "•••", "SAVE"};
        String[] topNames={"Workspace", "Guide and shortcuts", "More actions", "Save document"};
        String[] pillText={"＋", "B", "A−", "A+"};
        String[] pillNames={"New document", "Bold", "Decrease reading size", "Increase reading size"};
        int[] clicked={0};
        LabelHarness.View.OnClickListener listener=view -> clicked[0]++;
        for (int i=0; i<topText.length; i++) {
            LabelHarness.Button top=harness.top(topText[i], topNames[i], listener);
            LabelHarness.Button pill=harness.pill(pillText[i], pillNames[i], listener);
            check("top accessible name " + topText[i], topNames[i].equals(top.description));
            check("pill accessible name " + pillText[i], pillNames[i].equals(pill.description));
            check("top text/tooltip retained " + topText[i], topText[i].equals(top.text) && topNames[i].equals(top.tooltip));
            check("pill text/tooltip retained " + pillText[i], pillText[i].equals(pill.text) && pillNames[i].equals(pill.tooltip));
            check("helper listener retained " + i, top.listener==listener && pill.listener==listener);
            top.listener.onClick(top); pill.listener.onClick(pill);
        }
        check("helper clicks forwarded", clicked[0]==8);
        harness.buildSizeControls();
        check("four dialog-owned controls", harness.ui.buttons.size()==4 && harness.sourceRow.children.size()==2 && harness.readRow.children.size()==2);
        String[] names={"Decrease source text size", "Increase source text size", "Decrease reading text size", "Increase reading text size"};
        for (int i=0; i<names.length; i++) {
            LabelHarness.Button button=harness.ui.buttons.get(i);
            check("Appearance accessible name " + names[i], names[i].equals(button.description));
            check("Appearance tooltip " + names[i], names[i].equals(button.tooltip));
            check("Appearance visible glyph " + i, (i%2==0?"A−":"A+").equals(button.text));
            check("dialog ownership " + i, !harness.buttons.contains(button));
            button.listener.onClick(button);
            check("Appearance callback route " + i, i<2 ? harness.sourceChanges==i+1 && harness.readingChanges==0 : harness.sourceChanges==2 && harness.readingChanges==i-1);
        }
        check("source and reading values retained after +/-", harness.fontSp==18 && harness.previewFontSp==18);
        if (failures!=0) throw new AssertionError(failures + " accessibility label assertions failed");
        System.out.println("PASS " + checks + " host assertions on actual label helpers and four AST-extracted Appearance control statements");
    }
}
