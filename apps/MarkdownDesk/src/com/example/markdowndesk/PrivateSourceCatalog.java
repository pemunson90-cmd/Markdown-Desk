package com.example.markdowndesk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Pure97-byte source-coordinate rows. Imported/local catalog data alone never grants routing. */
final class PrivateSourceCatalog implements AutoCloseable {
    static final int NATIVE=1,OWN_FRAME=2,OWN_EVIDENCE=3,RETAINED=4;
    private static final byte[] MAGIC="MDSRC01\n".getBytes(StandardCharsets.US_ASCII);
    static final class Row {
        final UUID outer,owner,member;final int route;final long offset,length;final String hash;
        Row(UUID outer,int route,UUID owner,UUID member,long offset,long length,String hash)throws IOException {
            PrivateBundleInventory.requireGenerated(outer);
            if(route<1||route>4||owner==null||member==null||offset<0||length<0||offset>PrivateArchive.MAX_ARCHIVE_BYTES-length
                    ||route!=NATIVE&&(!generated(owner)||!generated(member))||(route==OWN_FRAME||route==OWN_EVIDENCE)&&offset!=0
                    ||hash==null||!hash.matches("[0-9a-f]{64}"))throw new IOException("Invalid source catalog row");
            this.outer=outer;this.route=route;this.owner=owner;this.member=member;this.offset=offset;this.length=length;this.hash=hash;
        }
    }
    final List<Row> rows;final long length;
    private final PrivateBundleInventory.Reservation retained;private boolean closed;
    private PrivateSourceCatalog(List<Row> rows,PrivateBundleInventory.Reservation held){this.rows=Collections.unmodifiableList(rows);length=52L+97L*rows.size();retained=held;}
    static PrivateSourceCatalog create(List<Row> rows,PrivateBundleInventory.MetadataBudget budget)throws IOException {
        if(rows==null||budget==null||rows.size()>16380)throw new IOException("Source catalog count/budget");
        PrivateBundleInventory.Reservation held=budget.reserve(4096L+512L*rows.size());
        try{List<Row> copy=new ArrayList<>(rows);UUID previous=null;for(Row row:copy){if(row==null||previous!=null&&PrivateBundleInventory.compare(previous,row.outer)>=0)throw new IOException("Source catalog order/duplicate");previous=row.outer;}return new PrivateSourceCatalog(copy,held);}
        catch(IOException|RuntimeException|Error failure){held.close();throw failure;}
    }
    static PrivateSourceCatalog read(InputStream input,PrivateBundleInventory.MetadataBudget budget)throws IOException {
        if(input==null||budget==null)throw new IOException("Missing source catalog input/budget");
        try(PrivateBundleInventory.Reservation work=budget.reserve(4096)) {
            InputStream progress=new FilterInputStream(input){public int read(byte[] b,int o,int n)throws IOException{if(n==0)return 0;int got=super.read(b,o,n);if(got!=0)return got;int one=super.read();if(one<0)return -1;b[o]=(byte)one;return 1;}};
            MessageDigest hash=HistoryStorage.digest();DataInputStream in=new DataInputStream(new java.security.DigestInputStream(progress,hash));
            byte[] magic=new byte[8];in.readFully(magic);if(!Arrays.equals(magic,MAGIC)||in.readInt()!=1)throw new IOException("Source catalog format");
            int length=in.readInt(),count=in.readInt();if(count<0||count>16380||length!=52L+97L*count)throw new IOException("Source catalog bounds");
            PrivateBundleInventory.Reservation held=budget.reserve(4096L+512L*count);
            try{List<Row> rows=new ArrayList<>(count);UUID previous=null;
                for(int i=0;i<count;i++){Row row=new Row(uuid(in),in.readUnsignedByte(),uuid(in),uuid(in),in.readLong(),in.readLong(),hash(in));
                    if(previous!=null&&PrivateBundleInventory.compare(previous,row.outer)>=0)throw new IOException("Source catalog order/duplicate");previous=row.outer;rows.add(row);}
                byte[] expected=hash.digest(),actual=new byte[32];new DataInputStream(progress).readFully(actual);
                if(!MessageDigest.isEqual(expected,actual)||progress.read()!=-1)throw new IOException("Source catalog checksum/EOF");
                return new PrivateSourceCatalog(rows,held);
            }catch(IOException|RuntimeException|Error failure){held.close();throw failure;}
        }
    }
    void write(OutputStream output)throws IOException {
        if(closed||output==null)throw new IOException("Closed/missing source catalog");
        MessageDigest hash=HistoryStorage.digest();DataOutputStream out=new DataOutputStream(new java.security.DigestOutputStream(output,hash));
        out.write(MAGIC);out.writeInt(1);out.writeInt((int)length);out.writeInt(rows.size());
        for(Row r:rows){uuid(out,r.outer);out.writeByte(r.route);uuid(out,r.owner);uuid(out,r.member);out.writeLong(r.offset);out.writeLong(r.length);PrivateBundleInventory.putHash(out,r.hash);}
        output.write(hash.digest());
    }
    public void close(){if(!closed){closed=true;retained.close();}}
    private static boolean generated(UUID id){return id.version()==4&&id.variant()==2;}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static String hash(DataInputStream in)throws IOException{byte[] b=new byte[32];in.readFully(b);return HistoryStorage.hex(b);}
}
