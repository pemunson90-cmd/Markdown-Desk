package com.example.markdowndesk;

import android.content.Context;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Behavioral host fixtures compiling the actual app classes, without an Android runtime. */
public final class SessionRecoveryCheck {
    private static int assertions;
    private static int cases;
    private static final String TEXT = "  # café 😀 漢字\r\n\tsecond e\u0301 line\r\nend  \r\n";
    private static final String[] TAB_FIELDS = {
        "id", "uri", "displayName", "text", "baselineHash", "baselineModified", "utf8Bom",
        "readOnly", "editorLocked", "dirty", "selectionStart", "selectionEnd", "editorScrollY",
        "previewScrollY", "mode", "findQuery", "replaceQuery", "findCaseSensitive", "findWholeWord", "findRegex"
    };

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("session-recovery-");
        try {
            run("DocumentTab.copy all fields and independent mutations", () -> copy());
            run("EditorTools independent UTF-8 digest and counting vectors", () -> editorTools());
            run("12-tab session reopened Context and repeated saves", () -> sessionRoundtrip(root));
            run("Session bounds, malformed fields, empty and malformed state", () -> sessionMalformed(root));
            run("v2 recovery CRLF/BOM metadata/Unicode and nulls", () -> recoveryRoundtrip(root));
            run("legacy recovery pair byte fidelity and v2 precedence", () -> legacyRecovery(root));
            run("Recovery missing, partial and malformed state", () -> recoveryMalformed(root));
            run("Interrupted stage files preserve previous committed states", () -> interrupted(root));
            run("AtomicCommit replace failures preserve destination", () -> atomicFailures(root));
            run("Store stage open/write failures preserve committed checkpoints", () -> writeFailures(root));
            run("Store replacement failures preserve committed checkpoints", () -> storeReplaceFailures(root));
            run("Store sync failures preserve committed checkpoints", () -> syncFailures(root));
            run("Clear removes only the respective store-owned filenames", () -> clearOwnership(root));
            System.out.println("PASS session/recovery: " + cases + " cases, " + assertions + " assertions");
        } finally {
            // Delete only the unique fixture directory allocated by this run; do not follow symlinks.
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(java.util.Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }

    private static Map<String,Object> values(int i) {
        Map<String,Object> v = new LinkedHashMap<>();
        v.put("id", "fixture-tab-" + i); v.put("uri", i % 2 == 0 ? null : "content://fixture/doc/" + i);
        v.put("displayName", "café-" + i + ".md"); v.put("text", TEXT + i);
        v.put("baselineHash", i % 3 == 0 ? null : "fixture-baseline-" + i);
        v.put("baselineModified", 1700000000000L + i); v.put("utf8Bom", (i & 1) != 0);
        v.put("readOnly", (i & 1) != 0); v.put("editorLocked", (i & 2) != 0); v.put("dirty", (i & 4) != 0);
        v.put("selectionStart", i); v.put("selectionEnd", i + 2);
        v.put("editorScrollY", i * 17); v.put("previewScrollY", i * 29); v.put("mode", i % 3);
        v.put("findQuery", "é[" + i + "]"); v.put("replaceQuery", "$" + i + "\\replacement😀");
        v.put("findCaseSensitive", (i & 1) != 0); v.put("findWholeWord", (i & 2) != 0); v.put("findRegex", (i & 4) != 0);
        return v;
    }
    private static DocumentTab tab(int i) throws Exception {
        DocumentTab tab = new DocumentTab();
        for (Map.Entry<String,Object> e : values(i).entrySet()) DocumentTab.class.getField(e.getKey()).set(tab, e.getValue());
        return tab;
    }
    private static void assertTab(Map<String,Object> expected, DocumentTab actual) throws Exception {
        for (Map.Entry<String,Object> e : expected.entrySet()) eq(e.getValue(), DocumentTab.class.getField(e.getKey()).get(actual), e.getKey());
    }
    private static void copy() throws Exception {
        java.util.Set<String> fields = new java.util.TreeSet<>();
        for (Field f : DocumentTab.class.getFields()) if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) fields.add(f.getName());
        eq(new java.util.TreeSet<>(Arrays.asList(TAB_FIELDS)), fields, "copy fixture covers every public field");
        DocumentTab original = tab(7), copy = original.copy();
        yes(copy != original, "distinct tab object"); assertTab(values(7), copy);
        for (String name : TAB_FIELDS) {
            Field f = DocumentTab.class.getField(name); Object old = f.get(copy);
            Object changed = f.getType() == boolean.class ? !((Boolean)old)
                : f.getType() == int.class ? ((Integer)old) + 100
                : f.getType() == long.class ? ((Long)old) + 100L : "changed-" + name;
            f.set(copy, changed);
        }
        assertTab(values(7), original);
        DocumentTab nullable = tab(0); assertTab(values(0), nullable.copy());
        eq("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", new DocumentTab().baselineHash, "empty initial baseline");
    }
    private static void editorTools() {
        eq("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", EditorTools.sha256("abc".getBytes(StandardCharsets.UTF_8)), "standard SHA-256 vector");
        eq(0, EditorTools.lineCount(null), "null lines"); eq(0, EditorTools.lineCount(""), "empty lines");
        eq(3, EditorTools.lineCount("a\r\nb\n"), "CRLF and final line");
        eq(0, EditorTools.wordCount(null), "null words"); eq(0, EditorTools.wordCount("... 😀"), "punctuation words");
        eq(3, EditorTools.wordCount("one two 123"), "simple word count");
    }
    private static Context context(Path root, String name) throws IOException { return new Context(Files.createDirectories(root.resolve(name)).toFile()); }
    private static Path file(Context c, String name) { return c.getFilesDir().toPath().resolve(name); }
    private static void put(Context c, String name, String text) throws IOException { Files.write(file(c,name), text.getBytes(StandardCharsets.UTF_8)); }
    private static List<DocumentTab> tabs() throws Exception {
        List<DocumentTab> tabs = new ArrayList<>(); for (int i=0; i<12; i++) tabs.add(tab(i)); return tabs;
    }
    private static void sessionRoundtrip(Path root) throws Exception {
        Context c = context(root,"session"); List<DocumentTab> input = tabs();
        SessionStore.write(c,input,11,1700000000999L);
        // A new Context and no retained document objects: a host reopen, not process death.
        for (DocumentTab t : input) { t.text = "mutated after write"; t.id = "mutated"; }
        SessionStore.State state = SessionStore.read(new Context(c.getFilesDir()));
        eq(12,state.tabs.size(),"12 tabs"); eq(11,state.activeIndex,"active last tab"); eq(1700000000999L,state.timestamp,"timestamp");
        for (int i=0;i<12;i++) assertTab(values(i),state.tabs.get(i));
        state.tabs.get(0).text="local mutation"; assertTab(values(0), SessionStore.read(c).tabs.get(0));
        SessionStore.write(c,tabs(),4,1700000001000L); eq(4,SessionStore.read(c).activeIndex,"second active tab");
        yes(!Files.exists(file(c,"open-tabs.json.tmp")),"stage consumed");
    }
    private static void sessionMalformed(Path root) throws Exception {
        Context c=context(root,"session-malformed"); eq(null,SessionStore.read(c),"absent");
        for (String json : new String[]{"{}","{\"tabs\":[]}","{\"tabs\":[null,7,\"bad\"]}","{\"tabs\":false}"}) {
            put(c,"open-tabs.json",json); eq(null,SessionStore.read(c),"empty/non-tab state");
        }
        put(c,"open-tabs.json","{broken"); throwsIO(() -> SessionStore.read(c),"malformed JSON");
        put(c,"open-tabs.json",""); throwsIO(() -> SessionStore.read(c),"empty file");
        put(c,"open-tabs.json","{\"activeIndex\":999,\"timestamp\":\"bad\",\"tabs\":[null,{\"mode\":-9,\"editorScrollY\":-3,\"previewScrollY\":-2,\"selectionStart\":\"bad\",\"selectionEnd\":\"bad\",\"readOnly\":\"bad\",\"baselineModified\":\"bad\"},{\"mode\":99},{\"mode\":\"bad\"}]}");
        SessionStore.State s=SessionStore.read(c); eq(3,s.tabs.size(),"skip non-object"); eq(2,s.activeIndex,"upper active clamp");
        eq(file(c,"open-tabs.json").toFile().lastModified(),s.timestamp,"invalid timestamp fallback");
        DocumentTab t=s.tabs.get(0); eq(0,t.mode,"lower mode clamp"); eq(2,s.tabs.get(1).mode,"upper mode clamp"); eq(0,s.tabs.get(2).mode,"invalid mode default");
        eq(0,t.editorScrollY,"negative editor scroll clamp"); eq(0,t.previewScrollY,"negative preview scroll clamp");
        eq(0,t.selectionStart,"invalid selection default"); eq(0,t.selectionEnd,"invalid end default");
        eq(false,t.readOnly,"invalid boolean default"); eq(-1L,t.baselineModified,"invalid baseline time default");
        eq(null,t.uri,"missing URI"); eq(null,t.baselineHash,"missing hash"); eq("Document.md",t.displayName,"missing display name");
        eq("",t.text,"missing text"); yes(t.id != null && !t.id.isEmpty(),"generated missing ID");
        put(c,"open-tabs.json","{\"activeIndex\":-5,\"tabs\":[{\"selectionStart\":3}]}");
        s=SessionStore.read(c); eq(0,s.activeIndex,"lower active clamp"); eq(3,s.tabs.get(0).selectionEnd,"missing selection end uses start");
        put(c,"open-tabs.json","{\"activeIndex\":\"bad\",\"tabs\":[{}]}"); eq(0,SessionStore.read(c).activeIndex,"invalid active default");
    }
    private static RecoveryStore.Draft draft() {
        RecoveryStore.Draft d=new RecoveryStore.Draft(); d.text=TEXT; d.displayName="Recovered 😀.md"; d.uri="content://fixture/original";
        d.baselineHash="independent-baseline"; d.baselineModified=1700000001234L; d.utf8Bom=true; d.readOnly=true;
        d.selectionStart=3; d.selectionEnd=9; d.timestamp=1700000005678L; return d;
    }
    private static void assertDraft(RecoveryStore.Draft d) {
        eq(TEXT,d.text,"recovery text"); bytes(TEXT.getBytes(StandardCharsets.UTF_8), d.text.getBytes(StandardCharsets.UTF_8),"UTF-8 byte fidelity");
        eq("Recovered 😀.md",d.displayName,"recovery name"); eq("content://fixture/original",d.uri,"recovery URI");
        eq("independent-baseline",d.baselineHash,"recovery baseline"); eq(1700000001234L,d.baselineModified,"baseline modified");
        eq(true,d.utf8Bom,"BOM metadata"); eq(true,d.readOnly,"readonly"); eq(3,d.selectionStart,"selection start"); eq(9,d.selectionEnd,"selection end"); eq(1700000005678L,d.timestamp,"draft timestamp");
    }
    private static void recoveryRoundtrip(Path root) throws Exception {
        Context c=context(root,"recovery"); RecoveryStore.write(c,draft()); assertDraft(RecoveryStore.read(new Context(c.getFilesDir())));
        JSONObject envelope=new JSONObject(new String(Files.readAllBytes(file(c,"recovery-v2.json")),StandardCharsets.UTF_8));
        eq(2,envelope.getInt("version"),"v2 envelope"); eq(TEXT,envelope.getString("text"),"envelope decoded text");
        yes(!Files.exists(file(c,"recovery-v2.json.tmp")),"recovery stage consumed");
        RecoveryStore.Draft d=draft(); d.uri=null; d.baselineHash=null; d.text="\uFEFF"+TEXT; d.readOnly=false; RecoveryStore.write(c,d);
        RecoveryStore.Draft got=RecoveryStore.read(c); eq(null,got.uri,"null URI"); eq(null,got.baselineHash,"null hash"); eq(false,got.readOnly,"readwrite");
        bytes(("\uFEFF"+TEXT).getBytes(StandardCharsets.UTF_8),got.text.getBytes(StandardCharsets.UTF_8),"literal BOM retained");
    }
    private static String legacyMeta() {
        return "{\"displayName\":\"Recovered 😀.md\",\"uri\":\"content://fixture/original\",\"baselineHash\":\"independent-baseline\",\"baselineModified\":1700000001234,\"utf8Bom\":true,\"readOnly\":true,\"selectionStart\":3,\"selectionEnd\":9,\"timestamp\":1700000005678}";
    }
    private static void legacyRecovery(Path root) throws Exception {
        Context c=context(root,"legacy"); put(c,"recovery-draft.md",TEXT); put(c,"recovery-meta.json",legacyMeta()); assertDraft(RecoveryStore.read(new Context(c.getFilesDir())));
        put(c,"recovery-draft.md","\uFEFF"+TEXT); bytes(("\uFEFF"+TEXT).getBytes(StandardCharsets.UTF_8),RecoveryStore.read(c).text.getBytes(StandardCharsets.UTF_8),"legacy literal BOM bytes");
        RecoveryStore.write(c,draft()); put(c,"recovery-draft.md","stale legacy"); put(c,"recovery-meta.json","malformed stale legacy"); assertDraft(RecoveryStore.read(c));
    }
    private static void recoveryMalformed(Path root) throws Exception {
        Context c=context(root,"recovery-malformed"); eq(null,RecoveryStore.read(c),"missing recovery");
        put(c,"recovery-draft.md","orphan"); eq(null,RecoveryStore.read(c),"orphan content"); Files.delete(file(c,"recovery-draft.md"));
        put(c,"recovery-meta.json","{}"); eq(null,RecoveryStore.read(c),"orphan metadata"); put(c,"recovery-draft.md","");
        RecoveryStore.Draft d=RecoveryStore.read(c); eq("",d.text,"empty legacy draft"); eq("Recovered draft.md",d.displayName,"default recovery name");
        eq(false,d.utf8Bom,"default BOM flag"); eq(-1L,d.baselineModified,"default baseline time"); eq(file(c,"recovery-draft.md").toFile().lastModified(),d.timestamp,"legacy timestamp fallback");
        put(c,"recovery-meta.json","bad"); throwsIO(() -> RecoveryStore.read(c),"legacy malformed metadata");
        for(String bad : new String[]{"", "{broken", "{}"}) { put(c,"recovery-v2.json",bad); throwsIO(() -> RecoveryStore.read(c),"malformed/missing text v2"); }
        put(c,"recovery-v2.json","{\"text\":\"\",\"selectionStart\":4,\"baselineModified\":\"bad\",\"readOnly\":\"bad\"}");
        d=RecoveryStore.read(c); eq("",d.text,"empty v2 draft"); eq(4,d.selectionEnd,"default end uses start"); eq(-1L,d.baselineModified,"invalid baseline time"); eq(false,d.readOnly,"invalid recovery boolean");
        eq(file(c,"recovery-v2.json").toFile().lastModified(),d.timestamp,"v2 timestamp fallback");
    }
    private static void interrupted(Path root) throws Exception {
        Context c=context(root,"interrupted"); SessionStore.write(c,tabs(),5,99L); RecoveryStore.write(c,draft());
        byte[] session=Files.readAllBytes(file(c,"open-tabs.json")), recovery=Files.readAllBytes(file(c,"recovery-v2.json"));
        put(c,"open-tabs.json.tmp","{partial"); put(c,"recovery-v2.json.tmp","{partial");
        put(c,"recovery-draft.md.tmp","new incomplete content"); put(c,"recovery-meta.json.tmp","{partial");
        SessionStore.State s=SessionStore.read(new Context(c.getFilesDir())); eq(5,s.activeIndex,"committed active index"); assertTab(values(0),s.tabs.get(0)); assertDraft(RecoveryStore.read(new Context(c.getFilesDir())));
        bytes(session,Files.readAllBytes(file(c,"open-tabs.json")),"session commit untouched"); bytes(recovery,Files.readAllBytes(file(c,"recovery-v2.json")),"recovery commit untouched");
        Context only=context(root,"stage-only"); put(only,"open-tabs.json.tmp","{}"); put(only,"recovery-v2.json.tmp","{}"); eq(null,SessionStore.read(only),"stage-only session ignored"); eq(null,RecoveryStore.read(only),"stage-only recovery ignored");
        SessionStore.write(c,tabs(),2,100L); RecoveryStore.write(c,draft()); eq(2,SessionStore.read(c).activeIndex,"retry replaces interrupted stage"); assertDraft(RecoveryStore.read(c));
        Context legacy=context(root,"legacy-interrupted"); put(legacy,"recovery-draft.md",TEXT); put(legacy,"recovery-meta.json",legacyMeta());
        put(legacy,"recovery-draft.md.tmp","partial new content"); put(legacy,"recovery-meta.json.tmp","{partial"); put(legacy,"recovery-v2.json.tmp","{partial");
        assertDraft(RecoveryStore.read(new Context(legacy.getFilesDir())));
    }
    private static void atomicFailures(Path root) throws Exception {
        Context c=context(root,"atomic"); Path target=file(c,"committed"), stage=file(c,"stage"); Files.write(target,"prior".getBytes(StandardCharsets.UTF_8));
        throwsIO(() -> AtomicCommit.replace(stage.toFile(),target.toFile()),"missing staged file"); eq("prior",Files.readString(target),"prior after missing stage");
        Path elsewhere=Files.createDirectories(file(c,"elsewhere")).resolve("stage"); Files.writeString(elsewhere,"new");
        throwsIO(() -> AtomicCommit.replace(elsewhere.toFile(),target.toFile()),"different parent refused"); eq("prior",Files.readString(target),"prior after parent rejection"); eq("new",Files.readString(elsewhere),"rejected stage kept");
        Path directory=Files.createDirectories(file(c,"directory")); Files.writeString(directory.resolve("child"),"sentinel"); Files.writeString(stage,"next");
        throwsIO(() -> AtomicCommit.replace(stage.toFile(),directory.toFile()),"nonempty destination directory"); eq("sentinel",Files.readString(directory.resolve("child")),"directory retained");
        AtomicCommit.replace(stage.toFile(),target.toFile()); eq("next",Files.readString(target),"atomic successful replacement"); yes(!Files.exists(stage),"successful stage consumed");
    }
    private static void storeReplaceFailures(Path root) throws Exception {
        Context c=context(root,"replace-failure"); SessionStore.write(c,tabs(),6,88L); RecoveryStore.write(c,draft());
        byte[] session=Files.readAllBytes(file(c,"open-tabs.json")),recovery=Files.readAllBytes(file(c,"recovery-v2.json"));
        Path nested=Files.createDirectories(file(c,"different-parent"));
        // Stage symlinks make writes and sync succeed; canonical-parent validation then fails.
        Files.createSymbolicLink(file(c,"open-tabs.json.tmp"),nested.resolve("session-stage"));
        Files.createSymbolicLink(file(c,"recovery-v2.json.tmp"),nested.resolve("recovery-stage"));
        throwsIO(() -> SessionStore.write(c,tabs(),0,0L),"session replace rejected");
        RecoveryStore.Draft changed=draft(); changed.text="new unsaved content"; throwsIO(() -> RecoveryStore.write(c,changed),"recovery replace rejected");
        bytes(session,Files.readAllBytes(file(c,"open-tabs.json")),"session preserved after failed replace"); bytes(recovery,Files.readAllBytes(file(c,"recovery-v2.json")),"recovery preserved after failed replace");
        eq(6,SessionStore.read(c).activeIndex,"session remains readable"); assertDraft(RecoveryStore.read(c));
        Files.delete(file(c,"open-tabs.json.tmp")); Files.delete(file(c,"recovery-v2.json.tmp"));
        SessionStore.write(c,tabs(),3,90L); RecoveryStore.write(c,changed); eq(3,SessionStore.read(c).activeIndex,"session retry"); eq("new unsaved content",RecoveryStore.read(c).text,"recovery retry");
    }
    private static void writeFailures(Path root) throws Exception {
        Context c=context(root,"write-failure"); SessionStore.write(c,tabs(),7,124L); RecoveryStore.write(c,draft());
        byte[] session=Files.readAllBytes(file(c,"open-tabs.json")),recovery=Files.readAllBytes(file(c,"recovery-v2.json"));
        // A directory at the staged filename reliably rejects opening without chmod/root assumptions.
        Files.createDirectory(file(c,"open-tabs.json.tmp")); Files.createDirectory(file(c,"recovery-v2.json.tmp"));
        throwsIO(() -> SessionStore.write(c,tabs(),0,0L),"session stage open failure"); throwsIO(() -> RecoveryStore.write(c,draft()),"recovery stage open failure");
        bytes(session,Files.readAllBytes(file(c,"open-tabs.json")),"session retained after open failure"); bytes(recovery,Files.readAllBytes(file(c,"recovery-v2.json")),"recovery retained after open failure");
        Files.delete(file(c,"open-tabs.json.tmp")); Files.delete(file(c,"recovery-v2.json.tmp"));
        yes(Files.exists(Path.of("/dev/full")),"Linux /dev/full fixture available");
        Files.createSymbolicLink(file(c,"open-tabs.json.tmp"),Path.of("/dev/full")); Files.createSymbolicLink(file(c,"recovery-v2.json.tmp"),Path.of("/dev/full"));
        throwsIO(() -> SessionStore.write(c,tabs(),0,0L),"session stage ENOSPC write failure"); throwsIO(() -> RecoveryStore.write(c,draft()),"recovery stage ENOSPC write failure");
        bytes(session,Files.readAllBytes(file(c,"open-tabs.json")),"session retained after write failure"); bytes(recovery,Files.readAllBytes(file(c,"recovery-v2.json")),"recovery retained after write failure");
        eq(7,SessionStore.read(c).activeIndex,"session remains readable after write failures"); assertDraft(RecoveryStore.read(c));
    }
    private static void syncFailures(Path root) throws Exception {
        // Linux /dev/null accepts writes but FileDescriptor.sync fails (EINVAL).
        // No production failure-injection hook or privilege-dependent chmod behavior.
        yes(Files.exists(Path.of("/dev/null")),"Linux /dev/null fixture available");
        Context c=context(root,"sync-failure"); SessionStore.write(c,tabs(),8,123L); RecoveryStore.write(c,draft());
        byte[] session=Files.readAllBytes(file(c,"open-tabs.json")),recovery=Files.readAllBytes(file(c,"recovery-v2.json"));
        Files.createSymbolicLink(file(c,"open-tabs.json.tmp"),Path.of("/dev/null")); Files.createSymbolicLink(file(c,"recovery-v2.json.tmp"),Path.of("/dev/null"));
        throwsSync(() -> SessionStore.write(c,tabs(),0,0L),"session sync failure"); throwsSync(() -> RecoveryStore.write(c,draft()),"recovery sync failure");
        bytes(session,Files.readAllBytes(file(c,"open-tabs.json")),"session retained after sync failure"); bytes(recovery,Files.readAllBytes(file(c,"recovery-v2.json")),"recovery retained after sync failure");
        eq(8,SessionStore.read(c).activeIndex,"session read after sync failure"); assertDraft(RecoveryStore.read(c));
    }
    private static void clearOwnership(Path root) throws Exception {
        Context c=context(root,"clear"); SessionStore.write(c,tabs(),0,0L); RecoveryStore.write(c,draft()); put(c,"user-document.md","user sentinel");
        put(c,"open-tabs.json.tmp","stage"); put(c,"recovery-v2.json.tmp","stage"); put(c,"recovery-draft.md",TEXT); put(c,"recovery-meta.json",legacyMeta());
        put(c,"recovery-draft.md.tmp","stage"); put(c,"recovery-meta.json.tmp","stage");
        SessionStore.clear(c); eq(null,SessionStore.read(c),"session cleared"); yes(!Files.exists(file(c,"open-tabs.json.tmp")),"session stage cleared"); assertDraft(RecoveryStore.read(c));
        yes(Files.exists(file(c,"recovery-v2.json.tmp")),"session clear keeps recovery stage");
        SessionStore.write(c,tabs(),1,1L); put(c,"open-tabs.json.tmp","stage"); RecoveryStore.clear(c); eq(null,RecoveryStore.read(c),"recovery cleared");
        for (String name : new String[]{"recovery-v2.json","recovery-v2.json.tmp","recovery-draft.md","recovery-meta.json","recovery-draft.md.tmp","recovery-meta.json.tmp"}) yes(!Files.exists(file(c,name)),"cleared "+name);
        eq(1,SessionStore.read(c).activeIndex,"recovery clear keeps session"); eq("stage",Files.readString(file(c,"open-tabs.json.tmp")),"recovery clear keeps session stage");
        eq("user sentinel",Files.readString(file(c,"user-document.md")),"user file preserved"); SessionStore.clear(c); SessionStore.clear(c); RecoveryStore.clear(c);
    }
    private interface Action { void run() throws Exception; }
    private static void run(String name, Action action) throws Exception { action.run(); cases++; System.out.println("PASS " + name); }
    private static void eq(Object expected,Object actual,String message) {
        assertions++; if (!java.util.Objects.equals(expected,actual)) throw new AssertionError(message+": expected="+expected+", actual="+actual);
    }
    private static void yes(boolean actual,String message) { eq(true,actual,message); }
    private static void bytes(byte[] expected,byte[] actual,String message) { yes(Arrays.equals(expected,actual),message); }
    private static void throwsIO(Action action,String message) throws Exception {
        assertions++; try { action.run(); } catch(IOException expected) { return; } throw new AssertionError(message+": IOException expected");
    }
    private static void throwsSync(Action action,String message) throws Exception {
        assertions++; try { action.run(); } catch(java.io.SyncFailedException expected) { return; } throw new AssertionError(message+": SyncFailedException expected");
    }
}
