package com.example.markdowndesk;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.*;
import java.security.MessageDigest;
import java.util.*;

/** One guarded new-copy attempt, not an overwrite primitive or malicious-provider proof. */
final class DocxNewCopy {
    enum Reason { ALIASED, DESTINATION, NONEMPTY, STAGING, WRITE, VERIFY, CLOSE, CANCELLED }
    static final class Failure extends IOException {
        final Reason reason;final boolean partial,uncertain;
        Failure(Reason reason,boolean partial,boolean uncertain){super("DOCX publication: "+reason);this.reason=reason;this.partial=partial;this.uncertain=uncertain;}
    }
    static final class VerifiedCopy {final long length;final String sha256;VerifiedCopy(DocxStage stage){length=stage.length();sha256=stage.hash();}}
    private DocxNewCopy(){}
    static VerifiedCopy publish(ContentResolver resolver,Context context,DocxStage stage,Uri destination,
            List<Uri> protectedUris,MarkdownDocx.Cancellation cancel)throws IOException {
        valid(destination);if(protectedUris==null||protectedUris.size()>25)throw new Failure(Reason.DESTINATION,false,false);
        for(Uri uri:protectedUris)if(uri!=null){valid(uri);
            if(uri.toString().equals(destination.toString())||sameDocument(context,uri,destination))throw new Failure(Reason.ALIASED,false,false);
        }
        cancel.check();metadata(resolver,destination);empty(resolver,destination);cancel.check();
        try(DocxStage.ReadHandle input=stage.openVerified(cancel)) {
            // No alternate mode or second open even if null/throw/partial write.
            stage.identity();cancel.check();
            ByteBuffer bytes=ByteBuffer.allocate(65536);MessageDigest hash=DocxStage.digest();
            OutputStream output;
            try{output=resolver.openOutputStream(destination,"w");}catch(IOException|RuntimeException bad){throw failure(Reason.WRITE,true,bad);}
            if(output==null)throw new Failure(Reason.WRITE,true,false);
            try(Owned<OutputStream> owned=new Owned<>(output,true)) {
                long total=0;
                while(true){cancel.check();bytes.clear();int n=input.channel.read(bytes);if(n<0)break;if(n==0)throw new Failure(Reason.STAGING,true,false);total+=n;if(total>stage.length())throw new Failure(Reason.STAGING,true,false);output.write(bytes.array(),0,n);hash.update(bytes.array(),0,n);}
                if(total!=stage.length()||!stage.hash().equals(DocxStage.hex(hash.digest())))throw new Failure(Reason.STAGING,true,false);
                stage.identity();output.flush();
            }catch(IOException bad){throw phase(Reason.WRITE,true,bad);}catch(RuntimeException bad){throw failure(Reason.WRITE,true,bad);}
        }
        cancel.check();verify(resolver,destination,stage,cancel);return new VerifiedCopy(stage);
    }
    private static void valid(Uri uri)throws Failure {
        if(uri==null||uri.toString().length()>4096||!"content".equals(uri.getScheme())||uri.getAuthority()==null||uri.getAuthority().isEmpty())throw new Failure(Reason.DESTINATION,false,false);
    }
    private static boolean sameDocument(Context context,Uri a,Uri b)throws Failure {
        if(!a.getAuthority().equals(b.getAuthority()))return false;
        try{return DocumentsContract.isDocumentUri(context,a)&&DocumentsContract.isDocumentUri(context,b)
                &&DocumentsContract.getDocumentId(a).equals(DocumentsContract.getDocumentId(b));}
        catch(RuntimeException malformed){throw new Failure(Reason.DESTINATION,false,false);}
    }
    private static IOException phase(Reason reason,boolean partial,IOException cause){
        if(cause instanceof Failure||cause instanceof MarkdownDocx.ExportException)return cause;
        return failure(reason,partial,cause);
    }
    private static Failure failure(Reason reason,boolean partial,Throwable cause){Failure failed=new Failure(reason,partial,false);failed.initCause(cause);return failed;}
    private static final class Owned<T extends AutoCloseable> implements AutoCloseable {
        final T value;final boolean partial;
        Owned(T value,boolean partial){this.value=value;this.partial=partial;}
        public void close()throws Failure {
            try{value.close();}catch(Exception|Error bad){Failure failure=new Failure(Reason.CLOSE,partial,true);failure.initCause(bad);throw failure;}
        }
    }
    private static void metadata(ContentResolver resolver,Uri destination)throws IOException {
        Cursor cursor;
        try{cursor=resolver.query(destination,new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE},null,null,null);}
        catch(RuntimeException bad){throw failure(Reason.DESTINATION,false,bad);}
        if(cursor==null)throw new Failure(Reason.DESTINATION,false,false);
        try(Owned<Cursor> owned=new Owned<>(cursor,false)) {
            if(!cursor.moveToNext())throw new Failure(Reason.DESTINATION,false,false);
            int mime=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE),size=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);
            if(mime<0||size<0||cursor.isNull(mime))throw new Failure(Reason.DESTINATION,false,false);
            String type=cursor.getString(mime);if(type==null||type.length()>4096||DocumentsContract.Document.MIME_TYPE_DIR.equals(type))throw new Failure(Reason.DESTINATION,false,false);
            if(!cursor.isNull(size)&&cursor.getLong(size)!=0)throw new Failure(Reason.NONEMPTY,false,false);
            if(cursor.moveToNext())throw new Failure(Reason.DESTINATION,false,false);
        }catch(RuntimeException bad){throw failure(Reason.DESTINATION,false,bad);}
    }
    private static void empty(ContentResolver resolver,Uri destination)throws IOException {
        InputStream input;
        try{input=resolver.openInputStream(destination);}catch(IOException|RuntimeException bad){throw failure(Reason.DESTINATION,false,bad);}
        if(input==null)throw new Failure(Reason.DESTINATION,false,false);
        try(Owned<InputStream> owned=new Owned<>(input,false)) {if(input.read()!=-1)throw new Failure(Reason.NONEMPTY,false,false);}
        catch(IOException bad){throw phase(Reason.DESTINATION,false,bad);}catch(RuntimeException bad){throw failure(Reason.DESTINATION,false,bad);}
    }
    private static void verify(ContentResolver resolver,Uri destination,DocxStage stage,MarkdownDocx.Cancellation cancel)throws IOException {
        byte[] bytes=new byte[65536];MessageDigest hash=DocxStage.digest();InputStream input;
        try{input=resolver.openInputStream(destination);}catch(IOException|RuntimeException bad){throw failure(Reason.VERIFY,true,bad);}
        if(input==null)throw new Failure(Reason.VERIFY,true,false);
        try(Owned<InputStream> owned=new Owned<>(input,true)) {
            long total=0;
            while(true){cancel.check();int n=input.read(bytes,0,(int)Math.min(bytes.length,stage.length()+1-total));if(n<0)break;if(n==0)throw new Failure(Reason.VERIFY,true,false);total+=n;if(total>stage.length())throw new Failure(Reason.VERIFY,true,false);hash.update(bytes,0,n);}
            if(total!=stage.length()||!stage.hash().equals(DocxStage.hex(hash.digest())))throw new Failure(Reason.VERIFY,true,false);
        }catch(IOException bad){throw phase(Reason.VERIFY,true,bad);}catch(RuntimeException bad){throw failure(Reason.VERIFY,true,bad);}
    }
}
