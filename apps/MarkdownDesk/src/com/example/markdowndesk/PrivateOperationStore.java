package com.example.markdowndesk;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

/** Operation data belongs to the registered native parent, never a separate capacity authority. */
final class PrivateOperationStore {
    static final String ROOT="private_operations";
    static final int MAX_ENTRIES=65536;
    static final class Control {
        final List<PrivateOperationRecord> preparations=new ArrayList<>();
        final Map<UUID,HistoryControlStore.Entry> links=new HashMap<>();
        boolean valid=true,ready=true,present,pending;
        long reservedBytes;int reservedEntries;
    }
    static Control control(List<HistoryControlStore.Entry> entries,HistoryControlStore.Registration registration,HistoryPinRecord.Inventory pins)throws IOException {
        Control result=new Control();Map<String,HistoryControlStore.Entry> byName=new HashMap<>();Map<UUID,PrivateOperationRecord> preparations=new HashMap<>();Set<UUID> operations=new HashSet<>();
        for(HistoryControlStore.Entry e:entries)if(e.filename!=null)byName.put(e.filename,e);
        for(HistoryControlStore.Entry e:entries){
            if(!operationEntry(e))continue;result.present=true;
            String name=committed(e);
            if(name.endsWith(".reserve")){
                PrivateOperationRecord p=e.operation;
                if(p==null||p.type!=PrivateOperationRecord.PREPARE||!p.name().equals(name)||!p.binding.bound(registration,entries)||preparations.put(p.preparation,p)!=null||!operations.add(p.operation)){result.valid=false;continue;}
                result.preparations.add(p);if(e.stage)result.ready=false;
            }
        }
        int pending=0;
        for(PrivateOperationRecord p:result.preparations){
            HistoryControlStore.Entry reserve=byName.get(p.name()),reserveStage=byName.get(p.name()+".stage");
            HistoryControlStore.Entry begin=byName.get(p.begin.name()),beginStage=byName.get(p.begin.name()+".stage"),close=byName.get("pin-"+p.operation+".close"),closeStage=byName.get("pin-"+p.operation+".close.stage");
            if(reserve!=null&&reserveStage!=null||begin!=null&&beginStage!=null){result.valid=false;continue;}
            byte[] expectedBegin=HistoryPinRecord.encode(p.begin);
            boolean fullBegin=false;
            if(begin!=null){if(!Objects.equals(begin.envelopeHash,p.beginHash)||begin.pin==null){result.valid=false;continue;}fullBegin=true;}
            else if(beginStage!=null){
                if(!beginStage.pinPrefix(expectedBegin)){result.valid=false;continue;}
                result.reservedBytes=add(result.reservedBytes,expectedBegin.length-beginStage.length);
                fullBegin=beginStage.length==expectedBegin.length&&beginStage.pin!=null;
            }else{result.reservedBytes=add(result.reservedBytes,expectedBegin.length);result.reservedEntries++;}
            // A complete BEGIN.stage already participates in the native pin inventory's CLOSE credit.
            if(!fullBegin){result.reservedBytes=add(result.reservedBytes,HistoryPinRecord.CLOSE_BYTES);result.reservedEntries++;}
            PrivateOperationRecord expectedLink=PrivateOperationRecord.link(p);HistoryControlStore.Entry link=byName.get(expectedLink.name()),stage=byName.get(expectedLink.name()+".stage");
            if(link!=null&&stage!=null){result.valid=false;continue;}
            if(link!=null||stage!=null){
                HistoryControlStore.Entry entry=link!=null?link:stage;
                if(begin==null||reserve==null||!entry.operationPrefix(expectedLink.encoded())||link!=null&&!expectedLink.envelopeHash.equals(entry.envelopeHash)){result.valid=false;continue;}
                result.links.put(p.preparation,entry);result.reservedBytes=add(result.reservedBytes,PrivateOperationRecord.LINK_BYTES-entry.length);
            }else{result.reservedBytes=add(result.reservedBytes,PrivateOperationRecord.LINK_BYTES);result.reservedEntries++;}
            if(reserveStage!=null&&(begin!=null||beginStage!=null||link!=null||stage!=null||close!=null||closeStage!=null))result.valid=false;
            if(link==null||close==null)pending++;
        }
        for(HistoryControlStore.Entry e:entries){
            if(operationEntry(e)&&committed(e).endsWith(".link")){
                boolean found=false;for(PrivateOperationRecord p:result.preparations)if(committed(e).equals("prep-"+p.preparation+".link"))found=true;
                if(!found)result.valid=false;
            }
            if(e.pin!=null&&e.pin.begin&&e.pin.manifest.domain==HistoryPinRecord.Domain.LOCAL_CAPTURE_V1&&!operations.contains(e.pin.operation))result.valid=false;
        }
        if(pending>1)result.valid=false;result.pending=pending!=0;result.ready&=result.valid;
        result.preparations.sort(Comparator.comparing(p->p.preparation.toString()));return result;
    }
    static boolean operationEntry(HistoryControlStore.Entry e){return e.filename!=null&&PrivateOperationRecord.knownName(committed(e));}
    private static String committed(HistoryControlStore.Entry e){return e.stage?e.filename.substring(0,e.filename.length()-6):e.filename;}

    static final class Ledger {
        long actualBytes,reservedBytes,occupiedEntries,reservedEntries;
        boolean complete=true,present,pending;
        String fingerprint="";
        final List<String> diagnostics=new ArrayList<>();
        final Map<String,String> entries=new TreeMap<>();
        void invalid(String reason){complete=false;diagnostics.add("operation: "+reason);}
    }
    static Ledger inspect(File nativeRoot,HistoryControlStore.Inspection control,HistoryStorage.Budget budget)throws IOException {
        // Deliberately bind the existing native monitor; monitorFor(private_operations/UUID) is a different authority.
        Object monitor=HistoryStorage.monitorFor(nativeRoot);if(!Thread.holdsLock(monitor))throw new IOException("Native parent monitor required");
        budget.charge(HistoryStorage.operationEvidenceBytes(nativeRoot));
        Ledger ledger=new Ledger();ledger.pending=control.operations.pending;ledger.present=control.operations.present;
        if(!control.operations.valid)ledger.invalid("invalid commitments");
        Path root=HistoryStorage.canonicalRoot(nativeRoot).getParentFile().toPath().resolve(ROOT);
        Map<String,PrivateOperationRecord.Artifact> expected=new HashMap<>();Set<String> directories=new HashSet<>(),linkedArtifacts=new HashSet<>();boolean rootFunded=false;
        for(PrivateOperationRecord p:control.operations.preparations){
            budget.charge(2048L+512L*p.artifacts.size());directories.add(p.preparation.toString());rootFunded|=(p.directoryMask&1)!=0;
            HistoryControlStore.Entry link=control.operations.links.get(p.preparation);
            for(PrivateOperationRecord.Artifact a:p.artifacts){String key=p.preparation+"/"+a.filename();expected.put(key,a);if(link!=null&&!link.stage)linkedArtifacts.add(key);}
        }
        BasicFileAttributes rootAttributes=attributesOrAbsent(root);
        if(rootAttributes==null){if(!directories.isEmpty()){ledger.reservedEntries=1+directories.size();if(!rootFunded)ledger.invalid("required namespace disappeared");} }
        else{
            ledger.present=true;count(ledger,budget,ROOT,rootAttributes,root,false);
            if(!rootAttributes.isDirectory()||rootAttributes.isSymbolicLink())ledger.invalid("non-directory namespace");
            else{
                try(DirectoryStream<Path> children=Files.newDirectoryStream(root)){
                    for(Path child:children){BasicFileAttributes a=attributes(child);String name=child.getFileName().toString();count(ledger,budget,name,a,child,false);
                        if(!directories.contains(name)||!a.isDirectory()||a.isSymbolicLink()){ledger.invalid("unknown namespace entry");continue;}
                        directories.remove(name);
                        try(DirectoryStream<Path> files=Files.newDirectoryStream(child)){
                            for(Path file:files){BasicFileAttributes f=attributes(file);String key=name+"/"+file.getFileName();
                                boolean stage=key.endsWith(".stage");String canonical=stage?key.substring(0,key.length()-6):key;
                                PrivateOperationRecord.Artifact wanted=expected.remove(canonical);
                                count(ledger,budget,key,f,file,wanted!=null&&f.isRegularFile()&&f.size()<=wanted.length);
                                if(wanted==null||!f.isRegularFile()||f.isSymbolicLink()||f.size()>wanted.length){ledger.invalid("unknown/conflicting artifact");continue;}
                                if(stage){ledger.reservedBytes=add(ledger.reservedBytes,wanted.length-f.size());ledger.pending=true;if(linkedArtifacts.contains(canonical))ledger.invalid("linked artifact replaced by stage");}
                                else if(f.size()!=wanted.length||!ledger.entries.get(key).endsWith(":"+wanted.hash))ledger.invalid("changed artifact");
                            }
                        }catch(DirectoryIteratorException invalid){ledger.invalid("unreadable preparation");}
                        same(a,attributes(child));
                    }
                }catch(DirectoryIteratorException invalid){ledger.invalid("unreadable namespace");}
                same(rootAttributes,attributes(root));ledger.reservedEntries=add(ledger.reservedEntries,directories.size());
                if(control.operations.preparations.isEmpty())ledger.invalid("unfunded namespace");
            }
        }
        for(Map.Entry<String,PrivateOperationRecord.Artifact> missing:expected.entrySet()){ledger.reservedBytes=add(ledger.reservedBytes,missing.getValue().length);ledger.reservedEntries=add(ledger.reservedEntries,1);if(linkedArtifacts.contains(missing.getKey()))ledger.invalid("linked artifact disappeared");}
        if(add(ledger.occupiedEntries,ledger.reservedEntries)>MAX_ENTRIES)ledger.invalid("namespace slot limit");
        HistoryStorage.requireOperationEvidence(nativeRoot,control,ledger);
        if(ledger.present){MessageDigest digest=HistoryStorage.digest();try(DataOutputStream out=new DataOutputStream(new java.security.DigestOutputStream(new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}},digest))){
            out.writeUTF("private-operation-ledger-v1");for(Map.Entry<String,String> e:ledger.entries.entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}
            for(HistoryControlStore.Entry e:control.entries)if(operationEntry(e)){out.writeUTF(e.filename);out.writeLong(e.length);out.writeUTF(e.envelopeHash==null?"":e.envelopeHash);out.writeBoolean(e.stage);}out.writeLong(ledger.actualBytes);out.writeLong(ledger.reservedBytes);out.writeLong(ledger.occupiedEntries);out.writeLong(ledger.reservedEntries);out.writeLong(control.controlReservedBytes);out.writeInt(control.controlReservedEntries);out.writeBoolean(ledger.complete);
        }ledger.fingerprint=HistoryStorage.hex(digest.digest());}
        return ledger;
    }
    private static void count(Ledger ledger,HistoryStorage.Budget budget,String key,BasicFileAttributes a,Path path,boolean verifiedLength)throws IOException {
        if(ledger.occupiedEntries>=MAX_ENTRIES)throw new IOException("Operation namespace entry bound");budget.charge(1024L+key.length()*2L);ledger.occupiedEntries++;
        String value;
        if(a.isRegularFile()){ledger.actualBytes=add(ledger.actualBytes,a.size());value="file:"+a.size()+":"+a.fileKey()+":"+(verifiedLength?fileHash(path,a):"unvalidated");}
        else value=(a.isDirectory()?"dir:":"unknown:")+a.fileKey();ledger.entries.put(key,value);
    }
    static String fileHash(Path path,BasicFileAttributes before)throws IOException {
        MessageDigest digest=HistoryStorage.digest();long count=0;ByteBuffer buffer=ByteBuffer.allocate(65536);
        try(SeekableByteChannel channel=Files.newByteChannel(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(true){buffer.clear();int n=channel.read(buffer);if(n<0)break;if(n==0)throw new IOException("Operation read stalled");count=add(count,n);if(count>before.size())throw new IOException("Operation grew during read");digest.update(buffer.array(),0,n);}
        }same(before,attributes(path));if(count!=before.size())throw new IOException("Operation length changed");return HistoryStorage.hex(digest.digest());
    }
    static BasicFileAttributes attributes(Path p)throws IOException{return Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    static BasicFileAttributes attributesOrAbsent(Path p)throws IOException{try{return attributes(p);}catch(NoSuchFileException absent){return null;}}
    static void same(BasicFileAttributes before,BasicFileAttributes after)throws IOException{if(before.fileKey()==null||!Objects.equals(before.fileKey(),after.fileKey())||before.size()!=after.size()||before.isDirectory()!=after.isDirectory()||before.isRegularFile()!=after.isRegularFile()||!before.lastModifiedTime().equals(after.lastModifiedTime()))throw new IOException("Changed operation identity");}
    static long add(long a,long b)throws IOException{try{return Math.addExact(a,b);}catch(ArithmeticException invalid){throw new IOException("Operation accounting overflow",invalid);}}
}
