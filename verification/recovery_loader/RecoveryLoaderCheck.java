package com.example.markdowndesk;

import java.nio.file.*;
import java.util.*;

public final class RecoveryLoaderCheck {
    private static int assertions, cases, failures;
    private static Path root;
    public static void main(String[] args) throws Exception {
        root=Files.createTempDirectory("recovery-loader-");
        try {
            run("legacy recovery replacement retries protection after storage repair", () -> protectionRetry());
            run("startup protection is immutable and deduplicates across restart", () -> deduplicate());
            run("protection failure blocks legacy replace and clear", () -> protectionFailure());
            run("valid session, missing recovery", () -> validSession());
            run("valid session, malformed recovery", () -> invalidRecovery());
            run("both missing", () -> bothMissing());
            run("missing session, valid recovery", () -> missingSessionValidRecovery());
            run("both malformed report both read failures", () -> bothMalformed());
            run("malformed session, valid v2 recovery retained with error", () -> invalidSessionValidRecovery());
            run("newer matching recovery merges and preserves unrelated tab fields", () -> newerMerge());
            run("older recovery preserves session", () -> olderRecovery());
            run("newer unmatched recovery appends without overwriting prior tab", () -> appendRecovery());
            run("full 12-tab session retains every tab with unmatched draft", () -> fullSession());
            System.out.println((failures==0 ? "PASS" : "FAIL")+" recovery-loader: "+cases+" cases, "+assertions+" assertions, "+failures+" failures");
            if(failures!=0) throw new AssertionError("Recovery loader expectations failed: "+failures);
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(root)) {
                for(Path p:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(p);
            }
        }
    }
    private static MainActivity fixture(String name) throws Exception { return new MainActivity(Files.createDirectories(root.resolve(name)).toFile()); }
    private static void put(MainActivity a,String name,String content) throws Exception { Files.writeString(a.getFilesDir().toPath().resolve(name),content); }
    private static DocumentTab tab(int i) {
        DocumentTab t=new DocumentTab(); t.id="tab-"+i; t.uri="content://fixture/"+i; t.text="session text "+i; t.displayName="session-"+i+".md";
        t.editorLocked=true; t.editorScrollY=37+i; t.previewScrollY=83+i; t.findQuery="query-"+i; t.replaceQuery="replacement-"+i;
        t.findRegex=true; t.findCaseSensitive=true; t.findWholeWord=true; t.mode=2; return t;
    }
    private static RecoveryStore.Draft draft(long timestamp,String uri) {
        RecoveryStore.Draft d=new RecoveryStore.Draft(); d.text="  café 😀\r\nrecovered draft  \r\n"; d.uri=uri; d.displayName="Recovered.md";
        d.baselineHash="draft-baseline"; d.baselineModified=77L; d.utf8Bom=true; d.readOnly=true; d.selectionStart=4; d.selectionEnd=8; d.timestamp=timestamp; return d;
    }
    private static void load(MainActivity a) {
        MainActivity.Intent intent=new MainActivity.Intent(); a.lambda$restoreSessionOrRecovery$42(intent);
        eq(0,a.deliveries,"callback asynchronous"); eq(1,a.main.queue.size(),"one posted callback");
        a.main.drain(); eq(1,a.deliveries,"one delivered callback"); eq(intent,a.deliveredIntent,"same intent forwarded");
    }
    private static void protectionRetry() throws Exception {
        MainActivity a=fixture("retry-protection");RecoveryStore.write(a,draft(200L,"content://fixture/old"));put(a,"protected_saves","blocking file");load(a);
        Files.delete(a.getFilesDir().toPath().resolve("protected_saves"));RecoveryStore.Draft replacement=draft(300L,"content://replacement");replacement.text="newer recovery";
        a.lambda$writeRecoveryNow$46(replacement);a.main.drain();eq("newer recovery",RecoveryStore.read(a).text,"storage repair permits recovery replacement");
        eq(1,a.getProtectionStore().snapshots().size(),"old draft protected before replacement");eq(true,a.legacyRecoveryProtected,"retry succeeded");
    }
    private static void deduplicate() throws Exception {
        MainActivity a=fixture("deduplicate");RecoveryStore.write(a,draft(200L,"content://fixture/old"));load(a);
        eq(1,a.getProtectionStore().snapshots().size(),"first protection");
        MainActivity b=new MainActivity(a.getFilesDir());load(b);eq(1,b.getProtectionStore().snapshots().size(),"no startup duplicate");
        eq(true,b.legacyRecoveryProtected,"legacy protected before UI callback");
        b.lambda$clearRecoveryAsync$47();eq(null,RecoveryStore.read(b),"protected legacy slot can clear");
        eq(1,b.getProtectionStore().snapshots().size(),"immutable protection survives clear");
    }
    private static void protectionFailure() throws Exception {
        MainActivity a=fixture("blocked-protection");RecoveryStore.Draft old=draft(200L,"content://fixture/old");RecoveryStore.write(a,old);
        put(a,"protected_saves","blocking file");load(a);
        eq(false,a.legacyRecoveryProtected,"failed protection never permits slot mutation");yes(a.deliveredError!=null,"actionable protection error delivered");
        a.lambda$clearRecoveryAsync$47();assertDraft(RecoveryStore.read(a));
        RecoveryStore.Draft replacement=draft(300L,"content://replacement");replacement.text="different latest edit";
        a.lambda$writeRecoveryNow$46(replacement);a.main.drain();assertDraft(RecoveryStore.read(a));
        eq(2,a.errors.size(),"blocked clear and replacement report recovery preservation");
    }
    private static void validSession() throws Exception {
        MainActivity a=fixture("valid"); SessionStore.write(a,Arrays.asList(tab(0)),0,100L); load(a);
        eq(null,a.deliveredError,"no error"); eq(null,a.deliveredDraft,"no recovery"); eq("session text 0",a.deliveredState.tabs.get(0).text,"session retained");
    }
    private static void invalidRecovery() throws Exception {
        MainActivity a=fixture("invalid-recovery"); SessionStore.write(a,Arrays.asList(tab(0)),0,100L); put(a,"recovery-v2.json","{broken"); load(a);
        yes(a.deliveredError instanceof java.io.IOException,"recovery read error"); eq(null,a.deliveredDraft,"invalid recovery absent"); eq("session text 0",a.deliveredState.tabs.get(0).text,"valid session retained");
    }
    private static void bothMissing() throws Exception {
        MainActivity a=fixture("missing"); load(a); eq(null,a.deliveredState,"no session"); eq(null,a.deliveredDraft,"no recovery"); eq(null,a.deliveredError,"no error");
    }
    private static void bothMalformed() throws Exception {
        MainActivity a=fixture("both-malformed"); put(a,"open-tabs.json","{broken"); put(a,"recovery-v2.json","{broken"); load(a);
        eq(null,a.deliveredState,"invalid session absent"); eq(null,a.deliveredDraft,"invalid recovery absent"); yes(a.deliveredError instanceof java.io.IOException,"read error delivered");
        // Independent failures should retain the first and attach the second; neither read is skipped.
        eq(1,a.deliveredError.getSuppressed().length,"both read failures retained");
    }
    private static void missingSessionValidRecovery() throws Exception {
        MainActivity a=fixture("missing-session"); RecoveryStore.write(a,draft(200L,"content://fixture/0")); load(a);
        eq(null,a.deliveredState,"missing session absent"); eq(null,a.deliveredError,"missing session is not an error"); yes(a.deliveredDraft!=null,"valid recovery delivered"); assertDraft(a.deliveredDraft);
    }
    private static void invalidSessionValidRecovery() throws Exception {
        MainActivity a=fixture("invalid-session"); put(a,"open-tabs.json","{broken"); RecoveryStore.write(a,draft(200L,"content://fixture/0")); load(a);
        eq(null,a.deliveredState,"invalid session absent"); yes(a.deliveredError instanceof java.io.IOException,"session read error retained");
        yes(a.deliveredDraft!=null,"valid recovery must survive malformed session"); assertDraft(a.deliveredDraft);
        eq("{broken",Files.readString(a.getFilesDir().toPath().resolve("open-tabs.json")),"loader does not rewrite malformed checkpoint");
        assertDraft(RecoveryStore.read(a));
        eq(1,a.getProtectionStore().snapshots().size(),"malformed-session orphan protected");
    }
    private static void assertDraft(RecoveryStore.Draft d) {
        eq("  café 😀\r\nrecovered draft  \r\n",d.text,"draft text intact"); eq("Recovered.md",d.displayName,"draft name");
        eq("draft-baseline",d.baselineHash,"draft baseline"); eq(77L,d.baselineModified,"draft baseline time"); eq(true,d.utf8Bom,"draft BOM");
        eq(true,d.readOnly,"draft readonly"); eq(4,d.selectionStart,"draft selection start"); eq(8,d.selectionEnd,"draft selection end");
    }
    private static void newerMerge() throws Exception {
        MainActivity a=fixture("merge"); SessionStore.write(a,Arrays.asList(tab(0),tab(1)),1,100L); RecoveryStore.write(a,draft(200L,"content://fixture/0")); load(a);
        eq(null,a.deliveredError,"merge no error"); eq(2,a.deliveredState.tabs.size(),"no duplicate tab"); eq(0,a.deliveredState.activeIndex,"recovered tab active"); eq(200L,a.deliveredState.timestamp,"draft timestamp");
        DocumentTab t=a.deliveredState.tabs.get(0); eq("  café 😀\r\nrecovered draft  \r\n",t.text,"newer text"); eq("Recovered.md",t.displayName,"merged name");
        eq("content://fixture/0",t.uri,"merged URI"); eq("draft-baseline",t.baselineHash,"merged baseline"); eq(77L,t.baselineModified,"merged baseline time");
        eq(true,t.utf8Bom,"merged BOM"); eq(true,t.readOnly,"merged readonly"); eq(true,t.dirty,"merged draft dirty"); eq(4,t.selectionStart,"merged selection start"); eq(8,t.selectionEnd,"merged selection end"); eq(0,t.mode,"merged editing mode");
        eq("tab-0",t.id,"stable tab ID"); eq(true,t.editorLocked,"existing lock retained"); eq(37,t.editorScrollY,"editor scroll retained"); eq(83,t.previewScrollY,"preview scroll retained");
        eq("query-0",t.findQuery,"find query retained"); eq("replacement-0",t.replaceQuery,"replacement retained"); eq(true,t.findRegex,"regex retained"); eq(true,t.findCaseSensitive,"case flag retained"); eq(true,t.findWholeWord,"whole word retained");
        eq("session text 1",a.deliveredState.tabs.get(1).text,"other tab text retained"); eq(2,a.deliveredState.tabs.get(1).mode,"other tab mode retained");
        eq("session text 0",SessionStore.read(a).tabs.get(0).text,"merge does not write stored session");
    }
    private static void olderRecovery() throws Exception {
        MainActivity a=fixture("older"); SessionStore.write(a,Arrays.asList(tab(0)),0,300L); RecoveryStore.write(a,draft(200L,"content://fixture/0")); load(a);
        eq("session text 0",a.deliveredState.tabs.get(0).text,"older draft does not replace"); eq(300L,a.deliveredState.timestamp,"session timestamp retained"); yes(a.deliveredDraft!=null,"older draft still forwarded");
    }
    private static void appendRecovery() throws Exception {
        MainActivity a=fixture("append"); SessionStore.write(a,Arrays.asList(tab(0)),0,100L); RecoveryStore.write(a,draft(200L,"content://fixture/new")); load(a);
        eq(2,a.deliveredState.tabs.size(),"new recovery appended"); eq(1,a.deliveredState.activeIndex,"new recovery active"); eq("session text 0",a.deliveredState.tabs.get(0).text,"prior text retained"); eq("content://fixture/new",a.deliveredState.tabs.get(1).uri,"appended URI");
    }
    private static void fullSession() throws Exception {
        MainActivity a=fixture("full"); List<DocumentTab> tabs=new ArrayList<>(); for(int i=0;i<12;i++)tabs.add(tab(i)); SessionStore.write(a,tabs,4,100L); RecoveryStore.write(a,draft(200L,"content://fixture/new")); load(a);
        eq(12,a.deliveredState.tabs.size(),"12 tabs preserved"); eq(4,a.deliveredState.activeIndex,"active index retained"); eq(100L,a.deliveredState.timestamp,"no merge timestamp");
        for(int i=0;i<12;i++)eq("session text "+i,a.deliveredState.tabs.get(i).text,"full-session tab "+i+" preserved"); yes(a.deliveredDraft!=null,"unmerged recovery still forwarded");
        eq(1,a.getProtectionStore().snapshots().size(),"full-session orphan protected");
    }
    private interface Action { void run() throws Exception; }
    private static void run(String name,Action action) throws Exception {
        cases++; try { action.run(); System.out.println("PASS "+name); } catch(AssertionError e) { failures++; System.out.println("FAIL "+name+": "+e.getMessage()); }
    }
    private static void eq(Object expected,Object actual,String name) { assertions++; if(!Objects.equals(expected,actual))throw new AssertionError(name+": expected="+expected+", actual="+actual); }
    private static void yes(boolean actual,String name) { eq(true,actual,name); }
}
