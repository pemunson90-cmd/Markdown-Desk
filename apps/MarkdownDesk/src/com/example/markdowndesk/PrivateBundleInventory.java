package com.example.markdowndesk;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Pure scoped inventory. Hashes and historical coordinates grant no authority.
 * Rows are borrowed immutable views. Closing releases logical accounting, not escaped Java objects:
 * escaped rows/lists stay readable. This is not a GC or arbitrary-retaining-caller heap guarantee.
 * Coordinators must retain this owner through all borrowed consumers and their quiescence,
 * separately reserve retained copies, and audit these lifetimes before integrating adoption.
 * Native/private payload validation and durable staging are deliberately outside this codec. */
public final class PrivateBundleInventory implements AutoCloseable {
    public static final long MAX_METADATA_BYTES = 16L << 20;
    private static final byte[] MAGIC = "MDBUNDL\n".getBytes(StandardCharsets.US_ASCII);

    /** Shared by all simultaneously live codec, typed-validation and remapping metadata. */
    public static final class MetadataBudget {
        private final long limit;
        private long used;
        public MetadataBudget() { this(MAX_METADATA_BYTES); }
        public MetadataBudget(long limit) {
            if (limit < 0 || limit > MAX_METADATA_BYTES) throw new IllegalArgumentException("Metadata limit");
            this.limit = limit;
        }
        public synchronized long usedBytes() { return used; }
        public synchronized Reservation reserve(long bytes) throws IOException {
            if (bytes < 0 || bytes > limit - used) throw new IOException("Combined metadata limit exceeded");
            // Allocate the token before committing credit: allocation failure strands no charge.
            Reservation reservation = new Reservation(this, bytes);
            used += bytes;
            return reservation;
        }
        private synchronized void release(long bytes) { used -= bytes; }
    }
    public static final class Reservation implements AutoCloseable {
        private MetadataBudget budget;
        private final long bytes;
        private Reservation(MetadataBudget budget, long bytes) { this.budget=budget; this.bytes=bytes; }
        public synchronized void close() { if (budget != null) { budget.release(bytes); budget=null; } }
    }
    private static final class Account implements AutoCloseable {
        final MetadataBudget budget;
        long bytes;
        Account(MetadataBudget budget) { this.budget=budget; }
        void add(long n) throws IOException {
            synchronized (budget) {
                if (n < 0 || n > budget.limit-budget.used) throw new IOException("Combined metadata limit exceeded");
                budget.used += n; bytes += n;
            }
        }
        public void close() { budget.release(bytes); bytes=0; }
    }
    public static final class Policy {
        public final UUID sourceBundleId;
        public final long generation, quotaBytes;
        public final int automaticCount;
        public final String sha256;
        private Policy(UUID id,long generation,long quota,int count,String hash) {
            sourceBundleId=id; this.generation=generation; quotaBytes=quota; automaticCount=count; sha256=hash;
        }
    }
    public static final class Scope {
        public final UUID id, activeEntryId;
        public final int kind, flags, entryCount;
        public final List<UUID> origins, tabs;
        public final Policy policy;
        private Scope(UUID id,int kind,int flags,UUID[] origins,int count,UUID active,UUID[] tabs,Policy policy) {
            this.id=id; this.kind=kind; this.flags=flags; this.origins=view(origins); entryCount=count;
            activeEntryId=active; this.tabs=view(tabs); this.policy=policy;
        }
    }
    public static final class DirectoryEntry {
        public final UUID id, scopeId, identity;
        public final int type;
        public final long length;
        public final String sha256;
        public final List<UUID> lineages;
        private DirectoryEntry(UUID id,UUID scope,int type,long length,String hash,UUID identity,UUID[] edges) {
            this.id=id; scopeId=scope; this.type=type; this.length=length; sha256=hash;
            this.identity=identity; lineages=view(edges);
        }
    }
    public static final class Origin {
        public final UUID id, sourceScopeId;
        public final String sourceArchiveSha256;
        public final List<UUID> parents;
        private Origin(UUID id,String hash,UUID scope,UUID[] parents) {
            this.id=id; sourceArchiveSha256=hash; sourceScopeId=scope; this.parents=view(parents);
        }
    }
    public static final class Coordinate {
        public final int tag;
        public final String value;
        private Coordinate(int tag,String value) { this.tag=tag; this.value=value; }
    }
    public static final class Lineage {
        public final UUID id, entryId, originId, sourceIdentity;
        public final int sourceType;
        public final List<Coordinate> coordinates;
        private int entryIndex;
        private Lineage(UUID id,UUID entry,UUID origin,int type,UUID identity,Coordinate[] coordinates) {
            this.id=id; entryId=entry; originId=origin; sourceType=type; sourceIdentity=identity;
            this.coordinates=view(coordinates);
        }
    }
    public final UUID preparationId, rootScopeId;
    public final long serializedLength, payloadBytes, archiveBytes;
    public final String sha256;
    private final Account account;
    private Scope[] scopes;
    private DirectoryEntry[] entries;
    private Origin[] origins;
    private Lineage[] lineages;
    private PrivateBundleInventory(UUID prep,UUID root,long length,long payload,long archive,String hash,
            Account account,Scope[] scopes,DirectoryEntry[] entries,Origin[] origins,Lineage[] lineages) {
        preparationId=prep; rootScopeId=root; serializedLength=length; payloadBytes=payload; archiveBytes=archive;
        sha256=hash; this.account=account; this.scopes=scopes; this.entries=entries; this.origins=origins; this.lineages=lineages;
    }
    private static <T> List<T> view(T[] array) { return Collections.unmodifiableList(Arrays.asList(array)); }
    private void open() { if (entries==null) throw new IllegalStateException("Inventory closed"); }
    public List<Scope> scopes() { open(); return view(scopes); }
    public List<DirectoryEntry> entries() { open(); return view(entries); }
    public List<Origin> origins() { open(); return view(origins); }
    public List<Lineage> lineages() { open(); return view(lineages); }
    public void close() { if (entries!=null) { scopes=null; entries=null; origins=null; lineages=null; account.close(); } }

    /** Streams and validates all inventory metadata before returning; does not close input. */
    public static PrivateBundleInventory read(InputStream input,MetadataBudget budget) throws IOException {
        if (input==null || budget==null) throw new IOException("Missing inventory input/budget");
        Account account=new Account(budget);
        try {
            account.add(4096);
            Wire w=new Wire(input); DataInputStream in=new DataInputStream(w);
            byte[] magic=new byte[8]; in.readFully(magic);
            require(Arrays.equals(magic,MAGIC)&&in.readInt()==1,"Unknown inventory format");
            int length=count(in,(int)MAX_METADATA_BYTES); require(length>=144,"Inventory length");
            w.limit=length-32;
            UUID prep=generated(in),root=generated(in);
            int b=count(in,64),e=count(in,16380),o=count(in,256),g=count(in,65536);
            require(b>0,"Missing scope");
            // Extra lineage/origin slots cover decoded rows, validation sort arrays and UUID objects.
            account.add(4096L*b+1024L*(e+1)+384L*o+512L*g);
            long payload=positive(in.readLong()),archive=positive(in.readLong());
            Scope[] scopes=new Scope[b]; DirectoryEntry[] entries=new DirectoryEntry[e];
            Origin[] origins=new Origin[o]; Lineage[] edges=new Lineage[g];
            UUID previous=null;
            for(int i=0;i<b;i++) {
                UUID id=generated(in); ordered(previous,id); previous=id;
                int kind=in.readUnsignedByte(),flags=in.readUnsignedByte();
                require((kind==1||kind==2)&&flags>=1&&flags<=3&&(kind!=1||flags==3),"Scope kind/flags");
                UUID[] refs=refs(in,o,account,true); int n=count(in,e);
                UUID active=marker(in)?generated(in):null;
                UUID[] tabs=refs(in,12,account,false);
                require((tabs.length==0)==(active==null),"Active tab marker");
                Policy policy=null;
                if(marker(in)) {
                    UUID source=uuid(in); long generation=positive(in.readLong()),quota=positive(in.readLong());
                    int auto=count(in,50); String hash=hash(in);
                    require((flags&1)!=0 && (quota==(64L<<20)||quota==(128L<<20)||quota==(256L<<20)
                            ||quota==(512L<<20)||quota==(1024L<<20)) && (auto==0||auto==5||auto==10||auto==20||auto==50),"Inert policy bounds");
                    policy=new Policy(source,generation,quota,auto,hash);
                }
                scopes[i]=new Scope(id,kind,flags,refs,n,active,tabs,policy);
            }
            previous=null; long sum=0;
            for(int i=0;i<e;i++) {
                UUID id=generated(in); ordered(previous,id); previous=id; UUID scope=generated(in);
                int type=in.readUnsignedByte(); require(type>=1&&type<=9,"Directory type");
                long n=positive(in.readLong()); require(n<=cap(type),"Directory payload cap"); sum+=n;
                String hash=hash(in); UUID identity=privateType(type)?generated(in):uuid(in);
                entries[i]=new DirectoryEntry(id,scope,type,n,hash,identity,refs(in,g,account,true));
            }
            previous=null;
            for(int i=0;i<o;i++) {
                UUID id=generated(in); ordered(previous,id); previous=id;
                origins[i]=new Origin(id,hash(in),generated(in),refs(in,o-1,account,true));
            }
            previous=null;
            for(int i=0;i<g;i++) {
                UUID id=generated(in); ordered(previous,id); previous=id;
                UUID entry=generated(in),origin=generated(in); int type=in.readUnsignedByte();
                require(type>=1&&type<=9,"Lineage type"); UUID identity=privateType(type)?generated(in):uuid(in);
                int n=count(in,2); Coordinate[] coords=new Coordinate[n]; int last=0;
                for(int j=0;j<n;j++) {
                    int tag=in.readUnsignedByte(); require(tag>last,"Coordinate order"); last=tag;
                    coords[j]=new Coordinate(tag,text(in,account));
                }
                require(tags(type,coords),"Source coordinate tags");
                edges[i]=new Lineage(id,entry,origin,type,identity,coords);
            }
            require(sum==payload && archive==122L+length+58L*e+sum && archive<=PrivateArchive.MAX_ARCHIVE_BYTES,"Inventory totals");
            require(w.count==length-32,"Inventory measured length");
            byte[] expected=w.body.digest(); w.checksum=true; w.limit=length;
            byte[] checksum=new byte[32]; in.readFully(checksum);
            require(MessageDigest.isEqual(expected,checksum),"Inventory checksum");
            require(input.read()==-1,"Trailing inventory bytes");
            PrivateBundleInventory result=new PrivateBundleInventory(prep,root,length,payload,archive,hex(w.full.digest()),account,scopes,entries,origins,edges);
            result.validate(); return result;
        } catch(IOException|RuntimeException|Error failure) { account.close(); throw failure; }
    }

    private void validate() throws IOException {
        int roots=0;
        for(Scope s:scopes) {
            if(s.kind==1) { roots++; require(s.id.equals(rootScopeId),"Root scope reference"); }
            int count=0,tabs=0,recovery=0,prefs=0;
            for(DirectoryEntry e:entries) if(e.scopeId.equals(s.id)) {
                count++; require((s.flags&(privateType(e.type)?2:1))!=0,"Scope presence flag");
                if(e.type==6) tabs++; if(e.type==7) recovery++; if(e.type==8) prefs++;
            }
            require(count==s.entryCount&&tabs==s.tabs.size()&&tabs<=12&&recovery<=1&&prefs<=1,"Scope membership/count");
            for(int i=0;i<s.tabs.size();i++) {
                UUID tab=s.tabs.get(i); DirectoryEntry e=entry(tab);
                require(e!=null&&e.type==6&&e.scopeId.equals(s.id),"Tab reference");
                for(int j=0;j<i;j++) require(!tab.equals(s.tabs.get(j)),"Duplicate tab");
            }
            require(s.activeEntryId==null||s.tabs.contains(s.activeEntryId),"Active tab reference");
        }
        require(roots==1,"Root count");
        for(DirectoryEntry e:entries) require(scope(e.scopeId)!=null,"Missing scope");
        DirectoryEntry[] sorted=entries.clone();
        Arrays.sort(sorted,(a,b)-> {int c=compare(a.scopeId,b.scopeId); if(c==0)c=Integer.compare(privateType(a.type)?6:a.type,privateType(b.type)?6:b.type); if(c==0)c=compare(a.identity,b.identity); return c;});
        for(int i=1;i<sorted.length;i++) {
            DirectoryEntry a=sorted[i-1],b=sorted[i];
            require(!a.scopeId.equals(b.scopeId)||!(a.type==b.type||privateType(a.type)&&privateType(b.type))||!a.identity.equals(b.identity),"Duplicate scoped identity");
        }
        boolean[] owned=new boolean[lineages.length],reachable=new boolean[origins.length];
        int[] state=new int[origins.length];
        for(int i=0;i<origins.length;i++) visit(i,state);
        for(Scope s:scopes) for(UUID id:s.origins) reach(originIndex(id),reachable);
        for(DirectoryEntry e:entries) for(UUID id:e.lineages) {
            int i=edgeIndex(id); require(i>=0&&!owned[i]&&lineages[i].entryId.equals(e.id),"Lineage ownership"); owned[i]=true;
        }
        for(int i=0;i<lineages.length;i++) {
            Lineage edge=lineages[i]; edge.entryIndex=entryIndex(edge.entryId);
            require(owned[i]&&edge.entryIndex>=0&&entries[edge.entryIndex].type==edge.sourceType,"Lineage entry/type");
            reach(originIndex(edge.originId),reachable);
        }
        for(boolean r:reachable) require(r,"Unattached origin");
        Lineage[] order=lineages.clone();
        Arrays.sort(order,(a,b)->{int c=compare(a.entryId,b.entryId);return c!=0?c:compare(a.originId,b.originId);});
        for(int i=1;i<order.length;i++) require(!order[i-1].entryId.equals(order[i].entryId)||!order[i-1].originId.equals(order[i].originId),"Duplicate entry/origin mapping");
        for(int reverse=0;reverse<2;reverse++) {
            final boolean inverse=reverse==1;
            Arrays.sort(order,(a,b)->mappingCompare(a,b,inverse));
            for(int i=1;i<order.length;i++) {
                Lineage a=order[i-1],b=order[i];
                if(mappingGroup(a,b)==0) {
                    UUID ac=entries[a.entryIndex].identity,bc=entries[b.entryIndex].identity;
                    if((inverse?a.sourceIdentity:ac).equals(inverse?b.sourceIdentity:bc))
                        require((inverse?ac:a.sourceIdentity).equals(inverse?bc:b.sourceIdentity),"Ambiguous lineage mapping");
                }
            }
        }
    }
    private int mappingGroup(Lineage a,Lineage b) {
        int c=compare(entries[a.entryIndex].scopeId,entries[b.entryIndex].scopeId);
        if(c==0)c=compare(a.originId,b.originId); if(c==0)c=Integer.compare(domain(a.sourceType),domain(b.sourceType)); return c;
    }
    private int mappingCompare(Lineage a,Lineage b,boolean reverse) {
        int c=mappingGroup(a,b); if(c==0)c=compare(reverse?a.sourceIdentity:entries[a.entryIndex].identity,reverse?b.sourceIdentity:entries[b.entryIndex].identity); return c;
    }
    private static int domain(int type) { return type==3?2:privateType(type)?6:type; }
    private void visit(int i,int[] state) throws IOException {
        require(i>=0,"Missing origin"); require(state[i]!=1,"Origin cycle"); if(state[i]==2)return;
        state[i]=1; for(UUID id:origins[i].parents)visit(originIndex(id),state); state[i]=2;
    }
    private void reach(int i,boolean[] seen) throws IOException {
        require(i>=0,"Missing origin"); if(seen[i])return; seen[i]=true;
        for(UUID id:origins[i].parents)reach(originIndex(id),seen);
    }
    private int find(UUID id,int kind) {
        int lo=0,hi=(kind==0?scopes.length:kind==1?entries.length:kind==2?origins.length:lineages.length)-1;
        while(lo<=hi) {int m=(lo+hi)>>>1; UUID key=kind==0?scopes[m].id:kind==1?entries[m].id:kind==2?origins[m].id:lineages[m].id; int c=compare(key,id); if(c==0)return m; if(c<0)lo=m+1;else hi=m-1;} return -1;
    }
    private Scope scope(UUID id) {int i=find(id,0);return i<0?null:scopes[i];}
    private int entryIndex(UUID id) {return find(id,1);}
    private DirectoryEntry entry(UUID id) {int i=entryIndex(id);return i<0?null:entries[i];}
    private int originIndex(UUID id) {return find(id,2);}
    private int edgeIndex(UUID id) {return find(id,3);}

    /** Canonical serialization, without buffering payload or closing/flushing caller output. */
    public void write(OutputStream output) throws IOException {
        open(); if(output==null)throw new IOException("Missing inventory output");
        MessageDigest hash=digest();
        DataOutputStream out=new DataOutputStream(new OutputStream(){
            public void write(int b)throws IOException {output.write(b);hash.update((byte)b);}
            public void write(byte[] b,int off,int n)throws IOException {output.write(b,off,n);hash.update(b,off,n);}
        });
        out.write(MAGIC);out.writeInt(1);out.writeInt((int)serializedLength);put(out,preparationId);put(out,rootScopeId);
        out.writeInt(scopes.length);out.writeInt(entries.length);out.writeInt(origins.length);out.writeInt(lineages.length);out.writeLong(payloadBytes);out.writeLong(archiveBytes);
        for(Scope s:scopes) {
            put(out,s.id);out.writeByte(s.kind);out.writeByte(s.flags);putRefs(out,s.origins);out.writeInt(s.entryCount);
            out.writeByte(s.activeEntryId==null?0:1);if(s.activeEntryId!=null)put(out,s.activeEntryId);putRefs(out,s.tabs);
            out.writeByte(s.policy==null?0:1);if(s.policy!=null){Policy p=s.policy;put(out,p.sourceBundleId);out.writeLong(p.generation);out.writeLong(p.quotaBytes);out.writeInt(p.automaticCount);putHash(out,p.sha256);}
        }
        for(DirectoryEntry e:entries){put(out,e.id);put(out,e.scopeId);out.writeByte(e.type);out.writeLong(e.length);putHash(out,e.sha256);put(out,e.identity);putRefs(out,e.lineages);}
        for(Origin o:origins){put(out,o.id);putHash(out,o.sourceArchiveSha256);put(out,o.sourceScopeId);putRefs(out,o.parents);}
        for(Lineage l:lineages){put(out,l.id);put(out,l.entryId);put(out,l.originId);out.writeByte(l.sourceType);put(out,l.sourceIdentity);out.writeInt(l.coordinates.size());for(Coordinate c:l.coordinates){out.writeByte(c.tag);writeText(out,c.value);}}
        output.write(hash.digest());
    }
    private void writeText(DataOutputStream out,String value)throws IOException {
        // A UTF-8 copy exists only while its corresponding reservation is live.
        try(Reservation scratch=account.budget.reserve(4L*value.length()+64)) {
            byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);
        }
    }
    private static void put(DataOutputStream out,UUID id)throws IOException {out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static void putRefs(DataOutputStream out,List<UUID> ids)throws IOException {out.writeInt(ids.size());for(UUID id:ids)put(out,id);}
    static void putHash(DataOutputStream out,String hash)throws IOException {for(int i=0;i<64;i+=2)out.writeByte(Integer.parseInt(hash.substring(i,i+2),16));}
    private static UUID[] refs(DataInputStream in,int cap,Account a,boolean sorted)throws IOException {
        int n=count(in,cap);
        // UUID object + array reference (including uncompressed references), plus array/list wrappers.
        a.add(48L*n+96); UUID[] ids=new UUID[n];
        for(int i=0;i<n;i++){ids[i]=generated(in);if(sorted&&i>0)ordered(ids[i-1],ids[i]);}return ids;
    }
    private static String text(DataInputStream in,Account a)throws IOException {
        int n=count(in,4096);require(n>0,"Empty coordinate");a.add(64L+2L*n);
        try(Reservation scratch=a.budget.reserve(4L*n+256)) {
            byte[] bytes=new byte[n];in.readFully(bytes);
            try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
            catch(CharacterCodingException ex){throw new IOException("Malformed coordinate UTF-8",ex);}
        }
    }
    private static boolean tags(int type,Coordinate[] c) {
        if(type<=4)return c.length==1&&c[0].tag==1;
        if(type==5)return c.length==0||c.length==1&&c[0].tag==4;
        if(type==6)return c.length==1&&c[0].tag==5;
        if(type==9)return c.length==2&&c[0].tag==2&&c[1].tag==3;
        return c.length==0;
    }
    private static boolean marker(DataInputStream in)throws IOException {int b=in.readUnsignedByte();require(b<=1,"Optional marker");return b==1;}
    private static int count(DataInputStream in,int max)throws IOException {long n=Integer.toUnsignedLong(in.readInt());require(n<=max,"Inventory count limit");return (int)n;}
    private static long positive(long n)throws IOException {require(n>=0,"Unsigned length overflow");return n;}
    private static UUID uuid(DataInputStream in)throws IOException {return new UUID(in.readLong(),in.readLong());}
    private static UUID generated(DataInputStream in)throws IOException {UUID id=uuid(in);requireGenerated(id);return id;}
    static void requireGenerated(UUID id)throws IOException {require(id!=null&&id.version()==4&&id.variant()==2,"Invalid generated UUID");}
    static int compare(UUID a,UUID b) {int c=Long.compareUnsigned(a.getMostSignificantBits(),b.getMostSignificantBits());return c!=0?c:Long.compareUnsigned(a.getLeastSignificantBits(),b.getLeastSignificantBits());}
    private static void ordered(UUID a,UUID b)throws IOException {require(a==null||compare(a,b)<0,"Noncanonical UUID order");}
    private static boolean privateType(int type){return type>=6&&type<=8;}
    private static long cap(int type){return type==1||type==6||type==7?PrivateArchive.MAX_DOCUMENT_ENTRY_BYTES:PrivateArchive.MAX_METADATA_ENTRY_BYTES;}
    private static String hash(DataInputStream in)throws IOException {byte[] b=new byte[32];in.readFully(b);return hex(b);}
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException ex){throw new AssertionError(ex);}}
    private static String hex(byte[] b){char[] out=new char[b.length*2];String digits="0123456789abcdef";for(int i=0;i<b.length;i++){out[2*i]=digits.charAt((b[i]&255)>>>4);out[2*i+1]=digits.charAt(b[i]&15);}return new String(out);}
    private static final class Wire extends InputStream {
        final InputStream input;final MessageDigest body=digest(),full=digest();long count,limit=MAX_METADATA_BYTES;boolean checksum;
        Wire(InputStream input){this.input=input;}
        public int read()throws IOException {if(count>=limit)throw new IOException("Inventory declared length exhausted");int b=input.read();if(b>=0){count++;full.update((byte)b);if(!checksum)body.update((byte)b);}return b;}
        public int read(byte[] b,int off,int len)throws IOException {
            if(len==0)return 0;if(count>=limit)throw new IOException("Inventory declared length exhausted");
            int n=input.read(b,off,(int)Math.min(len,limit-count));if(n==0){int v=read();if(v<0)return -1;b[off]=(byte)v;return 1;}
            if(n>0){count+=n;full.update(b,off,n);if(!checksum)body.update(b,off,n);}return n;
        }
    }
}
