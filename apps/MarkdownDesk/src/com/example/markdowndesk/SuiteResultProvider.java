package com.example.markdowndesk;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import org.mothership.suite.*;
import java.io.*;
import java.util.List;

/** Only a specific read-granted result can leave the editor. No directory listing or writes. */
public final class SuiteResultProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private File result(Uri uri)throws FileNotFoundException {
        Context c=getContext();
        if(c==null||(!SuiteTrust.uidIsMothership(c,Binder.getCallingUid())&&Binder.getCallingUid()!=android.os.Process.myUid()))throw new SecurityException("Untrusted result reader");
        List<String> p=uri.getPathSegments();
        if(!"content".equals(uri.getScheme())||!(EditorProtocol.DESK+".suite").equals(uri.getAuthority())||uri.getQuery()!=null||uri.getFragment()!=null||p.size()!=3||!"session".equals(p.get(0))||!"result.md".equals(p.get(2)))throw new FileNotFoundException("Unknown result");
        try{EditorProtocol.id(p.get(1));}catch(IllegalArgumentException e){throw new FileNotFoundException("Invalid session");}
        File f=new File(new File(c.getFilesDir(),"suite-sessions/"+p.get(1)),"result.md");
        if(!f.isFile()||f.length()>EditorProtocol.MAX_BYTES)throw new FileNotFoundException("Result unavailable");return f;
    }
    public String getType(Uri uri){return "text/markdown";}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"r".equals(mode))throw new SecurityException("Results are read-only");return ParcelFileDescriptor.open(result(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        try{File f=result(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] values=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]="working-copy.md";else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=f.length();}cursor.addRow(values);return cursor;}catch(FileNotFoundException e){return null;}
    }
    public Uri insert(Uri uri,ContentValues v){throw new UnsupportedOperationException();}
    public int update(Uri uri,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    public int delete(Uri uri,String s,String[] a){throw new UnsupportedOperationException();}
}
