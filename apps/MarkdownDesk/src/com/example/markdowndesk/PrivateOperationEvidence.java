package com.example.markdowndesk;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Lossless inert evidence framing. Success proves wire structure and control-chain bindings only.
 * It does not validate witness preimages, reconstruct failed data, join a source ledger, activate
 * pins, or grant routing/publication authority. Those typed producer/import joins are separate.
 * Raw sinks receive provisional bytes and must discard them if this call fails. The codec closes
 * each returned sink stream; no sink completion callback or durable import is implied. */
public final class PrivateOperationEvidence {
    public static final long MAX_BYTES=1L<<30;
    public static final int MAX_ROWS=65536+260;
    private static final byte[] MAGIC="MDEVID1\n".getBytes(StandardCharsets.US_ASCII);
    private static final UUID ZERO=new UUID(0,0);
    private static final String NIL=hex(new byte[32]),EMPTY=hex(digest().digest(new byte[0]));
    private static final long WORKSPACE=512L<<10;
    private PrivateOperationEvidence() {}

    public static final class Row {
        public final int role,kind,state,futureSlots,relation;
        public final UUID ownerPreparation,nodeId;
        public final String name,declaredHash,actualHash;
        public final long declaredLength,actualLength,futureBytes;
        public Row(int role,UUID ownerPreparation,UUID nodeId,int kind,int state,String name,
                long declaredLength,String declaredHash,long actualLength,String actualHash,
                long futureBytes,int futureSlots,int relation) {
            this.role=role;this.ownerPreparation=ownerPreparation;this.nodeId=nodeId;this.kind=kind;
            this.state=state;this.name=name;this.declaredLength=declaredLength;this.declaredHash=declaredHash;
            this.actualLength=actualLength;this.actualHash=actualHash;this.futureBytes=futureBytes;
            this.futureSlots=futureSlots;this.relation=relation;
        }
    }
    public interface Source {
        long length(); int rowCount(); Cursor open() throws IOException;
    }
    public interface Cursor extends AutoCloseable {
        Row next() throws IOException; InputStream openRaw() throws IOException;
        void close() throws IOException;
    }
    public interface RawSink { OutputStream open(Row row) throws IOException; }
    public static final class Summary implements AutoCloseable {
        public final long length;
        /** Conservative shared-budget debit retained until close, including decoded control owners. */
        public final long metadataBytes;
        public final int rowCount;
        /** SHA256 of the entire capsule, including its terminal checksum. */
        public final String hash;
        private final PrivateBundleInventory.Reservation retained;
        private Summary(long length,int count,String hash,long metadata,PrivateBundleInventory.Reservation retained) {
            this.length=length;rowCount=count;this.hash=hash;metadataBytes=metadata;this.retained=retained;
        }
        public void close(){retained.close();}
    }

    /** Does not close input. A canonical zero-row capsule is accepted as the grammar's empty case;
     * actual portable captures must supply the mandatory source context even with no operations. */
    public static Summary read(InputStream input,RawSink sink,PrivateBundleInventory.MetadataBudget budget)throws IOException {
        require(input!=null&&budget!=null,"Missing evidence input/budget");
        try(PrivateBundleInventory.Reservation workspace=budget.reserve(WORKSPACE)) {
            PrivateBundleInventory.Reservation retained=budget.reserve(56);
            boolean success=false;
            try {
                Wire wire=new Wire(input);DataInputStream in=new DataInputStream(wire);
                byte[] magic=new byte[8];in.readFully(magic);require(Arrays.equals(magic,MAGIC)&&in.readInt()==1,"Evidence framing");
                long length=in.readLong();int count=in.readInt();bounds(length,count);wire.limit=length-32;
                Validation validation=new Validation(retained);byte[] scratch=new byte[32768];
                long metadata=56;
                for(int i=0;i<count;i++) {
                    // Reserve before decoding names, rows, UUIDs or validator indexes.
                    retained.grow(1024);metadata+=1024;
                    int role=in.readUnsignedByte();UUID owner=uuid(in),node=uuid(in);
                    int kind=in.readUnsignedByte(),state=in.readUnsignedByte(),n=in.readUnsignedShort();
                    require(n>=1&&n<=255,"Evidence basename length");byte[] name=new byte[n];in.readFully(name);
                    Row row=new Row(role,owner,node,kind,state,decodeName(name),nonnegative(in.readLong()),hash(in),
                        nonnegative(in.readLong()),hash(in),nonnegative(in.readLong()),in.readInt(),in.readUnsignedByte());
                    long raw=nonnegative(in.readLong());validateRow(row,raw);validation.before(row);
                    require(raw<=wire.limit-wire.count,"Evidence raw exceeds capsule");
                    validation.reserveCapture(row,raw);byte[] capture=capture(row,raw);MessageDigest content=digest();
                    try(OutputStream out=sink==null?null:sink.open(row)) {
                        long left=raw;int position=0;
                        while(left>0) {int take=(int)Math.min(left,scratch.length);in.readFully(scratch,0,take);
                            content.update(scratch,0,take);if(capture!=null){System.arraycopy(scratch,0,capture,position,take);position+=take;}
                            if(out!=null)out.write(scratch,0,take);left-=take;}
                    }
                    require(hex(content.digest()).equals(row.actualHash),"Evidence physical hash");validation.after(row,capture);
                }
                validation.finish(count);require(wire.count==length-32,"Evidence total length");
                byte[] expected=wire.prefix.digest();wire.limit=length;byte[] trailer=new byte[32];in.readFully(trailer);
                require(MessageDigest.isEqual(expected,trailer),"Evidence checksum");
                require(input.read()==-1,"Trailing evidence bytes");
                Summary result=new Summary(length,count,hex(wire.full.digest()),metadata+validation.extraMetadata,retained);success=true;return result;
            } finally {if(!success)retained.close();}
        }
    }

    /** Streams one cursor once; output is provisional until this method returns successfully. */
    public static Summary write(Source source,OutputStream output,PrivateBundleInventory.MetadataBudget budget)throws IOException {
        require(source!=null&&output!=null&&budget!=null,"Missing evidence source/output/budget");
        try(PrivateBundleInventory.Reservation workspace=budget.reserve(WORKSPACE)) {
            PrivateBundleInventory.Reservation retained=budget.reserve(56);boolean success=false;
            try {
                long length=source.length();int count=source.rowCount();bounds(length,count);
                MessageDigest prefix=digest(),full=digest();CountingOutput counted=new CountingOutput(output,full);
                DataOutputStream out=new DataOutputStream(new java.security.DigestOutputStream(counted,prefix));
                out.write(MAGIC);out.writeInt(1);out.writeLong(length);out.writeInt(count);
                Validation validation=new Validation(retained);byte[] scratch=new byte[32768];long metadata=56;
                try(Cursor cursor=source.open()) {
                    require(cursor!=null,"Missing evidence cursor");
                    for(int i=0;i<count;i++) {
                        retained.grow(1024);metadata+=1024;Row row=cursor.next();require(row!=null,"Missing evidence row");
                        validateRow(row,row.actualLength);validation.before(row);byte[] name=nameBytes(row.name);
                        out.writeByte(row.role);uuid(out,row.ownerPreparation);uuid(out,row.nodeId);out.writeByte(row.kind);out.writeByte(row.state);
                        out.writeShort(name.length);out.write(name);out.writeLong(row.declaredLength);hash(out,row.declaredHash);
                        out.writeLong(row.actualLength);hash(out,row.actualHash);out.writeLong(row.futureBytes);out.writeInt(row.futureSlots);
                        out.writeByte(row.relation);out.writeLong(row.actualLength);
                        require(row.actualLength<=length-32-counted.count,"Evidence raw exceeds capsule");
                        validation.reserveCapture(row,row.actualLength);byte[] capture=capture(row,row.actualLength);MessageDigest content=digest();
                        try(InputStream raw=cursor.openRaw()) {
                            require(raw!=null,"Missing raw evidence stream");long left=row.actualLength;int position=0;
                            while(left>0) {int take=(int)Math.min(left,scratch.length),n=raw.read(scratch,0,take);
                                require(n>=0,"Truncated raw evidence");if(n==0){int one=raw.read();require(one>=0,"Truncated raw evidence");scratch[0]=(byte)one;n=1;}
                                content.update(scratch,0,n);if(capture!=null){System.arraycopy(scratch,0,capture,position,n);position+=n;}
                                out.write(scratch,0,n);left-=n;}
                            require(raw.read()==-1,"Trailing raw evidence");
                        }
                        require(hex(content.digest()).equals(row.actualHash),"Evidence physical hash");validation.after(row,capture);
                    }
                    require(cursor.next()==null,"Extra evidence row");
                }
                validation.finish(count);require(counted.count==length-32,"Evidence total length");
                counted.write(prefix.digest());Summary result=new Summary(length,count,hex(full.digest()),metadata+validation.extraMetadata,retained);
                success=true;return result;
            } finally {if(!success)retained.close();}
        }
    }

    private static final class Validation {
        Row previous;long slots,controlBytes,extraMetadata;int controls,contextBits;
        HistoryControlStore.Registration enrollment,registration;
        final TreeMap<Long,HistoryControlStore.Record> policies=new TreeMap<>();
        final Map<UUID,HistoryPinRecord> begins=new HashMap<>(),closes=new HashMap<>();
        final List<HistoryPinRecord> ownedPins=new ArrayList<>();
        final Set<UUID> ownedPinNodes=new HashSet<>();
        final List<PrivateOperationRecord> preparations=new ArrayList<>();
        final PrivateBundleInventory.Reservation reservation;
        Validation(PrivateBundleInventory.Reservation reservation){this.reservation=reservation;}
        void before(Row row)throws IOException {
            require(previous==null||compare(previous,row)<0,"Evidence order/duplicate");
            if(previous!=null&&previous.role==row.role&&previous.ownerPreparation.equals(row.ownerPreparation)) {
                String prior=previous.state==3?previous.name.substring(0,previous.name.length()-6):previous.name;
                String current=row.state==3?row.name.substring(0,row.name.length()-6):row.name;
                require(!prior.equals(current),"Conflicting staged/committed logical node");
            }
            previous=row;
            if(row.role==1||row.role==4&&row.kind<=3) {
                long committed=add(controls,add(row.state==0?0:1,row.futureSlots));
                require(committed<=256,"Source control namespace limit");controls=(int)committed;
                controlBytes=add(controlBytes,add(row.actualLength,row.futureBytes));
                require(controlBytes<=HistoryControlStore.MAX_BYTES,"Source control byte/reservation limit");
            } else if(row.role==2||row.role==3) {
                slots=add(slots,add(row.state==0?0:1,row.futureSlots));
                require(slots<=65536,"Source operation namespace limit");
            }
            if(row.role==1&&row.kind>=3&&!ZERO.equals(row.ownerPreparation))ownedPinNodes.add(row.nodeId);
        }
        void reserveCapture(Row row,long raw)throws IOException {
            long charge=0;
            if(row.role==4&&row.kind>=4||row.role==1&&row.state!=2)charge=raw;
            else if(row.role==4||row.role==1&&row.state==2) {
                require(raw<=16384,"Control envelope bound");charge=4096+4L*raw;
            }
            reservation.grow(charge);extraMetadata+=charge;
        }
        void after(Row row,byte[] raw)throws IOException {
            if(row.role==4) {
                if(row.kind!=3){int bit=1<<row.kind;require((contextBits&bit)==0,"Duplicate source context");contextBits|=bit;}
                if(row.kind<=3) {
                    HistoryControlStore.Record record=HistoryControlStore.decode(raw);
                    if(row.kind==1||row.kind==2) {
                        require(record instanceof HistoryControlStore.Registration,"Registration context type");
                        require(row.name.equals(row.kind==1?HistoryControlStore.ENROLLMENT_NAME:HistoryControlStore.REGISTRATION_NAME),"Registration context name");
                        if(row.kind==1)enrollment=(HistoryControlStore.Registration)record;else registration=(HistoryControlStore.Registration)record;
                    } else {
                        require(record instanceof HistoryControlStore.Policy&&row.name.equals(HistoryControlStore.policyFilename(record.generation)),"Policy context name/type");
                        require(policies.put(record.generation,record)==null,"Duplicate policy generation");
                    }
                }
            } else if(row.role==1&&row.state==2) {
                if(row.kind<=2) {
                    PrivateOperationRecord record=PrivateOperationRecord.decode(raw);
                    require(record.type==row.kind&&record.preparation.equals(row.ownerPreparation)&&record.name().equals(row.name),"Operation control binding");
                    if(row.kind==1)preparations.add(record);
                } else {
                    HistoryPinRecord pin=HistoryPinRecord.decode(raw);
                    require(pin.begin==(row.kind==3)&&pin.operation.equals(row.nodeId)&&pin.name().equals(row.name),"Pin name/identity");
                    if(ZERO.equals(row.ownerPreparation)) {
                        require(!pin.begin||pin.manifest.domain==HistoryPinRecord.Domain.NATIVE_HISTORY_V1,"Unowned nonlegacy pin");
                        require((pin.begin?begins:closes).put(pin.operation,pin)==null,"Duplicate legacy pin");
                    } else ownedPins.add(pin);
                }
            }
        }
        void finish(int count)throws IOException {
            if(count==0)return;
            require(contextBits==((1<<1)|(1<<2)|(1<<4)|(1<<5)|(1<<6)|(1<<7)),"Missing source context");
            require(enrollment!=null&&registration!=null&&enrollment.envelopeHash.equals(registration.envelopeHash),"Enrollment registration mismatch");
            HistoryControlStore.Record previous=registration;
            for(HistoryControlStore.Record record:policies.values()) {
                HistoryControlStore.Policy policy=(HistoryControlStore.Policy)record;
                require(previous.generation<Long.MAX_VALUE&&policy.generation==previous.generation+1&&policy.bundleId.equals(registration.bundleId)
                    &&policy.previousHash.equals(previous.envelopeHash),"Incomplete historical policy chain");previous=policy;
            }
            policies.put(0L,registration);
            require(begins.size()==closes.size(),"Unmatched legacy pin");
            for(HistoryPinRecord begin:begins.values()) {
                HistoryPinRecord close=closes.get(begin.operation);require(close!=null&&HistoryPinRecord.matches(begin,close),"Legacy CLOSE binding");bound(begin);
                require(!ownedPinNodes.contains(begin.operation),"Conflicting owned/staged legacy control");
                for(PrivateOperationRecord prepare:preparations)require(!prepare.operation.equals(begin.operation),"Legacy pin has preparation association");
            }
            for(HistoryPinRecord pin:ownedPins)if(pin.begin)bound(pin);
            for(PrivateOperationRecord prepare:preparations) {
                PrivateOperationRecord.Binding binding=prepare.binding;HistoryControlStore.Record policy=policies.get(binding.generation);
                require(policy!=null&&binding.bundle.equals(registration.bundleId)&&binding.registrationHash.equals(registration.envelopeHash)
                    &&binding.policyHash.equals(policy.envelopeHash)&&binding.bootstrapQuota==registration.bootstrapQuotaBytes
                    &&binding.quota==policy.quotaBytes&&binding.automaticCount==policy.automaticCount,"Historical preparation policy binding");
                for(int i=0;i<4;i++)require(binding.roles.get(i).basename.equals(registration.roles.get(i).basename),"Historical preparation role binding");
            }
        }
        void bound(HistoryPinRecord begin)throws IOException {
            HistoryControlStore.Record policy=policies.get(begin.generation);
            require(policy!=null&&begin.bundle.equals(registration.bundleId)&&begin.registrationHash.equals(registration.envelopeHash)
                &&begin.policyHash.equals(policy.envelopeHash),"Historical pin policy binding");
        }
    }
    private static byte[] capture(Row row,long raw)throws IOException {
        if(row.role==4&&row.kind<=3||row.role==1&&row.state==2) {
            require(raw<=16384,"Control envelope bound");return new byte[(int)raw];
        }
        return null;
    }
    private static void validateRow(Row row,long raw)throws IOException {
        require(row!=null&&row.ownerPreparation!=null&&row.nodeId!=null,"Missing row identity");nameBytes(row.name);
        require(row.role>=1&&row.role<=4&&row.state>=0&&row.state<=3,"Row role/state");
        require(row.declaredLength>=0&&row.declaredLength<=MAX_BYTES&&row.actualLength>=0&&row.actualLength<=MAX_BYTES
            &&row.futureBytes>=0&&row.futureBytes<=MAX_BYTES&&row.futureSlots>=0&&row.futureSlots<=65536,"Row bounds");
        validHash(row.declaredHash);validHash(row.actualHash);
        require(row.relation==0,"Prefix relation requires external byte comparison");
        require(raw==row.actualLength,"Raw physical length");
        if(row.state<=1)require(raw==0&&EMPTY.equals(row.actualHash),"Absent/directory physical bytes");
        if(row.state==2)require(row.declaredLength==raw&&row.declaredHash.equals(row.actualHash)&&row.futureBytes==0,"Committed file commitment");
        if(row.state==0||row.state==3)require(add(raw,row.futureBytes)==row.declaredLength,"Unavailable file reservation commitment");
        require(row.futureSlots==(row.state==0?1:0),"Physical/future slot commitment");
        if(row.role==4) {
            require(row.kind>=1&&row.kind<=7&&row.state==2&&ZERO.equals(row.ownerPreparation)&&ZERO.equals(row.nodeId)
                &&row.futureSlots==0,"Source context fields");
            if(row.kind>=4) {
                String[] names={"baseline-c0","baseline-ledger","baseline-graph","baseline-native"};
                require(row.name.equals(names[row.kind-4]),"Source witness basename");
            }
            return;
        }
        if(row.role==3) {
            require(row.kind==0&&row.state<=1&&row.declaredLength==0&&NIL.equals(row.declaredHash)&&row.futureBytes==0,"Namespace directory fields");
            if(ZERO.equals(row.ownerPreparation))require(ZERO.equals(row.nodeId)&&row.name.equals("private_operations"),"Namespace root identity");
            else require(generated(row.ownerPreparation)&&row.nodeId.equals(row.ownerPreparation)&&row.name.equals(row.ownerPreparation.toString()),"Preparation directory identity");
            return;
        }
        require(row.state!=1,"File row directory state");require(generated(row.nodeId),"Generated node UUID required");
        require(row.state!=0||!NIL.equals(row.declaredHash),"Absent file must retain declared hash");
        if(row.role==1) {
            require(row.kind>=1&&row.kind<=4,"Control kind");
            if(ZERO.equals(row.ownerPreparation))require(row.kind>=3&&row.state==2&&row.futureSlots==0,"Unowned control must be completed legacy");
            else require(generated(row.ownerPreparation),"Preparation UUID required");
            String name=row.kind<=2?"prep-"+row.ownerPreparation+(row.kind==1?".reserve":".link"):
                "pin-"+row.nodeId+(row.kind==3?".begin":".close");
            require(row.name.equals(name+(row.state==3?".stage":"")),"Canonical control name");
            if(row.kind<=2)require(row.nodeId.equals(row.ownerPreparation),"Preparation control node");
            require(row.declaredLength<=16384,"Control declaration bound");
        } else {
            require(generated(row.ownerPreparation)&&row.kind>=1&&row.kind<=8,"Artifact owner/kind");
            String stem=row.kind==1?"map-":row.kind==2?"capture-":row.kind==3?"inventory-":row.kind==4?"evidence-":row.kind==5?"sources-":"state-";
            String suffix=row.kind==1?".native":row.kind==2?".descriptor":row.kind==3?".bundle":row.kind==4?".capsule":row.kind==5?".catalog":".frame";
            require(row.name.equals(stem+row.nodeId+suffix+(row.state==3?".stage":"")),"Canonical artifact name");
            long cap=row.kind==4?MAX_BYTES:row.kind<=5?16L<<20:
                row.kind==8?PrivateStateCodec.MAX_PREFERENCES_BYTES:PrivateStateCodec.MAX_PAYLOAD_BYTES+PrivateStateCodec.MAX_OVERHEAD_BYTES;
            require(row.declaredLength<=cap,"Artifact declaration bound");
        }
    }
    private static void bounds(long length,int count)throws IOException {
        require(length>=56&&length<=MAX_BYTES&&count>=0&&count<=MAX_ROWS&&count<=(length-56)/139,"Evidence length/count");
    }
    private static int compare(Row a,Row b)throws IOException {
        int c=Integer.compare(a.role,b.role);if(c==0)c=Long.compareUnsigned(a.ownerPreparation.getMostSignificantBits(),b.ownerPreparation.getMostSignificantBits());
        if(c==0)c=Long.compareUnsigned(a.ownerPreparation.getLeastSignificantBits(),b.ownerPreparation.getLeastSignificantBits());
        if(c==0){byte[] x=nameBytes(a.name),y=nameBytes(b.name);for(int i=0;i<Math.min(x.length,y.length);i++)if(x[i]!=y[i])return Integer.compare(x[i]&255,y[i]&255);c=Integer.compare(x.length,y.length);}return c;
    }
    private static byte[] nameBytes(String name)throws IOException {
        require(name!=null&&name.length()>=1&&name.length()<=255,"Missing/oversized basename");byte[] bytes;
        try {ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(name));bytes=new byte[encoded.remaining()];encoded.get(bytes);}
        catch(CharacterCodingException invalid){throw new IOException("Invalid basename UTF8",invalid);}
        require(bytes.length>=1&&bytes.length<=255&&!name.equals(".")&&!name.equals("..")&&name.indexOf('/')<0&&name.indexOf('\\')<0&&name.indexOf('\0')<0,"Invalid single basename");return bytes;
    }
    private static String decodeName(byte[] bytes)throws IOException {
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException invalid){throw new IOException("Invalid basename UTF8",invalid);}
    }
    private static boolean generated(UUID id){return id.version()==4&&id.variant()==2;}
    private static long nonnegative(long value)throws IOException{require(value>=0,"Unsigned value outside supported range");return value;}
    private static long add(long a,long b)throws IOException{require(a>=0&&b>=0&&a<=Long.MAX_VALUE-b,"Evidence counter overflow");return a+b;}
    private static void validHash(String value)throws IOException{require(value!=null&&value.matches("[0-9a-f]{64}"),"Evidence SHA256");}
    private static String hash(DataInputStream in)throws IOException{byte[] bytes=new byte[32];in.readFully(bytes);return hex(bytes);}
    private static void hash(DataOutputStream out,String value)throws IOException{validHash(value);for(int i=0;i<64;i+=2)out.writeByte(Integer.parseInt(value.substring(i,i+2),16));}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static String hex(byte[] bytes){char[] chars=new char[bytes.length*2];char[] alphabet="0123456789abcdef".toCharArray();for(int i=0;i<bytes.length;i++){chars[i*2]=alphabet[(bytes[i]>>>4)&15];chars[i*2+1]=alphabet[bytes[i]&15];}return new String(chars);}
    private static void require(boolean yes,String message)throws IOException{if(!yes)throw new IOException(message);}
    private static final class Wire extends InputStream {
        final InputStream input;final MessageDigest prefix=digest(),full=digest();long count,limit=MAX_BYTES;
        Wire(InputStream input){this.input=input;}
        public int read()throws IOException{require(count<limit,"Evidence boundary");int b=input.read();if(b>=0){prefix.update((byte)b);full.update((byte)b);count++;}return b;}
        public int read(byte[] b,int off,int len)throws IOException{if(len==0)return 0;require(count<limit,"Evidence boundary");int n=input.read(b,off,(int)Math.min(len,limit-count));if(n==0){int one=read();if(one<0)return -1;b[off]=(byte)one;return 1;}if(n>0){prefix.update(b,off,n);full.update(b,off,n);count+=n;}return n;}
    }
    private static final class CountingOutput extends OutputStream {
        final OutputStream out;final MessageDigest hash;long count;
        CountingOutput(OutputStream out,MessageDigest hash){this.out=out;this.hash=hash;}
        public void write(int b)throws IOException{out.write(b);hash.update((byte)b);count++;}
        public void write(byte[] b,int off,int len)throws IOException{out.write(b,off,len);hash.update(b,off,len);count+=len;}
    }
}
