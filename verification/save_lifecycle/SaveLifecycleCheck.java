package com.example.markdowndesk;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class SaveLifecycleCheck {
    static int cases,assertions,failures;
    static final String ORIGINAL="content://fixture/original",DESTINATION="content://fixture/save-as",OTHER="content://fixture/other";
    public static void main(String[] args)throws Exception{
        boolean safety=Arrays.asList(args).contains("--safety");
        run("HISTORY changed active Save As invalidates old dialog",()->historySaveAsInvalidation());
        run("HISTORY startup remains on I/O queue and blocked control retains evidence",()->historyStartup());
        run("HISTORY verified same-URI and Save As routes retain exact identity",()->historyRoutes());
        run("HISTORY optional metadata errors preserve verified success and newer edits",()->optionalHistory());
        run("HISTORY optional warning follows originating tab/destination after continuation",()->historyWarningScope());
        run("RESTORE quota failure keeps existing evidence and blocks copy",()->restoreQuota());
        run("RESTORE canceled and busy picker results cannot write",()->restoreCanceledBusy());
        run("RESTORE versions list performs I/O asynchronously and launches explicit copy picker",()->versionsPicker());
        run("CONSENT overwrite reuses original captured revision and preserves newer edits",()->consentRevision());
        run("CONSENT dialog retains captured origin after active tab switch",()->consentOrigin());
        run("CONSENT stale observed hash conflicts again",()->staleConsent());
        run("CONSENT closed origin cannot retarget",()->closedConsent());
        run("PROTECTION failed store blocks all provider writes",()->failedProtection());
        run("PROTECTION failed provider read retains attempted edit",()->failedRead());
        run("RESTORE corrupted selected snapshot fails closed",()->corruptedRestore());
        run("RESTORE snapshot copy independent of twelve active tabs",()->restoreCopy(false));
        run("RESTORE original URI cannot be overwritten",()->restoreCopy(true));
        run("PROTECTION ordinary save retains exact original and captured attempted bytes",()->protectedSave(false));
        run("PROTECTION Save As protects nonempty destination first",()->protectedSave(true));
        run("PROTECTION generic force cannot bypass changed source",()->forceConflict());
        run("PROTECTION unknown baseline must conflict",()->unknownBaseline());
        run("PROTECTION uncertain outcome does not promise legacy recovery",()->uncertainty());
        run("invalid Unicode capture stops before queuing save",()->invalidCapture());
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
    static HistoryIdentity identities(MainActivity a)throws java.io.IOException{return new HistoryIdentity(a.filesDir.toPath().resolve("history_identity").toFile(),new MainActivity.AndroidDurability());}
    static HistoryIdentity.Descriptor uriHistory(MainActivity a,String uri)throws java.io.IOException {
        for(HistoryIdentity.Descriptor d:identities(a).descriptors())if(uri.equals(d.exactUri))return d;
        throw new AssertionError("No actual URI history: "+uri);
    }
    static void historySaveAsInvalidation(){for(String route:new String[]{"same-uri","active-save-as","inactive-save-as"}){
        MainActivity a=fixture();a.historyRequest=17;MainActivity.AlertDialog old=new MainActivity.AlertDialog();a.historyDialog=old;
        if(route.equals("same-uri"))a.beginSave(false);
        else {a.saveAsTabId="origin";a.chooseDestination(MainActivity.Uri.parse(DESTINATION));}
        a.io.drain();
        if(route.equals("inactive-save-as")){a.tabs.add(tab("other",OTHER,"other text"));a.switchToTab(1,false);}
        a.main.drain();boolean changedActive=route.equals("active-save-as");
        eq(changedActive,old.dismissed,"only changed active save dismisses history "+route);
        eq(changedActive?18L:17L,a.historyRequest,"only changed active save advances history generation "+route);
        eq(changedActive?null:old,a.historyDialog,"history handle lifetime follows active URI adoption "+route);
    }}
    static void historyStartup(){MainActivity a=fixture();try{
        a.beginSave(false);eq(null,a.historyController,"controller not initialized on main capture");eq(false,java.nio.file.Files.exists(a.filesDir.toPath().resolve("history_control")),"no control fsync/publication before worker");
        complete(a);eq(true,a.historyController!=null,"worker initialized real controller");
        ProtectionStore.Snapshot retained=a.store().snapshots().get(0);byte[] envelope=java.nio.file.Files.readAllBytes(a.filesDir.toPath().resolve("protected_saves").resolve(retained.id+".snapshot"));
        java.nio.file.Path enrollment=a.filesDir.toPath().resolve("history_enrollment.control");byte[] originalEnrollment=java.nio.file.Files.readAllBytes(enrollment),bad=originalEnrollment.clone();bad[bad.length-1]^=1;java.nio.file.Files.write(enrollment,bad);
        a.historyController=null;a.editor.text="newer unsaved text";a.dirty=true;int writes=a.provider.writes,clears=a.recoveriesCleared;
        a.beginSave(false);complete(a);eq(writes,a.provider.writes,"failed shared startup opens no provider writer");eq(null,a.historyController,"failed controller not published");
        eq(true,a.dirty,"failed startup retains dirty editor");eq("newer unsaved text",a.tabs.get(0).text,"failed startup retains captured edit");eq(clears,a.recoveriesCleared,"failed startup does not clear recovery");
        eq(true,Arrays.equals(envelope,java.nio.file.Files.readAllBytes(a.filesDir.toPath().resolve("protected_saves").resolve(retained.id+".snapshot"))),"failed startup keeps exact protection envelope");
        eq(true,a.store().snapshot(retained.id).byteCount>0,"raw protected inspection remains readable with bad enrollment");
        java.nio.file.Files.write(enrollment,originalEnrollment);a.beginSave(false);complete(a);eq(writes+1,a.provider.writes,"explicit save retry after control repair reopens real authority");
    }catch(java.io.IOException e){throw new AssertionError(e);}}
    static void historyRoutes(){try{
        MainActivity same=fixture();same.beginSave(false);complete(same);HistoryIdentity.Descriptor original=uriHistory(same,ORIGINAL);
        eq(0,original.origins.size(),"same URI has no invented origin");eq(2,same.controller().list(original.id).size(),"same URI completed pair visible");
        eq(true,same.controller().list(original.id).stream().allMatch(v->v.status==HistoryIndex.Status.INDEXED_VERIFIED),"same URI actually indexed");
        MainActivity copy=fixture();String destinationId=copy.controller().resolveDocument(DESTINATION,"destination-tab");copy.saveAsTabId="origin";copy.chooseDestination(MainActivity.Uri.parse(DESTINATION));complete(copy);
        HistoryIdentity.Descriptor destination=uriHistory(copy,DESTINATION),source=uriHistory(copy,ORIGINAL);
        eq(destinationId,destination.id,"existing destination identity preserved");eq(Collections.singletonList(source.id),destination.origins,"Save As uses captured old URI before adoption");
        eq(DESTINATION,copy.currentUri.toString(),"Save As adopts verified destination");eq(2,copy.controller().list(destination.id).size(),"destination pair indexed");
        MainActivity unsaved=fixture();unsaved.currentUri=null;unsaved.tabs.get(0).uri=null;unsaved.saveAsTabId="origin";unsaved.chooseDestination(MainActivity.Uri.parse(DESTINATION));complete(unsaved);
        HistoryIdentity.Descriptor dest=uriHistory(unsaved,DESTINATION);HistoryIdentity.Descriptor origin=identities(unsaved).lookup(dest.origins.get(0));
        eq("origin",origin.tabId,"unsaved Save As uses captured TAB origin");eq(null,origin.exactUri,"unsaved provenance never guesses old URI");
        for(String fault:new String[]{"write","mismatch"}){MainActivity failed=fixture();failed.provider.fault=fault;failed.saveAsTabId="origin";failed.chooseDestination(MainActivity.Uri.parse(DESTINATION));complete(failed);eq(0,identities(failed).descriptors().size(),"failed Save As creates no identity/fork "+fault);}
        MainActivity canceled=fixture();canceled.controller();canceled.saveAsTabId="origin";canceled.cancelPicker();eq(0,identities(canceled).descriptors().size(),"picker cancellation creates no identity/fork");
    }catch(java.io.IOException e){throw new AssertionError(e);}}
    static void corruptOptional(MainActivity a,String role)throws java.io.IOException {
        a.controller();String extension=role.equals("history_identity")?"identity":"index";
        java.nio.file.Files.write(a.filesDir.toPath().resolve(role).resolve(UUID.randomUUID()+"."+extension),new byte[]{1});
    }
    static long historyWarnings(MainActivity a){return a.toasts.stream().filter(t->t.contains("history metadata could not be confirmed")).count();}
    static void optionalHistory(){for(String role:new String[]{"history_identity","history_index"})try{
        MainActivity a=fixture();corruptOptional(a,role);a.saveAsTabId="origin";a.chooseDestination(MainActivity.Uri.parse(DESTINATION));a.io.drain();
        a.editor.text="newer edit after verified write";a.dirty=true;a.main.drain();
        eq(1,a.provider.writes,"optional error does not undo provider success "+role);eq(0,a.errors.size(),"optional error never reaches failure callback "+role);
        eq(true,a.store().transactions().get(0).verified,"actual native completion retained "+role);eq(DESTINATION,a.currentUri.toString(),"verified destination adopted despite optional error "+role);
        eq("newer edit after verified write",a.tabs.get(0).text,"newer edits retained despite optional error "+role);eq(true,a.dirty,"newer edits remain dirty "+role);eq(0,a.recoveriesCleared,"newer recovery not cleared "+role);
        eq(1,a.recoveriesScheduled,"newer recovery scheduled "+role);eq(1L,historyWarnings(a),"origin-bound optional warning visible "+role);
    }catch(java.io.IOException e){throw new AssertionError(e);}}
    static void historyWarningScope(){for(String transition:new String[]{"continuation-switch","continuation-uri","finishing","closed"})try{
        MainActivity a=fixture();corruptOptional(a,"history_index");a.tabs.add(tab("other",OTHER,"other text"));final int[] continued={0};
        a.pendingAfterSave=()->{continued[0]++;if(transition.equals("continuation-switch"))a.switchToTab(1,false);if(transition.equals("continuation-uri")){a.tabs.get(0).uri=OTHER;a.currentUri=MainActivity.Uri.parse(OTHER);}};
        a.beginSave(false);a.io.drain();
        if(transition.equals("finishing"))a.finishing=true;
        if(transition.equals("closed")){a.tabs.remove(0);a.activeTab=0;a.restoreFixtureTab();}
        a.main.drain();eq(0L,historyWarnings(a),"optional warning suppressed after "+transition);eq(0,a.errors.size(),"optional error does not become save failure after "+transition);
        eq(true,a.store().transactions().get(0).verified,"completion retained after "+transition);
        if(!transition.equals("closed"))eq(1,continued[0],"successful continuation still runs "+transition);
    }catch(java.io.IOException e){throw new AssertionError(e);}}
    static void restoreQuota(){MainActivity a=fixture();try{
        ProtectionStore.Snapshot s=a.store().putSnapshot("orphan",ORIGINAL,"orphan.md",1,"retained evidence".getBytes(StandardCharsets.UTF_8),"attempted");
        java.nio.file.Path store=a.filesDir.toPath().resolve("protected_saves");
        a.controller();HistoryCapacity capacity=HistoryStorage.capacity(store.toFile());
        long remaining=capacity.policy().quotaBytes-capacity.inspect().dataChargedBytes;
        while(remaining>0){long size=Math.min(remaining,ProtectionStore.MAX_DOCUMENT_BYTES);
            try(java.io.RandomAccessFile stage=new java.io.RandomAccessFile(store.resolve(UUID.randomUUID()+".stage").toFile(),"rw")){stage.setLength(size);}remaining-=size;}
        HistoryCapacity.Usage usage=capacity.inspect();
        eq(true,usage.usageComplete,"quota proof has complete physical accounting");eq(false,usage.mutationBlocked,"quota proof has acknowledged writable authority");
        eq(capacity.policy().quotaBytes,usage.dataChargedBytes,"shared data quota exactly occupied before restore");
        a.restoreSnapshotId=s.id;a.chooseProtectedDestination(MainActivity.Uri.parse(DESTINATION));a.io.drain();a.main.drain();
        eq(0,a.provider.writes,"quota never permits unprotected copy");eq(1,a.errors.size(),"quota protection failure surfaced");eq(1,a.store().snapshots().size(),"selected evidence retained");eq(true,a.dirty,"unsaved current editor retained");
    }catch(java.io.IOException e){throw new AssertionError(e);}}
    static void restoreCanceledBusy(){MainActivity a=fixture();a.restoreSnapshotId="selected";a.onActivityResult(1004,0,null);eq(null,a.restoreSnapshotId,"cancel consumes pending snapshot");eq(0,a.io.tasks.size(),"cancel no copy worker");a.restoreSnapshotId="selected";a.saveInProgress=true;a.chooseProtectedDestination(MainActivity.Uri.parse(DESTINATION));eq(null,a.restoreSnapshotId,"busy result consumed");eq(0,a.io.tasks.size(),"busy copy result no worker");eq(0,a.provider.writes,"no canceled or busy copy mutation");}
    static void versionsPicker(){MainActivity a=fixture();try{
        ProtectionStore.Snapshot v=a.store().putSnapshot("orphan-document",ORIGINAL,"orphan.md",17,"protected copy".getBytes(StandardCharsets.UTF_8),"attempted");
        a.openProtectedSaves();eq(null,a.dialog,"enumeration does not synchronously block UI");eq(1,a.io.tasks.size(),"list queued on existing I/O executor");a.io.drain();eq(null,a.dialog,"dialog waits for UI callback");a.main.drain();eq(1,a.dialog.labels.length,"orphan visible independently");
        a.dialog.items.onClick(null,0);eq(0,a.pickerRequest,"selection first requires explicit restore choice");a.dialog.positive.onClick(null,0);eq(1004,a.pickerRequest,"separate restore-to-copy picker");eq(v.id,a.restoreSnapshotId,"selected snapshot ID captured");
        a.chooseProtectedDestination(MainActivity.Uri.parse(DESTINATION));a.io.drain();a.main.drain();eq("protected copy",new String(a.provider.bytes.get(DESTINATION),StandardCharsets.UTF_8),"selected evidence restored");
        eq(ORIGINAL,a.currentUri.toString(),"listing and restoring never adopt editor identity");
    }catch(java.io.IOException e){throw new AssertionError(e);}}
    static MainActivity conflicting(){MainActivity a=fixture();a.provider.bytes.put(ORIGINAL,"external".getBytes(StandardCharsets.UTF_8));a.beginSave(false);complete(a);return a;}
    static void consentRevision(){MainActivity a=conflicting();try{
        long revision=a.store().snapshots().get(0).revision;a.editor.text="newer text while conflict dialog is open";a.dirty=true;
        a.dialog.positive.onClick(null,0);complete(a);
        eq("edited café 😀\r\n",new String(a.provider.bytes.get(ORIGINAL),StandardCharsets.UTF_8),"consent saves captured A only");
        eq("newer text while conflict dialog is open",a.tabs.get(0).text,"newer B retained");eq(true,a.dirty,"newer B remains unsaved");
        eq(revision,a.store().transactions().get(0).attempted.revision,"consent retains request revision");
    }catch(java.io.IOException e){throw new AssertionError(e);}}
    static void consentOrigin(){MainActivity a=conflicting();MainActivity.AlertDialog.Builder dialog=a.dialog;long revision=a.capturedSaveRevision;a.tabs.add(tab("other",OTHER,"other text"));a.switchToTab(1,false);dialog.positive.onClick(null,0);complete(a);eq(ORIGINAL,a.provider.writeUris.get(0),"captured origin destination");eq("other text",a.tabs.get(1).text,"other tab unchanged");try{
        HistoryIdentity.Descriptor origin=uriHistory(a,ORIGINAL);eq(0,origin.origins.size(),"consent retains original source URI instead of active other URI");
        eq(true,a.controller().list(origin.id).stream().filter(v->v.groupId!=null).allMatch(v->v.status==HistoryIndex.Status.INDEXED_VERIFIED&&v.revision==revision&&v.documentId.equals("origin")),"consent indexing retains captured tab and revision");
        eq(false,identities(a).descriptors().stream().anyMatch(d->OTHER.equals(d.exactUri)),"consent never creates active-other provenance");
    }catch(java.io.IOException e){throw new AssertionError(e);}}
    static void staleConsent(){MainActivity a=conflicting();MainActivity.AlertDialog.Builder dialog=a.dialog;a.provider.bytes.put(ORIGINAL,"new external version".getBytes(StandardCharsets.UTF_8));dialog.positive.onClick(null,0);complete(a);eq(0,a.provider.writes,"stale consent never writes");eq(true,a.dialog!=dialog,"new conflict decision required");}
    static void closedConsent(){MainActivity a=conflicting();MainActivity.AlertDialog.Builder dialog=a.dialog;a.tabs.clear();a.tabs.add(tab("other",OTHER,"other text"));a.activeTab=0;a.restoreFixtureTab();dialog.positive.onClick(null,0);eq(0,a.io.tasks.size(),"no worker for removed origin");eq(0,a.provider.writes,"no retargeted overwrite");}
    static void failedProtection(){MainActivity a=fixture();try{java.nio.file.Files.writeString(a.filesDir.toPath().resolve("protected_saves"),"blocking file");}catch(java.io.IOException e){throw new AssertionError(e);}a.beginSave(false);complete(a);eq(0,a.provider.writes,"protection failure blocks mutation");eq(0,a.provider.reads,"protection failure blocks provider read as well");eq(1,a.errors.size(),"protection error surfaced");}
    static void failedRead(){MainActivity a=fixture();a.provider.fault="read";a.beginSave(false);complete(a);eq(0,a.provider.writes,"read failure never writes");try{eq(1,a.store().snapshots().size(),"attempt protected on read failure");}catch(java.io.IOException e){throw new AssertionError(e);}}
    static void corruptedRestore(){MainActivity a=fixture();try{ProtectionStore.Snapshot s=a.store().putSnapshot("orphan",ORIGINAL,"orphan.md",1,"restored edit".getBytes(StandardCharsets.UTF_8),"attempted");java.nio.file.Files.write(a.filesDir.toPath().resolve("protected_saves").resolve(s.id+".snapshot"),new byte[]{1,2,3});a.restoreSnapshotId=s.id;a.chooseProtectedDestination(MainActivity.Uri.parse(DESTINATION));a.io.drain();a.main.drain();eq(0,a.provider.writes,"disk corruption blocks copy");eq(1,a.errors.size(),"copy validation error reported");eq(true,a.dirty,"unsaved current tab retained");}catch(java.io.IOException e){throw new AssertionError(e);}}
    static void restoreCopy(boolean original){
        MainActivity a=fixture();try{
            ProtectionStore.Snapshot snapshot=a.store().putSnapshot("orphan-document",ORIGINAL,"orphan.md",17,"protected recovered edit".getBytes(StandardCharsets.UTF_8),"attempted");
            for(int i=1;i<12;i++)a.tabs.add(tab("other-"+i,OTHER+i,"other content"));
            a.restoreSnapshotId=snapshot.id;a.chooseProtectedDestination(MainActivity.Uri.parse(original?ORIGINAL:DESTINATION));a.io.drain();a.main.drain();
            eq(original?0:1,a.provider.writes,"explicit restore provider writes");eq(12,a.tabs.size(),"no recovery tab eviction");
            eq(ORIGINAL,a.currentUri.toString(),"active tab identity untouched");eq("edited café 😀\r\n",a.editor.text,"active editor untouched");
            eq(true,a.dirty,"active unsaved editor retained");eq(null,a.restoreSnapshotId,"one-time picker evidence consumed");
            if(!original){eq("protected recovered edit",new String(a.provider.bytes.get(DESTINATION),StandardCharsets.UTF_8),"snapshot bytes copied");eq(3,a.store().snapshots().size(),"all protected evidence retained");}
        }catch(java.io.IOException e){throw new AssertionError(e);}
    }
    static void protectedSave(boolean copy){
        MainActivity a=fixture();a.provider.bytes.put(DESTINATION,"previous destination".getBytes(StandardCharsets.UTF_8));
        if(copy){a.saveAsTabId="origin";a.chooseDestination(MainActivity.Uri.parse(DESTINATION));}else a.beginSave(false);
        a.editor.text="newer edits made before worker";complete(a);
        try{
            java.util.List<ProtectionStore.Snapshot> versions=a.store().snapshots();
            eq(2,versions.size(),"two durable protected versions");
            for(ProtectionStore.Snapshot v:versions){eq("origin",v.documentId,"captured identity");eq(copy?DESTINATION:ORIGINAL,v.uri,"captured target");
                eq(v.role.equals("original")?(copy?"previous destination":"original text"):"edited café 😀\r\n",new String(v.bytes(),StandardCharsets.UTF_8),"exact captured bytes");}
            eq(true,a.store().transactions().get(0).verified,"verified durable completion");
        }catch(java.io.IOException e){throw new AssertionError(e);}
    }
    static void forceConflict(){MainActivity a=fixture();a.provider.bytes.put(ORIGINAL,"external".getBytes(StandardCharsets.UTF_8));a.beginSave(true);complete(a);eq(0,a.provider.writes,"force cannot overwrite external bytes");eq(true,a.dialog!=null,"conflict dialog surfaced");}
    static void unknownBaseline(){MainActivity a=fixture();a.baselineHash=null;a.beginSave(false);complete(a);eq(0,a.provider.writes,"unknown baseline cannot write");eq(true,a.dialog!=null,"unknown baseline conflict");}
    static void uncertainty(){MainActivity a=fixture();a.provider.fault="write";a.beginSave(false);complete(a);eq(true,a.errors.get(0).contains("may be incomplete"),"uncertain provider warning");eq(false,a.errors.get(0).contains("recovery draft is still available"),"no false draft promise");}
    static void invalidCapture(){
        MainActivity a=fixture();a.editor.text="draft\ud800";a.pendingAfterSave=()->{throw new AssertionError("invalid capture continued");};a.beginSave(false);
        eq(0,a.io.tasks.size(),"invalid Unicode queues no worker");eq(0,a.provider.writes,"invalid Unicode no provider write");
        eq(true,a.dirty,"invalid Unicode remains dirty");eq(false,a.saveInProgress,"invalid Unicode not saving");
        eq(null,a.pendingAfterSave,"invalid Unicode clears continuation");eq(1,a.errors.size(),"invalid Unicode reported");
    }
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
