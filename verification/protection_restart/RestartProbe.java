package com.example.markdowndesk;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Real production records and coordinator, killed externally at host durability boundaries. */
public final class RestartProbe {
    static final byte[] ORIGINAL="\ufefforiginal\r\n雪".getBytes(StandardCharsets.UTF_8);
    static final byte[] EDITED="\ufeffedited\r\n🧭".getBytes(StandardCharsets.UTF_8);
    static final BufferedReader CONTROL=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
    static void boundary(String name)throws IOException {
        System.out.println(name); System.out.flush();
        if(!"continue".equals(CONTROL.readLine()))throw new IOException("Missing test continuation");
    }
    static final class Disk implements ProtectionStore.Durability {
        boolean armed; int calls;
        void after()throws IOException {if(armed)boundary("durable-"+(++calls));}
        public void syncFile(FileDescriptor fd)throws IOException {fd.sync();after();}
        public void move(File a,File b)throws IOException {Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE);after();}
        public void syncDirectory(File dir)throws IOException {
            try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);} after();
        }
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[1]); Path source=root.resolve("provider.md"); Disk disk=new Disk();
        ProtectionStore store=new ProtectionStore(root.resolve("protection").toFile(),disk,64L*1024*1024);
        if("save".equals(args[0])) {
            Files.write(source,ORIGINAL);disk.armed=true;
            SaveCoordinator.Provider provider=new SaveCoordinator.Provider(){
                int reads;
                public byte[] read()throws IOException {
                    byte[] bytes=Files.readAllBytes(source);
                    if(++reads==3)boundary("readback");
                    return bytes;
                }
                public void write(byte[] bytes)throws IOException {
                    try(FileOutputStream out=new FileOutputStream(source.toFile())) {
                        boundary("provider-open");
                        out.write(bytes,0,2);out.flush();out.getFD().sync();boundary("provider-partial");
                        out.write(bytes,2,bytes.length-2);out.flush();out.getFD().sync();boundary("provider-written");
                    }
                }
            };
            new SaveCoordinator(store).save(new SaveCoordinator.Request("synthetic-document","content://fixture/document",
                    "Synthetic.md",7,EDITED,EditorTools.sha256(ORIGINAL),null,false),provider);
            throw new AssertionError("Parent should kill before save returns");
        }
        String point=args[2];
        int durable=point.startsWith("durable-")?Integer.parseInt(point.substring(8)):0;
        boolean mutated=point.startsWith("provider-")||point.equals("readback")||durable>=10;
        byte[] expected=point.equals("provider-open")?new byte[0]:point.equals("provider-partial")?
                Arrays.copyOf(EDITED,2):mutated?EDITED:ORIGINAL;
        require(Arrays.equals(expected,Files.readAllBytes(source)),"actual canonical bytes at "+point);
        List<ProtectionStore.Snapshot> snapshots=store.snapshots();
        int expectedSnapshots=durable==1?0:durable>=2&&durable<=4?1:2;
        require(snapshots.size()==expectedSnapshots,"committed snapshot count at "+point);
        for(ProtectionStore.Snapshot snapshot:snapshots) {
            byte[] bytes=snapshot.bytes();
            require(Arrays.equals(snapshot.role.equals("original")?ORIGINAL:EDITED,bytes),"exact protected bytes");
            require(snapshot.hash.equals(EditorTools.sha256(bytes)),"integrity after new JVM restart");
        }
        List<ProtectionStore.Transaction> transactions=store.transactions();
        boolean prepared=mutated||durable>=8;
        require(transactions.size()==(prepared?1:0),"prepared transaction count");
        if(prepared) {
            ProtectionStore.Transaction transaction=transactions.get(0);
            require(Arrays.equals(ORIGINAL,transaction.original.bytes()),"original survives interrupted save");
            require(Arrays.equals(EDITED,transaction.attempted.bytes()),"edit survives interrupted save");
            require(transaction.verified==(durable>=11),"completion only after exact readback and record commit");
        }
        // Re-enumerating must not delete interrupted staging or immutable records.
        long before;
        try(java.util.stream.Stream<Path> entries=Files.list(root.resolve("protection"))){before=entries.count();}
        store.snapshots();store.transactions();
        try(java.util.stream.Stream<Path> entries=Files.list(root.resolve("protection"))){require(before==entries.count(),"restart does not prune records");}
        System.out.println("PASS restart "+point);
    }
}
