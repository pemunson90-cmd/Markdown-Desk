package com.example.markdowndesk;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;

/** Android fsync/rename boundary. Unsupported directory fsync propagates as failure. */
public final class AndroidDurability implements ProtectionStore.Durability {
    @Override public void syncFile(FileDescriptor descriptor) throws IOException {
        try {Os.fsync(descriptor);} catch(ErrnoException e){throw new IOException("Protection file sync failed",e);}
    }
    @Override public void move(File stage,File destination) throws IOException {
        if(!stage.getAbsoluteFile().getParentFile().equals(destination.getAbsoluteFile().getParentFile())
                || Files.isSymbolicLink(stage.toPath())
                || Files.exists(destination.toPath(),LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Invalid atomic protection move");
        try {Os.rename(stage.getAbsolutePath(),destination.getAbsolutePath());}
        catch(ErrnoException e){throw new IOException("Atomic protection move failed",e);}
    }
    @Override public void syncDirectory(File directory) throws IOException {
        FileDescriptor descriptor;
        // Android's public OsConstants omits O_DIRECTORY; fstat the opened descriptor instead.
        try {descriptor=Os.open(directory.getAbsolutePath(),OsConstants.O_RDONLY|OsConstants.O_NOFOLLOW|OsConstants.O_CLOEXEC,0);}
        catch(ErrnoException e){throw new IOException("Protection directory open failed",e);}
        IOException failure=null;
        try {
            if(!OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)) failure=new IOException("Protection sync target is not a directory");
            else Os.fsync(descriptor);
        }catch(ErrnoException e){failure=new IOException("Protection directory sync failed",e);}
        try {Os.close(descriptor);}catch(ErrnoException e){
            IOException close=new IOException("Protection directory close failed",e);
            if(failure==null)failure=close;else failure.addSuppressed(close);
        }
        if(failure!=null)throw failure;
    }
}
