package android.system;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
/** Host boundary double: does real host file/directory sync and atomic move, not Android proof. */
public final class Os {
 public static String failure="";
 public static final List<String> calls=new ArrayList<>();
 public static int flags;
 private static final Map<FileDescriptor,Path> paths=new IdentityHashMap<>();
 private static void event(String name)throws ErrnoException {calls.add(name);if(Arrays.asList(failure.split(",")).contains(name))throw new ErrnoException(name,5);}
 public static FileDescriptor open(String name,int options,int mode)throws ErrnoException {
  event("open");flags=options;Path p=Paths.get(name);
  if(Files.isSymbolicLink(p)||(options&OsConstants.O_NOFOLLOW)==0)throw new ErrnoException("nofollow",40);
  FileDescriptor fd=new FileDescriptor();paths.put(fd,p);return fd;
 }
 public static StructStat fstat(FileDescriptor fd)throws ErrnoException {event("fstat");return new StructStat(Files.isDirectory(paths.get(fd))?0040000:0100000);}
 public static void fsync(FileDescriptor fd)throws ErrnoException {
  event("fsync");try{Path p=paths.get(fd);if(p==null)fd.sync();else try(FileChannel c=FileChannel.open(p,StandardOpenOption.READ)){c.force(true);}}
  catch(IOException e){throw new ErrnoException("fsync",5);}
 }
 public static void close(FileDescriptor fd)throws ErrnoException {paths.remove(fd);event("close");}
 public static void rename(String from,String to)throws ErrnoException {event("rename");try{Files.move(Paths.get(from),Paths.get(to),StandardCopyOption.ATOMIC_MOVE);}catch(IOException e){throw new ErrnoException("rename",5);}}
}
