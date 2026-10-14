package com.example.markdowndesk;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class SaveLifecycleCheck {
    static int cases,assertions,failures;
    static final String ORIGINAL="content://fixture/original",DESTINATION="content://fixture/save-as",OTHER="content://fixture/other";
    public static void main(String[] args)throws Exception{
        boolean safety=Arrays.asList(args).contains("--safety");
        run("normal save commits verified version",()->normal());
        run("normal Save As success adopts verified destination",()->saveAsSuccess());
        run("untitled Save As succeeds without current URI",()->untitledSaveAs());
        run("newer active edits remain dirty and retain recovery",()->newerEdits());
        run("switched-tab callback updates only saving tab",()->switchedTab());
        run("Save As newer edits remain dirty",()->saveAsNewerEdits());
        run("Save As switched-tab callback preserves active identity",()->saveAsSwitchedTab());
        run("closed-tab callback does not resurrect original tab",()->closedTab());
        run("provider write and readback failures retain dirty content",()->failedNormal());
        run("cancelled Save As leaves origin unchanged",()->cancelled());
        if(safety){
            run("SAFETY Save As does not adopt identity before verification",()->beforeVerification());
            run("SAFETY provider-write-failed Save As retains original identity and baseline",()->failedSaveAs("write"));
            run("SAFETY readback-mismatched Save As retains original identity and baseline",()->failedSaveAs("mismatch"));
            run("SAFETY removed Save As origin never retargets active tab",()->removedOrigin());
            run("SAFETY busy picker result never adopts destination",()->busyPickerResult());
            run("SAFETY closed Save As tab clears stale continuation",()->closedSaveAsContinuation());
        }
        System.out.println((failures==0?"PASS":"FAIL")+" save-lifecycle"+(safety?" safety":" baseline")+": "+cases+" cases, "+assertions+" assertions, "+failures+" failures");
        if(failures>0)throw new AssertionError("save lifecycle failures: "+failures);
    }
    static MainActivity fixture(){
        MainActivity a=new MainActivity();DocumentTab t=tab("origin",ORIGINAL,"original text");a.tabs.add(t);a.activeTab=0;a.restoreFixtureTab();
        a.provider.bytes.put(ORIGINAL,"original text".getBytes(StandardCharsets.UTF_8));
        a.editor.text="edited café 😀\r\n";a.dirty=true;return a;
    }
    static DocumentTab tab(String id,String uri,String text){
        DocumentTab t=new DocumentTab();t.id=id;t.uri=uri;t.displayName="original.md";t.text=text;t.baselineHash=EditorTools.sha256(text.getBytes(StandardCharsets.UTF_8));
        t.baselineModified=100L;t.readOnly=false;return t;
    }
    static void complete(MainActivity a){eq(1,a.io.tasks.size(),"one pending worker");a.io.drain();eq(1,a.main.tasks.size(),"one completion posted");a.main.drain();}
    static void normal(){
        MainActivity a=fixture();final int[] followup={0};a.pendingAfterSave=()->followup[0]++;a.beginSave(false);
        eq(true,a.saveInProgress,"saving flag");eq(false,a.saveButton.enabled,"save disabled");eq(0,a.provider.writes,"worker not yet run");complete(a);
        eq(1,a.provider.writes,"one provider write");eq(ORIGINAL,a.provider.writeUris.get(0),"original target");
        eq("edited café 😀\r\n",new String(a.provider.bytes.get(ORIGINAL),StandardCharsets.UTF_8),"saved bytes");
        eq(false,a.tabs.get(0).dirty,"saved clean");eq(false,a.dirty,"active clean");eq(false,a.saveInProgress,"saving reset");eq(true,a.saveButton.enabled,"button restored");
        eq(1,a.recoveriesCleared,"clean recovery cleared");eq(1,followup[0],"followup ran");eq(null,a.pendingAfterSave,"followup consumed");
    }
    static void saveAsSuccess(){
        MainActivity a=fixture();a.saveAsTabId="origin";a.chooseDestination(MainActivity.Uri.parse(DESTINATION));complete(a);
        eq(DESTINATION,a.tabs.get(0).uri,"destination adopted");eq(DESTINATION,a.currentUri.toString(),"active identity");
        eq(EditorTools.sha256("edited café 😀\r\n".getBytes(StandardCharsets.UTF_8)),a.baselineHash,"verified baseline");
        eq("original text",new String(a.provider.bytes.get(ORIGINAL),StandardCharsets.UTF_8),"original bytes untouched");
        eq(false,a.tabs.get(0).dirty,"saved clean");eq(0,a.errors.size(),"no errors");
    }
    static void newerEdits(){
        MainActivity a=fixture();final int[] followup={0};a.pendingAfterSave=()->followup[0]++;a.beginSave(false);a.io.drain();
        a.editor.text="newer draft\r\n";a.dirty=true;a.main.drain();
        eq("newer draft\r\n",a.tabs.get(0).text,"newer text retained");eq(true,a.tabs.get(0).dirty,"newer tab dirty");eq(true,a.dirty,"active dirty");
        eq("edited café 😀\r\n",new String(a.provider.bytes.get(ORIGINAL),StandardCharsets.UTF_8),"only captured version saved");
        eq(0,a.recoveriesCleared,"newer recovery not cleared");eq(1,a.recoveriesScheduled,"newer recovery scheduled");eq(0,followup[0],"followup blocked by dirty state");
    }
    static void untitledSaveAs(){
        MainActivity a=fixture();a.currentUri=null;a.tabs.get(0).uri=null;a.saveAsTabId="origin";
        a.chooseDestination(MainActivity.Uri.parse(DESTINATION));complete(a);
        eq(DESTINATION,a.tabs.get(0).uri,"untitled destination");eq(false,a.dirty,"untitled clean");eq(1,a.provider.writes,"untitled write");
    }
    static void saveAsNewerEdits(){
        MainActivity a=fixture();a.saveAsTabId="origin";a.chooseDestination(MainActivity.Uri.parse(DESTINATION));a.io.drain();
        a.editor.text="Save As newer text";a.dirty=true;a.main.drain();
        eq(DESTINATION,a.currentUri.toString(),"verified Save As adopts destination");eq("Save As newer text",a.tabs.get(0).text,"Save As newer text retained");
        eq(true,a.tabs.get(0).dirty,"Save As newer dirty");eq(0,a.recoveriesCleared,"Save As newer no recovery clear");eq(1,a.recoveriesScheduled,"Save As newer recovery scheduled");
    }
    static void saveAsSwitchedTab(){
        MainActivity a=fixture();a.tabs.add(tab("other",OTHER,"other content"));a.saveAsTabId="origin";
        a.chooseDestination(MainActivity.Uri.parse(DESTINATION));a.io.drain();a.switchToTab(1,false);a.main.drain();
        eq(DESTINATION,a.tabs.get(0).uri,"inactive origin adopts verified Save As");eq(false,a.tabs.get(0).dirty,"inactive origin clean");
        eq(OTHER,a.currentUri.toString(),"active other identity unchanged");eq("other content",a.editor.text,"active other text unchanged");eq(0,a.recoveriesCleared,"inactive Save As no recovery clear");
    }
    static void switchedTab(){
        MainActivity a=fixture();a.tabs.add(tab("other",OTHER,"other content"));final int[] followup={0};a.pendingAfterSave=()->followup[0]++;a.beginSave(false);a.io.drain();a.switchToTab(1,false);
        a.editor.text="other newer content";a.dirty=true;a.main.drain();
        eq(1,a.activeTab,"other tab remains active");eq(OTHER,a.currentUri.toString(),"other URI retained");eq("other newer content",a.editor.text,"other editor retained");
        eq(false,a.tabs.get(0).dirty,"saved origin clean");eq(ORIGINAL,a.tabs.get(0).uri,"origin identity retained");eq(true,a.dirty,"other dirty retained");
        eq(0,a.recoveriesCleared,"inactive completion does not clear recovery");eq(0,followup[0],"inactive completion does not run followup");
    }
    static void closedTab(){
        MainActivity a=fixture();a.beginSave(false);a.io.drain();a.tabs.clear();a.tabs.add(tab("other",OTHER,"other content"));a.activeTab=0;a.restoreFixtureTab();a.main.drain();
        eq(1,a.tabs.size(),"no resurrection");eq("other",a.tabs.get(0).id,"surviving tab unchanged");eq(OTHER,a.currentUri.toString(),"other identity unchanged");
        eq(false,a.saveInProgress,"saving reset after closed tab");eq(0,a.recoveriesCleared,"no recovery clear for removed tab");
    }
    static void failedNormal(){
        for(String fault:new String[]{"write","mismatch"}){
            MainActivity a=fixture();a.provider.fault=fault;a.beginSave(false);complete(a);
            eq(true,a.dirty,"failed save keeps dirty "+fault);eq("edited café 😀\r\n",a.tabs.get(0).text,"draft retained "+fault);
            eq(ORIGINAL,a.tabs.get(0).uri,"normal save original identity "+fault);eq(0,a.recoveriesCleared,"failed save no clear "+fault);
            eq(1,a.errors.size(),"failure displayed "+fault);eq(false,a.saveInProgress,"failed saving reset "+fault);
        }
    }
    static void cancelled(){
        MainActivity a=fixture();a.saveAsTabId="origin";a.pendingAfterSave=()->{throw new AssertionError("cancel callback ran");};a.cancelPicker();
        eq(ORIGINAL,a.currentUri.toString(),"cancel original URI");eq(ORIGINAL,a.tabs.get(0).uri,"cancel tab URI");eq(null,a.saveAsTabId,"cancel clears picker origin");
        eq(null,a.pendingAfterSave,"cancel clears followup");eq(0,a.provider.writes,"cancel no write");eq(0,a.io.tasks.size(),"cancel no worker");
    }
    static void beforeVerification(){
        MainActivity a=fixture();String baseline=a.baselineHash;a.saveAsTabId="origin";a.chooseDestination(MainActivity.Uri.parse(DESTINATION));
        eq(0,a.provider.writes,"no provider write yet");eq(ORIGINAL,a.tabs.get(0).uri,"original tab identity until verified");
        eq(ORIGINAL,a.currentUri.toString(),"original active identity until verified");eq(baseline,a.baselineHash,"original baseline until verified");
    }
    static void failedSaveAs(String fault){
            MainActivity a=fixture();String baseline=a.baselineHash;a.readOnly=true;a.tabs.get(0).readOnly=true;a.utf8Bom=true;a.tabs.get(0).utf8Bom=true;a.provider.fault=fault;
            a.saveAsTabId="origin";a.chooseDestination(MainActivity.Uri.parse(DESTINATION));complete(a);
            eq(1,a.errors.size(),"failed Save As reported "+fault);eq(ORIGINAL,a.tabs.get(0).uri,"failed Save As retains origin "+fault);
            eq(ORIGINAL,a.currentUri.toString(),"failed Save As active origin "+fault);eq(baseline,a.baselineHash,"failed Save As baseline "+fault);
            eq(true,a.readOnly,"failed Save As readonly "+fault);eq(true,a.dirty,"failed Save As dirty "+fault);
            eq("original.md",a.displayName,"failed Save As original name "+fault);eq(100L,a.baselineModified,"failed Save As original time "+fault);
            eq(true,a.utf8Bom,"failed Save As BOM retained "+fault);eq(baseline,a.tabs.get(0).baselineHash,"failed Save As tab baseline "+fault);
    }
    static void removedOrigin(){
        MainActivity a=fixture();a.saveAsTabId="origin";a.pendingAfterSave=()->{throw new AssertionError("removed-origin continuation ran");};a.tabs.clear();a.tabs.add(tab("other",OTHER,"other content"));a.activeTab=0;a.restoreFixtureTab();
        a.chooseDestination(MainActivity.Uri.parse(DESTINATION));a.io.drain();a.main.drain();
        eq(0,a.provider.writes,"removed origin never writes other tab");eq(OTHER,a.tabs.get(0).uri,"removed origin leaves active identity");
        eq("other content",a.tabs.get(0).text,"removed origin leaves active text");eq(OTHER,a.currentUri.toString(),"active URI unchanged");
        eq(null,a.pendingAfterSave,"removed origin continuation cleared");eq(null,a.saveAsTabId,"removed origin picker ID cleared");
    }
    static void busyPickerResult(){
        MainActivity a=fixture();a.saveAsTabId="origin";a.beginSave(false);a.pendingAfterSave=()->{throw new AssertionError("busy picker continuation ran");};
        a.chooseDestination(MainActivity.Uri.parse(DESTINATION));
        eq(ORIGINAL,a.currentUri.toString(),"busy result active URI unchanged");eq(ORIGINAL,a.tabs.get(0).uri,"busy result tab URI unchanged");
        eq(null,a.pendingAfterSave,"busy result continuation cleared");eq(null,a.saveAsTabId,"busy result picker ID cleared");
        eq(1,a.io.tasks.size(),"only already-running save queued");complete(a);eq(ORIGINAL,a.provider.writeUris.get(0),"already-running original target");
    }
    static void closedSaveAsContinuation(){
        MainActivity a=fixture();a.saveAsTabId="origin";final int[] followup={0};a.pendingAfterSave=()->followup[0]++;
        a.chooseDestination(MainActivity.Uri.parse(DESTINATION));a.io.drain();a.tabs.clear();a.tabs.add(tab("other",OTHER,"other content"));a.activeTab=0;a.restoreFixtureTab();a.main.drain();
        eq(null,a.pendingAfterSave,"closed origin stale continuation cleared");eq(0,followup[0],"closed origin no continuation");
        a.provider.bytes.put(OTHER,"other content".getBytes(StandardCharsets.UTF_8));a.beginSave(false);complete(a);eq(0,followup[0],"later other save cannot consume stale continuation");
    }
    interface Action{void run();}
    static void run(String name,Action action){cases++;try{action.run();System.out.println("PASS "+name);}catch(AssertionError e){failures++;System.out.println("FAIL "+name+": "+e.getMessage());}}
    static void eq(Object expected,Object actual,String message){assertions++;if(!Objects.equals(expected,actual))throw new AssertionError(message+": expected="+expected+", actual="+actual);}
}
