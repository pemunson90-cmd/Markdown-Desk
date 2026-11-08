package com.example.markdowndesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Counts real B1 inputs. Candidate accounting only: neither heap proof nor protocol authority. */
public final class PortableFundingCheck {
    static final long KIB=1024, PARENT=16L<<20;
    // Proposed enforced private-FS representation bounds, not observations of unborn own inodes.
    static final int KEY_UNITS=128,TIME_UNITS=64,NAME_BYTES=255,PATH_UNITS=4096;
    static final long CURRENT_NATIVE_CURSOR_CANDIDATE=256*KIB;
    static int cases;
    static void yes(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static long add(long... values){long n=0;for(long v:values){yes(v>=0,"negative accounting input");n=Math.addExact(n,v);}return n;}
    static long mul(long a,long b){yes(a>=0&&b>=0,"negative product");return Math.multiplyExact(a,b);}
    static int units(Path path){String s=path.toString();yes(s.length()<=PATH_UNITS,"private path bound");return s.length();}
    static int name(String n){yes(n.getBytes(StandardCharsets.UTF_8).length<=NAME_BYTES,"private basename bound");return n.length();}
    static long observedIdentity(Path p)throws Exception {
        BasicFileAttributes a=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        yes(a.fileKey()!=null&&!a.isSymbolicLink(),"real private identity");String k=a.fileKey().toString(),c=a.creationTime().toString(),m=a.lastModifiedTime().toString();
        yes(k.length()<=KEY_UNITS&&c.length()<=TIME_UNITS&&m.length()<=TIME_UNITS,"observed key/time representation bound");return add(k.length(),c.length(),m.length(),Long.toString(a.size()).length(),3);
    }
    static final class Estimate {
        final Map<String,Long> n=new LinkedHashMap<>();
        long used,head,need,slack,nativeScratchRoom;
        void put(String key,long value){yes(value>=0,"negative "+key);n.put(key,value);}
        long get(String key){Long v=n.get(key);yes(v!=null,"missing actual input "+key);return v;}
        void print(String label){System.out.println("CASE "+label+" "+n);System.out.println("RESULT "+label+" used="+used+" head="+head+" required="+need+" residual="+slack+" native_cursor_candidate="+CURRENT_NATIVE_CURSOR_CANDIDATE+" maximum_native_cursor_allowance="+nativeScratchRoom);}
    }
    static Estimate estimate(PortablePlanCheck.F f,PrivateCapturePlan p)throws Exception {
        Estimate e=new Estimate();e.used=PortablePlanCheck.used(f.budget);e.head=PARENT-e.used;
        yes(f.graph.complete&&f.graph.records.isEmpty(),"actual zero-native approved B1 scenario");
        long oldA=0;for(PrivateOperationRecord old:f.control.operations.preparations)oldA=add(oldA,old.artifacts.size());
        long ownA=p.prepare.artifacts.size(),frames=p.privateFrames.size(),rows=f.inputs.inventory().entries().size();
        yes(ownA<=19&&ownA==5+frames,"actual frozen artifact table");yes(rows==frames+1,"actual ordinary evidence/private membership");
        long b=add(f.control.actualBytes,p.prepare.encoded().length,204,173,126),controls=add(f.control.actualEntries,4),a=add(oldA,ownA),preps=add(f.control.operations.preparations.size(),1);
        long nodes=add(f.ledger.occupiedEntries,ownA,2),opControls=add(f.ledger.controlIdentities.size(),2);
        e.put("B0",f.control.actualBytes);e.put("Pbytes",p.prepare.encoded().length);e.put("Bstar",b);e.put("Estar",controls);e.put("Astar",a);e.put("Pstar",preps);e.put("Nstar",nodes);e.put("Ostar",opControls);e.put("private_frames",frames);e.put("Irows",rows);
        long tc=0,tl=0,tp=0,tn=0,observed=0,future=0;
        Path parent=f.parent,controlRoot=parent.resolve("history_control"),operationRoot=parent.resolve("private_operations"),ownRoot=operationRoot.resolve(f.prep.toString());
        for(Path dir:Arrays.asList(parent,controlRoot,f.store.storageRoot().toPath(),f.catalog.storageRoot().toPath(),f.identity.storageRoot().toPath(),f.index.storageRoot().toPath()))tp=add(tp,units(dir));
        for(HistoryControlStore.Entry old:f.control.entries){Path file=old.filename.equals(HistoryControlStore.ENROLLMENT_NAME)?parent.resolve(old.filename):controlRoot.resolve(old.filename);long id=observedIdentity(file);observed=add(observed,id);tc=add(tc,name(old.filename),old.envelopeHash.length(),units(file),id);tp=add(tp,units(file));}
        for(Map.Entry<String,String> old:f.ledger.entries.entrySet()){tl=add(tl,old.getKey().length(),old.getValue().length());Path path=old.getKey().equals(PrivateOperationStore.ROOT)?operationRoot:operationRoot.resolve(old.getKey());tp=add(tp,units(path));}
        for(Map.Entry<String,String> old:f.ledger.controlIdentities.entrySet())tl=add(tl,old.getKey().length(),old.getValue().length());
        // Actual generated names/paths; only future key/time strings use the explicit enforced cap.
        long controlIdentityCap=add(KEY_UNITS,2L*TIME_UNITS,20,3);
        for(String own:Arrays.asList(p.prepare.name(),p.prepare.begin.name(),p.link.name(),"pin-"+p.prepare.operation+".close")){
            Path path=controlRoot.resolve(own+".stage");tc=add(tc,name(own+".stage"),64,units(path),controlIdentityCap);tp=add(tp,units(path));future=add(future,controlIdentityCap);
        }
        for(PrivateOperationRecord.Artifact artifact:p.prepare.artifacts){String key=f.prep+"/"+artifact.filename()+".stage";Path path=ownRoot.resolve(artifact.filename()+".stage");long value=add(5,20,KEY_UNITS,64,2);tl=add(tl,key.length(),value);tp=add(tp,units(path));future=add(future,value);name(artifact.filename()+".stage");}
        for(Path dir:Arrays.asList(operationRoot,ownRoot)){String key=dir.equals(operationRoot)?PrivateOperationStore.ROOT:f.prep.toString();tl=add(tl,key.length(),4+KEY_UNITS);tp=add(tp,units(dir));future=add(future,4+KEY_UNITS);}
        for(String own:Arrays.asList(p.prepare.name(),p.link.name())){tl=add(tl,own.length()+6,controlIdentityCap);future=add(future,controlIdentityCap);}
        for(File role:Arrays.asList(f.store.storageRoot(),f.catalog.storageRoot(),f.identity.storageRoot(),f.index.storageRoot()))tn=add(tn,role.getPath().length());
        e.put("Tcontrol",tc);e.put("Tledger",tl);e.put("Tpaths",tp);e.put("Tnative_frozen",tn);e.put("observed_identity_units",observed);e.put("future_identity_cap_units",future);
        long transitionT=add(tc,tl,tp),ackT=add(tc,tp);e.put("Ttransition",transitionT);e.put("Tack",ackT);
        long h=f.inputs.capsuleMetadataBytes(f.budget),q=f.inputs.capsuleRowCount(f.budget);
        long m=p.prepare.artifact(1).length,i=p.prepare.artifact(3).length,k=p.prepare.artifact(5).length,d=p.prepare.artifact(2).length;
        e.put("M",m);e.put("I",i);e.put("K",k);e.put("D",d);e.put("H",h);e.put("Q",q);e.put("private_payload_bytes",f.frames.stream().mapToLong(v->v.payloadBytes).sum());
        // Observe frozen ordinary rows, never manufacture source authority or parse a caller catalog.
        long sourcePathUnits=0;
        for(int ordinal=0;ordinal<rows;ordinal++){PrivateSourceCatalog.Row row=f.inputs.sourceCoordinateAt(f.budget,ordinal);yes(row.route==PrivateSourceCatalog.OWN_FRAME||row.route==PrivateSourceCatalog.OWN_EVIDENCE,"initial scope has no native/retained route");PrivateOperationRecord.Artifact found=null;for(PrivateOperationRecord.Artifact art:p.prepare.artifacts)if(art.id.equals(row.member))found=art;yes(found!=null&&found.length==row.length&&found.hash.equals(row.hash),"real catalog artifact join");sourcePathUnits=add(sourcePathUnits,units(ownRoot.resolve(found.filename())));}
        sourcePathUnits=add(sourcePathUnits,units(ownRoot.resolve(p.prepare.artifact(3).filename())));e.put("source_path_units",sourcePathUnits);e.put("source_open_tickets",mul(2,add(rows,1)));
        long c=add(128*KIB,mul(12,b),mul(8192,controls),mul(1024,a),mul(4,tc));
        long l=add(128*KIB,mul(4096,nodes),mul(2048,preps),mul(1024,a),mul(2048,opControls),mul(4,tl));
        long maps=add(4096,mul(1024,add(controls,6)),mul(4,tp));
        long rt=add(32*KIB,mul(4096,add(f.control.actualEntries,f.ledger.occupiedEntries,ownA,6)),mul(4,transitionT));
        long rc=add(mul(8192,add(frames,3)),mul(4096,frames),512*KIB);
        long table=add(8192,mul(2048,add(rows,1)),mul(4,sourcePathUnits),mul(2,d));
        long ack=add(8192,mul(2048,add(controls,6)),mul(4,ackT));
        long rb=add(16*KIB,mul(4096,add(rows,q,1)),mul(8,add(i,k,h))),opens=mul(32768,add(rows,1));
        long wr=add(mul(2,c),l,192*KIB),wa=add(mul(2,c),l,mul(8,add(d,m,i,k,h)),mul(4096,add(rows,q,1)),512*KIB);
        // Root ruling: constant-space CURRENT native cursor, not original/current cardinality maps.
        long wtBase=add(mul(5,c),mul(2,l),mul(3,maps),192*KIB),wt=add(wtBase,CURRENT_NATIVE_CURSOR_CANDIDATE);
        long wb=add(512*KIB,128*KIB,mul(384,rows),mul(8,add(i,k,h))),w=Math.max(Math.max(wr,wa),Math.max(wt,wb));
        long providerWrappers=32768;long retained=add(rt,rc,table,ack,rb,opens,providerWrappers);e.need=add(retained,w);e.slack=e.head-e.need;e.nativeScratchRoom=e.head-retained-wtBase;
        for(Object[] pair:new Object[][]{{"C",c},{"L",l},{"Maps",maps},{"Rtransition",rt},{"Rcopies",rc},{"Rtable",table},{"Rack",ack},{"Rreadback",rb},{"Ropens",opens},{"Rprovider_wrappers",providerWrappers},{"Rtotal",retained},{"Wrefresh",wr},{"Wactivate",wa},{"Wterminal_without_native_cursor",wtBase},{"Wterminal",wt},{"Wreadback",wb},{"Wpeak",w},{"C_slots",5L},{"L_slots",2L}})e.put((String)pair[0],(Long)pair[1]);
        return e;
    }
    static void runCase(String label,int tabs,int payload,boolean recovery,boolean oneUnder)throws Exception {
        try(PortablePlanCheck.F f=new PortablePlanCheck.F()){
            f.scan();f.selection(payload,tabs,recovery);f.capture();f.inputs.measure();PrivateCapturePlan[] plan={null};f.locked(()->plan[0]=f.plan());
            // Real post-plan checkpoint only; do not run B1 test-only reparsing with independent codec budgets.
            Estimate e=estimate(f,plan[0]);e.print(label);yes(e.need<=e.head,"candidate schedule exceeds actual parent: "+label);
            long before=PortablePlanCheck.used(f.budget);
            if(oneUnder){f.budget.charge(e.head-(e.need-1));long at=PortablePlanCheck.used(f.budget);try{f.budget.charge(e.need);throw new AssertionError("accepted one byte under");}catch(HistoryStorage.BudgetExceeded expected){yes(PortablePlanCheck.used(f.budget)==at,"failed debit changed parent");}}
            else{f.budget.charge(e.need);yes(PortablePlanCheck.used(f.budget)==before+e.need,"original parent exact candidate debit");}
            f.absentP();yes(!Files.exists(f.parent.resolve("private_operations")),"estimator created no transport/artifact");
            long funded=PortablePlanCheck.used(f.budget);f.inputs.close();f.graphInput.close();yes(PortablePlanCheck.used(f.budget)==funded,"closing B1 inputs refunded parent");cases++;
        }
    }
    interface Checked {void run()throws Exception;}
    static void reject(Checked action,String why)throws Exception {
        try{action.run();throw new AssertionError("accepted "+why);}catch(IOException expected){}
    }
    static void unavailable(PortablePlanCheck.F f,HistoryStorage.Budget budget,String why)throws Exception {
        reject(()->f.inputs.capsuleMetadataBytes(budget),why+" metadata");
        reject(()->f.inputs.capsuleRowCount(budget),why+" row count");
        reject(()->f.inputs.sourceCoordinateAt(budget,0),why+" coordinate");
    }
    static void inputSeam()throws Exception {
        try(PortablePlanCheck.F f=new PortablePlanCheck.F()){
            f.scan();f.selection(0,2);f.capture();unavailable(f,f.budget,"unmeasured");f.inputs.measure();
            unavailable(f,null,"null budget");
            HistoryStorage.Budget foreign=new HistoryStorage.Budget(HistoryStorage.METADATA_BUDGET);foreign.charge(PortablePlanCheck.used(f.budget));
            unavailable(f,foreign,"equal-limit/equal-spent foreign budget");
            reject(()->f.inputs.sourceCoordinateAt(f.budget,-1),"negative ordinal");
            reject(()->f.inputs.sourceCoordinateAt(f.budget,f.inputs.inventory().entries().size()),"past-end ordinal");
            long before=PortablePlanCheck.used(f.budget),h=f.inputs.capsuleMetadataBytes(f.budget);int q=f.inputs.capsuleRowCount(f.budget);
            for(int n=0;n<f.inputs.inventory().entries().size();n++)yes(f.inputs.sourceCoordinateAt(f.budget,n)!=null,"actual coordinate");
            yes(PortablePlanCheck.used(f.budget)==before,"read-only seam debited budget");
            try(InputStream catalogInput=f.inputs.openCatalog();
                    PrivateSourceCatalog parsed=PrivateSourceCatalog.read(catalogInput,new PrivateBundleInventory.MetadataBudget(16L<<20))){
                yes(parsed.rows.size()==f.inputs.inventory().entries().size(),"real serialized row count");
                for(int n=0;n<parsed.rows.size();n++){
                    PrivateSourceCatalog.Row a=f.inputs.sourceCoordinateAt(f.budget,n),b=parsed.rows.get(n);
                    yes(a.outer.equals(b.outer)&&a.route==b.route&&a.owner.equals(b.owner)&&a.member.equals(b.member)&&a.offset==b.offset&&a.length==b.length&&a.hash.equals(b.hash),"exact serialized coordinate "+n);
                }
            }
            try(InputStream reopened=f.inputs.openCatalog()){yes(reopened.read()!=-1,"catalog reader closes and permits reopen");}
            long actualH=56;int actualQ=0;
            try(PrivateOperationEvidence.Cursor cursor=f.inputs.capsuleSource().open()){
                unavailable(f,f.budget,"pending cursor");PrivateOperationEvidence.Row row;byte[] buffer=new byte[4096];
                while((row=cursor.next())!=null){actualQ++;actualH=add(actualH,138,row.name.getBytes(StandardCharsets.UTF_8).length,row.role==1||row.role==4?row.actualLength:0);try(InputStream in=cursor.openRaw()){while(in.read(buffer)!=-1){}}}
            }
            yes(h==actualH&&q==actualQ,"actual capsule metadata and row count");
            f.inputs.close();unavailable(f,f.budget,"closed");
        }
        try(PortablePlanCheck.F f=new PortablePlanCheck.F()){
            f.scan();f.selection(0,0);f.capture();f.budget.charge(PARENT-PortablePlanCheck.used(f.budget));
            reject(()->f.inputs.measure(),"measurement exhausted original budget");unavailable(f,f.budget,"failed measurement");
        }
        System.out.println("Portable original-input seam: provenance/state/coordinates PASS");
    }
    public static void main(String[] args)throws Exception {
        try{add(Long.MAX_VALUE,1);throw new AssertionError("overflow accepted");}catch(ArithmeticException expected){}
        inputSeam();
        runCase("minimum",0,0,false,false);runCase("two8MiB",2,PrivateStateCodec.MAX_PAYLOAD_BYTES,false,false);runCase("14frames",12,0,true,false);runCase("minimum-one-under",0,0,false,true);
        System.out.println("Portable funding estimator: "+cases+" real B1 cases PASS; candidate counts/debits only, NOT heap/authority/consumer acceptance");
        System.out.println("MISSING production proof: current-native cursor256KiB implementation bound;12B/8192 object multipliers;capacity nesting<=5C/2L;private-FS future representation enforcement;finite per-outer open enforcement;prepaid consumption replacing existing direct parent charges.");
    }
}
