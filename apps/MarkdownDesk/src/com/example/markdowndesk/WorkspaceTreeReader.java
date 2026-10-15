package com.example.markdowndesk;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.provider.DocumentsContract;
import java.io.FileNotFoundException;
import java.util.*;

/** One synchronous, read-only SAF child query. Run on an I/O worker.
 * Caps app-retained metadata, not provider/CursorWindow allocations. There is no
 * pagination/continuation promise, recursive walk, grant mutation or write API. */
final class WorkspaceTreeReader {
    enum State { COMPLETE, LIMITED, CANCELLED, FAILED }
    enum Error { NONE, ACCESS_REVOKED, UNAVAILABLE, PROVIDER_FAILURE, MALFORMED_PROVIDER }
    static final class Limits {
        final int rows,results,textUnits,fieldUnits;
        Limits(int rows,int results,int textUnits,int fieldUnits) {
            if(rows<1||rows>256||results<1||results>100||textUnits<1||textUnits>65536||fieldUnits<1||fieldUnits>4096)
                throw new IllegalArgumentException("Invalid workspace query limits");
            this.rows=rows;this.results=results;this.textUnits=textUnits;this.fieldUnits=fieldUnits;
        }
        static Limits defaults(){return new Limits(256,100,65536,4096);}
    }
    static final class Directory {
        private final WorkspaceTreeReader owner;private final String id;
        private Directory(WorkspaceTreeReader owner,String id){this.owner=owner;this.id=id;}
    }
    static final class Entry {
        final Uri uri;final String documentId,displayName,mimeType;
        final Long size,modified;final long flags;final Directory directory;
        private Entry(Uri uri,String id,String name,String mime,Long size,Long modified,long flags,Directory directory){
            this.uri=uri;documentId=id;displayName=name;mimeType=mime;this.size=size;this.modified=modified;this.flags=flags;this.directory=directory;
        }
    }
    static final class Page {
        final List<Entry> entries;final int scannedRows,retainedTextUnits;
        final State state;final Error error;
        private Page(List<Entry> entries,int scanned,int units,State state,Error error){
            // This list is private to one completed call; no mutable alias escapes.
            this.entries=Collections.unmodifiableList(entries);scannedRows=scanned;retainedTextUnits=units;this.state=state;this.error=error;
        }
    }
    private static final String[] PROJECTION={DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS};
    private final ContentResolver resolver;private final Uri tree;private final Directory root;
    WorkspaceTreeReader(ContentResolver resolver,Uri selectedTree){
        if(resolver==null||selectedTree==null||!"content".equals(selectedTree.getScheme())
                ||selectedTree.getAuthority()==null||selectedTree.getAuthority().isEmpty()
                ||selectedTree.toString().length()>4096||!DocumentsContract.isTreeUri(selectedTree))
            throw new IllegalArgumentException("Expected selected SAF tree");
        String id=DocumentsContract.getTreeDocumentId(selectedTree);
        if(id==null||id.isEmpty()||id.length()>4096)throw new IllegalArgumentException("Invalid SAF tree identifier");
        this.resolver=resolver;tree=selectedTree;root=new Directory(this,id);
    }
    Directory root(){return root;}
    Page list(Directory directory,Limits limits,CancellationSignal cancellation){
        if(directory==null||directory.owner!=this||limits==null||cancellation==null)
            throw new IllegalArgumentException("Foreign directory or missing query inputs");
        List<Entry> entries=new ArrayList<>();Set<String> ids=new HashSet<>();
        int scanned=0,units=0;State state=State.COMPLETE;Error error=Error.NONE;Cursor cursor=null;
        try {
            cancellation.throwIfCanceled();
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,directory.id);
            cursor=resolver.query(children,PROJECTION.clone(),null,null,null,cancellation);
            cancellation.throwIfCanceled();
            if(cursor==null)throw new ProviderFailure();
            int[] columns=new int[PROJECTION.length];
            for(int i=0;i<columns.length;i++){columns[i]=cursor.getColumnIndex(PROJECTION[i]);if(columns[i]<0)throw new Malformed();}
            while(true){
                cancellation.throwIfCanceled();
                // Conservative partial result: do not read an uncharged lookahead row.
                if(scanned>=limits.rows||entries.size()>=limits.results||units>=limits.textUnits){state=State.LIMITED;break;}
                boolean found=cursor.moveToNext();if(found)scanned++;
                cancellation.throwIfCanceled();if(!found)break;
                String id=field(cursor,columns[0],limits.fieldUnits,true);
                String name=field(cursor,columns[1],limits.fieldUnits,false);
                String mime=field(cursor,columns[2],limits.fieldUnits,true);
                if(ids.contains(id))throw new Malformed();
                Uri document=DocumentsContract.buildDocumentUriUsingTree(tree,id);
                int uriUnits=document.toString().length();if(uriUnits>limits.fieldUnits)throw new Malformed();
                long needed=(long)id.length()+name.length()+mime.length()+uriUnits;
                if(needed>limits.textUnits-units){state=State.LIMITED;break;}
                Long size=number(cursor,columns[3]),modified=number(cursor,columns[4]);
                long flags=cursor.isNull(columns[5])?0:cursor.getLong(columns[5]);
                cancellation.throwIfCanceled();
                // All retained fields, the ID set and optional handle are admitted together.
                ids.add(id);units+=(int)needed;
                Directory child=DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)?new Directory(this,id):null;
                entries.add(new Entry(document,id,name,mime,size,modified,flags,child));
            }
        }catch(OperationCanceledException cancelled){state=State.CANCELLED;}
        catch(SecurityException revoked){state=State.FAILED;error=Error.ACCESS_REVOKED;}
        catch(Malformed malformed){state=State.FAILED;error=Error.MALFORMED_PROVIDER;}
        catch(RuntimeException failure){state=State.FAILED;error=failure.getCause() instanceof FileNotFoundException?Error.UNAVAILABLE:Error.PROVIDER_FAILURE;}
        finally {
            if(cursor!=null)try{cursor.close();}catch(RuntimeException failure){state=State.FAILED;error=Error.PROVIDER_FAILURE;}
        }
        return new Page(entries,scanned,units,state,error);
    }
    private static String field(Cursor cursor,int column,int maximum,boolean nonempty){
        if(cursor.isNull(column))throw new Malformed();String value=cursor.getString(column);
        if(value==null||value.length()>maximum||nonempty&&value.isEmpty())throw new Malformed();return value;
    }
    private static Long number(Cursor cursor,int column){if(cursor.isNull(column))return null;long n=cursor.getLong(column);return n<0?null:n;}
    private static final class Malformed extends RuntimeException {}
    private static final class ProviderFailure extends RuntimeException {}
}
