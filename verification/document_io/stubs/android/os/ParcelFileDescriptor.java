package android.os;
import java.io.*;
/** Real host descriptor, no Android binder or provider lifecycle simulation. */
public final class ParcelFileDescriptor implements AutoCloseable {
    private final RandomAccessFile file;
    public boolean closed;
    public ParcelFileDescriptor(File path, boolean truncate) throws IOException {
        file = new RandomAccessFile(path, "rw");
        if (truncate) file.setLength(0);
    }
    public FileDescriptor getFileDescriptor() throws IOException { return file.getFD(); }
    @Override public void close() throws IOException { closed = true; file.close(); }
}
