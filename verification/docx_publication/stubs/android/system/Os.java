package android.system;import java.io.*;import java.nio.channels.*;import java.nio.file.*;import java.util.*;
public final class Os {private static final Map<FileDescriptor,FileChannel> open=new IdentityHashMap<>();public static boolean failSync,failClose;
public static FileDescriptor open(String p,int flags,int mode)throws ErrnoException {try{FileDescriptor fd=new FileDescriptor();open.put(fd,FileChannel.open(Path.of(p),StandardOpenOption.READ));return fd;}catch(IOException e){throw new ErrnoException();}}
public static StructStat fstat(FileDescriptor f){return new StructStat();}public static void fsync(FileDescriptor f)throws ErrnoException {if(failSync)throw new ErrnoException();try{open.get(f).force(true);}catch(IOException e){throw new ErrnoException();}}
public static void close(FileDescriptor f)throws ErrnoException {if(failClose)throw new ErrnoException();try{open.remove(f).close();}catch(IOException e){throw new ErrnoException();}}
}
