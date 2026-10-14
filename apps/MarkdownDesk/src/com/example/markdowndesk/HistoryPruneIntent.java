package com.example.markdowndesk;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Pure future journal framing. No filesystem access, publication, tombstone or deletion authority.
 * Supplied policy/authorization bindings are immutable evidence, not acknowledged runtime authority.
 * The current control parser deliberately does not recognize these future names.
 */
final class HistoryPruneIntent {
    static final String INTENT_NAME="prune.intent", STAGE_NAME="prune.intent.stage";
    static final int MAX_BYTES=64*1024, MAX_MEMBERS=256;
    private static final int MAGIC=0x4d444850, VERSION=1, HEADER=12, CHECKSUM=32;
    private static final int MAX_IDENTITIES=4096, MAX_ALIAS_BYTES=4096;

    enum Role {
        STORE("store",1), CATALOG("catalog",2), IDENTITY("identity",3), INDEX("index",4);
        final String wire;final int code;Role(String wire,int code){this.wire=wire;this.code=code;}
        static Role code(int code)throws IOException{for(Role r:values())if(r.code==code)return r;throw invalid("Unknown role ID");}
        static Role parse(String value)throws IOException{for(Role r:values())if(r.wire.equals(value))return r;throw invalid("Unknown logical role");}
    }
    enum Kind {
        SNAPSHOT("snapshot",Role.STORE,1), TRANSACTION("transaction",Role.STORE,2), COMPLETE("complete",Role.STORE,3),
        CHECKPOINT("checkpoint",Role.CATALOG,4), IDENTITY("identity",Role.IDENTITY,5), INDEX("index",Role.INDEX,6);
        final String wire;final Role role;final int code;Kind(String wire,Role role,int code){this.wire=wire;this.role=role;this.code=code;}
        static Kind code(int code)throws IOException{for(Kind k:values())if(k.code==code)return k;throw invalid("Unknown kind ID");}
        static Kind parse(String value)throws IOException{for(Kind k:values())if(k.wire.equals(value))return k;throw invalid("Unknown native kind");}
    }
    static final class Member {
        final Role role;final Kind kind;final String id,envelopeHash;final long serializedBytes;
        private Member(Role role,Kind kind,String id,long length,String hash)throws IOException{
            if(role==null||kind==null||kind.role!=role)throw invalid("Role/kind mismatch");
            this.id=id(id);this.envelopeHash=hash(hash);this.role=role;this.kind=kind;
            long max=role==Role.STORE?ProtectionStore.MAX_DOCUMENT_BYTES+32768L:16384;
            if(length<(role==Role.STORE?45:44)||length>max)throw invalid("Native envelope length");serializedBytes=length;
        }
        String key(){return role.wire+"/"+id+"."+kind.wire;}
        boolean same(Member other){return other!=null&&key().equals(other.key())&&serializedBytes==other.serializedBytes&&envelopeHash.equals(other.envelopeHash);}
    }
    static final class Reference {
        final Role sourceRole,targetRole;final Kind sourceKind,targetKind;final String sourceId,targetId;
        private Reference(Role sr,String si,Kind sk,Role tr,String ti,Kind tk)throws IOException{
            if(sk.role!=sr||tk.role!=tr)throw invalid("Reference role/kind mismatch");
            sourceRole=sr;sourceKind=sk;sourceId=id(si);targetRole=tr;targetKind=tk;targetId=id(ti);
        }
        String source(){return key(sourceRole,sourceId,sourceKind);}
        String target(){return key(targetRole,targetId,targetKind);}
        String edge(){return source()+">"+target();}
    }
    static final class Group {
        final String historyId,ownershipId,transactionId,originalSnapshotId,attemptedSnapshotId;final long sequence;
        final List<Member> records;final List<Reference> references;final Set<String> checkpointIds;
        private Group(DataInputStream in)throws IOException{
            historyId=id(text(in,false));ownershipId=id(text(in,false));transactionId=id(text(in,false));
            originalSnapshotId=id(text(in,false));attemptedSnapshotId=id(text(in,false));sequence=in.readLong();
            if(sequence<=0||originalSnapshotId.equals(attemptedSnapshotId))throw invalid("Invalid ownership identity/order");
            checkpointIds=ids(in,MAX_MEMBERS);records=members(in,MAX_MEMBERS,true);
            List<Reference> refs=new ArrayList<>();int n=count(in,MAX_MEMBERS+9);
            for(int i=0;i<n;i++)refs.add(new Reference(Role.parse(text(in,false)),text(in,false),Kind.parse(text(in,false)),Role.parse(text(in,false)),text(in,false),Kind.parse(text(in,false))));
            references=freeze(refs);validateGroup(this);
        }
    }
    static final class IdentityProof {
        final String id,exactUri,tabId;final Set<String> origins;
        private IdentityProof(DataInputStream in)throws IOException{
            id=id(text(in,false));exactUri=text(in,true);tabId=text(in,true);origins=ids(in,MAX_IDENTITIES);
            if((exactUri==null)==(tabId==null)||(exactUri!=null&&exactUri.isEmpty())||(tabId!=null&&tabId.isEmpty())||origins.contains(id))throw invalid("Identity routing/provenance");
        }
    }
    static final class PolicyBinding {
        final String bundleId,envelopeHash;final long generation,quotaBytes;final int automaticCount;
        PolicyBinding(String bundleId,long generation,String envelopeHash,long quotaBytes,int automaticCount)throws IOException{
            this.bundleId=id(bundleId);this.envelopeHash=hash(envelopeHash);
            long mib=1024L*1024;
            if(generation<0||!(quotaBytes==64*mib||quotaBytes==128*mib||quotaBytes==256*mib||quotaBytes==512*mib||quotaBytes==1024*mib)
                    ||!(automaticCount==0||automaticCount==5||automaticCount==10||automaticCount==20||automaticCount==50))throw invalid("Effective policy descriptor");
            this.generation=generation;this.quotaBytes=quotaBytes;this.automaticCount=automaticCount;
        }
    }
    static final class Authorization {
        final String previewFingerprint;final Set<String> selectedCheckpointIds;
        Authorization(String previewFingerprint,Collection<String> selected)throws IOException{
            this.previewFingerprint=hash(previewFingerprint);selectedCheckpointIds=canonicalIds(selected,MAX_MEMBERS);
        }
    }

    final PolicyBinding policy;final Authorization authorization;
    final String previewFingerprint,graphFingerprint,unaffectedFingerprint,envelopeHash;
    final HistoryPruner.Mode mode;final long requiredBytes,requiredEntries,bytes,entries;
    final List<Member> members,identityEnvelopes;final List<Group> groups,anchors;final List<IdentityProof> identities;
    final Set<String> selectedCheckpointIds,externalProtectedSnapshotIds;
    final int serializedBytes;
    private final byte[] proof;

    private HistoryPruneIntent(PolicyBinding policy,Authorization authorization,String unaffected,byte[] proof,
            List<Member> identityEnvelopes,int length,String envelopeHash)throws IOException{
        this.policy=policy;this.authorization=authorization;unaffectedFingerprint=hash(unaffected);
        this.proof=proof.clone();previewFingerprint=hex(digest(proof));
        if(!previewFingerprint.equals(authorization.previewFingerprint))throw invalid("Authorization fingerprint mismatch");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(proof));String domain=text(in,false);HistoryPruner.Mode declared;
        if("history-retention-pressure-preview-v1".equals(domain)){
            if(!"AUTOMATIC_PRESSURE".equals(text(in,false)))throw invalid("Pressure proof mode");declared=HistoryPruner.Mode.AUTOMATIC_PRESSURE;
        }else if("history-retention-preview-v1".equals(domain))declared=null;
        else throw invalid("Preview proof domain");
        graphFingerprint=hash(text(in,false));int automatic=in.readUnsignedByte();if(automatic>1)throw invalid("Invalid boolean");
        HistoryPruner.Mode parsed=declared==null?(automatic==1?HistoryPruner.Mode.AUTOMATIC_FLOOR:HistoryPruner.Mode.SELECTED):declared;mode=parsed;
        if((parsed!=HistoryPruner.Mode.SELECTED)!=(automatic==1))throw invalid("Proof mode mismatch");
        requiredBytes=nonnegative(in.readLong());requiredEntries=nonnegative(in.readLong());bytes=nonnegative(in.readLong());entries=nonnegative(in.readLong());
        if(in.readLong()!=policy.quotaBytes||in.readInt()!=policy.automaticCount)throw invalid("Preview/effective policy mismatch");
        if(parsed!=HistoryPruner.Mode.SELECTED&&policy.automaticCount==0)throw invalid("Automatic retention disabled");
        externalProtectedSnapshotIds=ids(in,MAX_MEMBERS);selectedCheckpointIds=ids(in,MAX_MEMBERS);
        if(!selectedCheckpointIds.equals(authorization.selectedCheckpointIds))throw invalid("Authorization selection mismatch");
        if(parsed==HistoryPruner.Mode.SELECTED){if(selectedCheckpointIds.isEmpty()||requiredBytes!=0||requiredEntries!=0)throw invalid("Selected mode demand");}
        else if(!selectedCheckpointIds.isEmpty())throw invalid("Automatic mode selection");
        members=members(in,MAX_MEMBERS,true);groups=groups(in);anchors=groups(in);
        List<IdentityProof> identities=new ArrayList<>();int n=count(in,MAX_MEMBERS);String previous=null;
        for(int i=0;i<n;i++){IdentityProof identity=new IdentityProof(in);ordered(previous,identity.id);previous=identity.id;identities.add(identity);}this.identities=freeze(identities);
        Map<Role,Long> rootEntries=new EnumMap<>(Role.class),rootBytes=new EnumMap<>(Role.class);n=count(in,3);previous=null;
        for(int i=0;i<n;i++){Role role=Role.parse(text(in,false));ordered(previous,role.wire);previous=role.wire;if(role==Role.IDENTITY)throw invalid("Identity cannot be removed");rootEntries.put(role,nonnegative(in.readLong()));rootBytes.put(role,nonnegative(in.readLong()));}
        if(in.available()!=0)throw invalid("Trailing preview proof bytes");
        this.identityEnvelopes=freeze(identityEnvelopes);validate(parsed,rootEntries,rootBytes);
        // Legacy domain mode is derived from its strict boolean, never silently interpreted as pressure.
        serializedBytes=length;this.envelopeHash=envelopeHash;
    }

    static HistoryPruneIntent create(HistoryPruner.DeletePlan plan,HistoryGraph.Graph graph,PolicyBinding policy,
            Authorization authorization,String unaffectedFingerprint)throws IOException{
        if(plan==null||graph==null||policy==null||authorization==null||!graph.complete||graph.pruneBlocked
                ||!plan.graphFingerprint.equals(graph.fingerprint)||graph.policy==null
                ||plan.policy.quotaBytes!=policy.quotaBytes||plan.policy.automaticCount!=policy.automaticCount
                ||graph.policy.quotaBytes!=policy.quotaBytes||graph.policy.automaticCount!=policy.automaticCount)throw invalid("Preview/graph/effective policy mismatch");
        if(!plan.proofFingerprint.equals(authorization.previewFingerprint)||!plan.selectedCheckpointIds.equals(authorization.selectedCheckpointIds))throw invalid("Explicit authorization mismatch");
        // Charge complete framing BEFORE allocating proof bytes or identity descriptor collections.
        long size=HEADER+CHECKSUM+16+8+32+8+4+32+32+32+4+4+16L*authorization.selectedCheckpointIds.size()+4+plan.proofBytes;
        if(size>MAX_BYTES||plan.members.size()>MAX_MEMBERS)throw invalid("Complete intent byte/member limit");
        Set<String> wanted=new HashSet<>();for(ProtectionStore.StoredRecord r:plan.members)wanted.add(r.rootRole+"/"+r.filename);
        for(HistoryPruner.GroupProof anchor:plan.anchors)for(ProtectionStore.StoredRecord r:anchor.records)wanted.add(r.rootRole+"/"+r.filename);
        List<Member> identities=new ArrayList<>();Map<String,Member> nativeRecords=new HashMap<>();
        for(ProtectionStore.StoredRecord r:graph.records){
            if("identity".equals(r.rootRole)){size+=58;if(size>MAX_BYTES)throw invalid("Complete intent identity byte limit");identities.add(nativeMember(r));}
            if(wanted.contains(r.rootRole+"/"+r.filename)){Member m=nativeMember(r);if(nativeRecords.put(m.key(),m)!=null)throw invalid("Duplicate graph envelope");}
        }
        Collections.sort(identities,Comparator.comparing(Member::key));
        ByteArrayOutputStream proof=new ByteArrayOutputStream(plan.proofBytes);HistoryPruner.writeCanonicalProof(plan,proof);
        if(proof.size()!=plan.proofBytes)throw invalid("Preview proof size mismatch");
        HistoryPruneIntent intent=new HistoryPruneIntent(policy,authorization,unaffectedFingerprint,proof.toByteArray(),identities,0,null);
        for(Member member:intent.members)if(!member.same(nativeRecords.get(member.key())))throw invalid("Graph member envelope mismatch");
        for(Group anchor:intent.anchors)for(Member member:anchor.records)if(!member.same(nativeRecords.get(member.key())))throw invalid("Graph anchor envelope mismatch");
        // Explicit supplied graph is an immutable proof snapshot; it does not perform fresh runtime reconciliation.
        return decode(encode(intent));
    }

    static byte[] encode(HistoryPruneIntent intent)throws IOException{
        if(intent==null)throw invalid("Missing intent");long size=HEADER+CHECKSUM+16+8+32+8+4+32+32+32+4+intent.proof.length+4+16L*intent.selectedCheckpointIds.size()+4+58L*intent.identityEnvelopes.size();
        if(size>MAX_BYTES)throw invalid("Complete intent byte limit");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream((int)size);DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt((int)size-HEADER-CHECKSUM);
        uuid(out,intent.policy.bundleId);out.writeLong(intent.policy.generation);out.write(unhex(intent.policy.envelopeHash));out.writeLong(intent.policy.quotaBytes);out.writeInt(intent.policy.automaticCount);
        out.write(unhex(intent.previewFingerprint));out.write(unhex(intent.authorization.previewFingerprint));out.write(unhex(intent.unaffectedFingerprint));
        out.writeInt(intent.proof.length);out.write(intent.proof);out.writeInt(intent.selectedCheckpointIds.size());for(String id:intent.selectedCheckpointIds)uuid(out,id);
        out.writeInt(intent.identityEnvelopes.size());for(Member m:intent.identityEnvelopes){out.writeByte(m.role.code);out.writeByte(m.kind.code);uuid(out,m.id);out.writeLong(m.serializedBytes);out.write(unhex(m.envelopeHash));}
        out.flush();byte[] beforeChecksum=bytes.toByteArray();out.write(digest(beforeChecksum));out.flush();return bytes.toByteArray();
    }

    static HistoryPruneIntent decode(byte[] envelope)throws IOException{
        if(envelope==null||envelope.length<HEADER+CHECKSUM||envelope.length>MAX_BYTES)throw invalid("Intent envelope bound");
        envelope=envelope.clone();DataInputStream in=new DataInputStream(new ByteArrayInputStream(envelope,0,envelope.length-CHECKSUM));
        if(in.readInt()!=MAGIC||in.readInt()!=VERSION||in.readInt()!=envelope.length-HEADER-CHECKSUM)throw invalid("Intent framing");
        MessageDigest digest=HistoryStorage.digest();digest.update(envelope,0,envelope.length-CHECKSUM);
        byte[] checksum=new byte[CHECKSUM];System.arraycopy(envelope,envelope.length-CHECKSUM,checksum,0,CHECKSUM);
        if(!MessageDigest.isEqual(checksum,digest.digest()))throw invalid("Intent checksum");
        PolicyBinding policy=new PolicyBinding(uuid(in),in.readLong(),hash(in),in.readLong(),in.readInt());String preview=hash(in),authorized=hash(in),unaffected=hash(in);
        int proofBytes=in.readInt();if(proofBytes<=0||proofBytes>in.available()-8)throw invalid("Preview subframe bound");
        byte[] proof=new byte[proofBytes];in.readFully(proof);if(!preview.equals(hex(digest(proof))))throw invalid("Preview subframe checksum");
        List<String> selected=new ArrayList<>();int n=count(in,MAX_MEMBERS);String previous=null;
        for(int i=0;i<n;i++){String id=uuid(in);ordered(previous,id);previous=id;selected.add(id);}Authorization authorization=new Authorization(authorized,selected);
        List<Member> identities=new ArrayList<>();n=count(in,MAX_IDENTITIES);if(n>in.available()/58)throw invalid("Identity envelope bound");previous=null;
        for(int i=0;i<n;i++){Role role=Role.code(in.readUnsignedByte());Kind kind=Kind.code(in.readUnsignedByte());
            Member member=new Member(role,kind,uuid(in),in.readLong(),hash(in));if(member.kind!=Kind.IDENTITY)throw invalid("Nonidentity envelope proof");ordered(previous,member.key());previous=member.key();identities.add(member);}
        if(in.available()!=0)throw invalid("Trailing intent bytes");
        return new HistoryPruneIntent(policy,authorization,unaffected,proof,identities,envelope.length,hex(digest(envelope)));
    }

    private void validate(HistoryPruner.Mode parsed,Map<Role,Long> rootEntries,Map<Role,Long> rootBytes)throws IOException{
        if(members.isEmpty()||groups.isEmpty()||groups.size()>MAX_MEMBERS/5||entries!=members.size()||bytes<requiredBytes||entries<requiredEntries)throw invalid("Incomplete capacity proof");
        Map<String,Member> removed=new TreeMap<>(),closure=new TreeMap<>(),survivors=new TreeMap<>();long total=0;
        Map<Role,Long> actualEntries=new EnumMap<>(Role.class),actualBytes=new EnumMap<>(Role.class);
        for(Member member:members){if(member.role==Role.IDENTITY||removed.put(member.key(),member)!=null)throw invalid("Invalid removed member");total=add(total,member.serializedBytes);actualEntries.put(member.role,add(actualEntries.getOrDefault(member.role,0L),1));actualBytes.put(member.role,add(actualBytes.getOrDefault(member.role,0L),member.serializedBytes));}
        if(total!=bytes||!actualEntries.equals(rootEntries)||!actualBytes.equals(rootBytes))throw invalid("Capacity/root accounting mismatch");
        Map<String,Group> fixed=new TreeMap<>();String prior=null;
        for(Group anchor:anchors){ordered(prior,anchor.historyId);prior=anchor.historyId;if(!anchor.checkpointIds.isEmpty()||fixed.put(anchor.historyId,anchor)!=null)throw invalid("Invalid fixed anchor");
            for(Member member:anchor.records)if(removed.containsKey(member.key())||survivors.put(member.key(),member)!=null)throw invalid("Anchor/removal overlap");}
        Set<String> selected=new TreeSet<>(),histories=new TreeSet<>(),groupTransactions=new HashSet<>();long sequence=0;
        for(Group group:groups){if(group.sequence<=sequence||!groupTransactions.add(group.transactionId))throw invalid("Duplicate/order group proof");sequence=group.sequence;
            Group anchor=fixed.get(group.historyId);if(anchor==null||anchor.sequence<=group.sequence)throw invalid("Missing later same-history fixed anchor");histories.add(group.historyId);selected.addAll(group.checkpointIds);
            if(parsed!=HistoryPruner.Mode.SELECTED&&!group.checkpointIds.isEmpty())throw invalid("Named automatic group");
            if(externalProtectedSnapshotIds.contains(group.originalSnapshotId)||externalProtectedSnapshotIds.contains(group.attemptedSnapshotId))throw invalid("External pin removal");
            for(Member member:group.records)if(closure.put(member.key(),member)!=null||!member.same(removed.get(member.key())))throw invalid("Removed group closure mismatch");}
        if(!closure.keySet().equals(removed.keySet())||!selected.equals(selectedCheckpointIds)||!histories.equals(fixed.keySet()))throw invalid("Incomplete deletion/selection/anchor closure");
        Map<String,IdentityProof> routing=new TreeMap<>();for(IdentityProof identity:identities)if(routing.put(identity.id,identity)!=null)throw invalid("Duplicate identity routing");
        if(!histories.equals(routing.keySet()))throw invalid("Incomplete identity routing proof");
        Map<String,Member> identityRecords=new TreeMap<>();prior=null;
        for(Member member:identityEnvelopes){if(member.kind!=Kind.IDENTITY)throw invalid("Identity proof type");ordered(prior,member.key());prior=member.key();identityRecords.put(member.id,member);}
        for(IdentityProof identity:identities){if(!identityRecords.containsKey(identity.id))throw invalid("Missing identity envelope");for(String origin:identity.origins)if(!identityRecords.containsKey(origin))throw invalid("Missing provenance identity envelope");}
    }

    private static void validateGroup(Group group)throws IOException{
        Map<String,Member> records=new HashMap<>();for(Member member:group.records){if(member.kind==Kind.IDENTITY||records.put(member.key(),member)!=null)throw invalid("Duplicate/identity group record");}
        List<String> base=Arrays.asList(key(Role.STORE,group.originalSnapshotId,Kind.SNAPSHOT),key(Role.STORE,group.attemptedSnapshotId,Kind.SNAPSHOT),key(Role.STORE,group.transactionId,Kind.TRANSACTION),key(Role.STORE,group.transactionId,Kind.COMPLETE),key(Role.INDEX,group.ownershipId,Kind.INDEX));
        Set<String> expected=new HashSet<>(base);for(String checkpoint:group.checkpointIds)expected.add(key(Role.CATALOG,checkpoint,Kind.CHECKPOINT));
        if(!records.keySet().equals(expected)||records.size()!=5+group.checkpointIds.size())throw invalid("Incomplete native group members");
        List<String> edges=new ArrayList<>();String transaction=base.get(2),complete=base.get(3),index=base.get(4);edges.add(complete+">"+transaction);
        for(String snapshot:base.subList(0,2)){edges.add(transaction+">"+snapshot);edges.add(complete+">"+snapshot);edges.add(index+">"+snapshot);}
        edges.add(index+">"+transaction);edges.add(index+">"+key(Role.IDENTITY,group.historyId,Kind.IDENTITY));
        if(group.references.size()!=edges.size()+group.checkpointIds.size())throw invalid("Incomplete group reference closure");
        for(int i=0;i<edges.size();i++)if(!edges.get(i).equals(group.references.get(i).edge()))throw invalid("Invalid native group edge/order");
        int i=edges.size();for(String checkpoint:group.checkpointIds){Reference reference=group.references.get(i++);
            if(!key(Role.CATALOG,checkpoint,Kind.CHECKPOINT).equals(reference.source())||!base.subList(0,2).contains(reference.target()))throw invalid("Invalid checkpoint closure edge");}
    }
    private static List<Group> groups(DataInputStream in)throws IOException{int n=count(in,MAX_MEMBERS/5);List<Group> result=new ArrayList<>();for(int i=0;i<n;i++)result.add(new Group(in));return freeze(result);}
    private static Member nativeMember(ProtectionStore.StoredRecord record)throws IOException{
        if(record==null)throw invalid("Missing native envelope");Member member=new Member(Role.parse(record.rootRole),Kind.parse(record.kind),record.id,record.serializedBytes,record.envelopeHash);
        if(!(member.id+"."+member.kind.wire).equals(record.filename))throw invalid("Noncanonical generated native filename");return member;
    }
    private static List<Member> members(DataInputStream in,int max,boolean sorted)throws IOException{
        int n=count(in,max);List<Member> result=new ArrayList<>();String previous=null;
        for(int i=0;i<n;i++){Role role=Role.parse(text(in,false));String file=text(in,false),id=text(in,false);Kind kind=Kind.parse(text(in,false));Member member=new Member(role,kind,id,in.readLong(),text(in,false));
            if(!(member.id+"."+kind.wire).equals(file))throw invalid("Noncanonical generated member filename");if(sorted){ordered(previous,member.key());previous=member.key();}result.add(member);}return freeze(result);
    }
    private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max||n>in.available()/4)throw invalid("Collection bound");return n;}
    private static Set<String> ids(DataInputStream in,int max)throws IOException{int n=count(in,max);List<String> result=new ArrayList<>();String previous=null;for(int i=0;i<n;i++){String id=id(text(in,false));ordered(previous,id);previous=id;result.add(id);}return canonicalIds(result,max);}
    private static Set<String> canonicalIds(Collection<String> input,int max)throws IOException{
        if(input==null||input.size()>max)throw invalid("ID collection bound");Set<String> result=new TreeSet<>();for(String value:input)if(!result.add(id(value)))throw invalid("Duplicate ID");return Collections.unmodifiableSet(result);
    }
    private static String text(DataInputStream in,boolean nullable)throws IOException{
        int n=in.readInt();if(nullable&&n==-1)return null;if(n<0||n>MAX_ALIAS_BYTES||n>in.available())throw invalid("UTF8 field bound");byte[] bytes=new byte[n];in.readFully(bytes);
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException bad){throw invalid("Invalid UTF8 proof");}
    }
    private static void ordered(String previous,String current)throws IOException{if(previous!=null&&previous.compareTo(current)>=0)throw invalid("Duplicate/noncanonical order");}
    private static String key(Role role,String id,Kind kind){return role.wire+"/"+id+"."+kind.wire;}
    private static String id(String value)throws IOException{try{if(value==null||!UUID.fromString(value).toString().equals(value))throw invalid("Noncanonical UUID");return value;}catch(IllegalArgumentException bad){throw invalid("Noncanonical UUID");}}
    private static String hash(String value)throws IOException{if(value==null||!value.matches("[0-9a-f]{64}"))throw invalid("Invalid SHA256 fingerprint");return value;}
    private static String hash(DataInputStream in)throws IOException{byte[] bytes=new byte[32];in.readFully(bytes);return hex(bytes);}
    private static byte[] unhex(String hash)throws IOException{hash(hash);byte[] bytes=new byte[32];for(int i=0;i<32;i++)bytes[i]=(byte)Integer.parseInt(hash.substring(i*2,i*2+2),16);return bytes;}
    private static void uuid(DataOutputStream out,String id)throws IOException{UUID uuid=UUID.fromString(id(id));out.writeLong(uuid.getMostSignificantBits());out.writeLong(uuid.getLeastSignificantBits());}
    private static String uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong()).toString();}
    private static byte[] digest(byte[] bytes)throws IOException{return HistoryStorage.digest().digest(bytes);}
    private static String hex(byte[] bytes){return HistoryStorage.hex(bytes);}
    private static long nonnegative(long value)throws IOException{if(value<0)throw invalid("Negative accounting");return value;}
    private static long add(long a,long b)throws IOException{try{return Math.addExact(a,b);}catch(ArithmeticException overflow){throw invalid("Accounting overflow");}}
    private static <T> List<T> freeze(Collection<T> input){return Collections.unmodifiableList(new ArrayList<>(input));}
    private static IOException invalid(String reason){return new IOException(reason);}
}
