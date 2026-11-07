package com.example.markdowndesk;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import com.example.markdowndesk.archive.ArchiveJournal;
import com.example.markdowndesk.archive.ArchiveMarkdownIndexes;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** SAF sidecar writer. New generation fully written before any original is renamed.
 * Interrupted writes retain old index, staging file, or named backup; never
 * truncate a known-good index in-place. Called exclusively on an I/O executor.
 */
final class ArchiveSidecarPublisher {
    private final ContentResolver resolver;
    private final Uri root;
    ArchiveSidecarPublisher(Context context,Uri root){this.resolver=context.getContentResolver();this.root=root;}

    void publish(String chatTitle,String archiveId,String chatId,
                 Collection<ArchiveJournal.Annotation> annotations)throws IOException{
        if(root==null||!"content".equalsIgnoreCase(root.getScheme()))
            throw new IOException("Select a notes directory first");
        String perChat=ArchiveMarkdownIndexes.perChat(chatTitle,archiveId,chatId,annotations);
        String master=ArchiveMarkdownIndexes.master(annotations);
        Uri rootDocument=DocumentsContract.buildDocumentUriUsingTree(root,DocumentsContract.getTreeDocumentId(root));
        Uri chats=child(rootDocument,"Chat Indexes");
        if(chats==null){
            chats=DocumentsContract.createDocument(resolver,rootDocument,
                    DocumentsContract.Document.MIME_TYPE_DIR,"Chat Indexes");
            if(chats==null)throw new IOException("The selected provider cannot create the Chat Indexes folder");
        }
        publishChat(chats,chatTitle,archiveId,chatId,perChat);
        publishDocument(rootDocument,"MASTER-LINKS.md",master,null,null);
    }

    void publishAll(Collection<ArchiveJournal.Annotation> annotations)throws IOException{
        if(root==null)throw new IOException("Select an annotation directory");
        Uri rootDocument=DocumentsContract.buildDocumentUriUsingTree(root,DocumentsContract.getTreeDocumentId(root));
        Uri chats=child(rootDocument,"Chat Indexes");
        if(chats==null)chats=DocumentsContract.createDocument(resolver,rootDocument,
                DocumentsContract.Document.MIME_TYPE_DIR,"Chat Indexes");
        if(chats==null)throw new IOException("Cannot create Chat Indexes folder");
        Map<String,ArchiveJournal.Annotation> unique=new LinkedHashMap<>();
        for(ArchiveJournal.Annotation a:annotations) {
            if(a==null || a.archiveId==null || a.chatId==null)
                throw new IOException("Missing annotation sidecar source identity");
            unique.put(a.archiveId.length()+":"+a.archiveId+a.chatId,a);
        }
        for(ArchiveJournal.Annotation a:unique.values())
            publishChat(chats,a.chatTitle,a.archiveId,a.chatId,
                    ArchiveMarkdownIndexes.perChat(a.chatTitle,a.archiveId,a.chatId,annotations));
        publishDocument(rootDocument,"MASTER-LINKS.md",ArchiveMarkdownIndexes.master(annotations),null,null);
    }

    private void publishChat(Uri folder,String title,String archiveId,String chatId,
                             String generated)throws IOException {
        String safe=ArchiveMarkdownIndexes.fileName(title,chatId);
        String legacy=ArchiveMarkdownIndexes.legacyFileName(title,chatId);
        // Legacy short-ID normalization could collide (for example "a/b" vs "a_b").
        // Copy only an identity-proven old notebook, retaining manual prose and the
        // entire original file. Never rename/delete a legacy notebook automatically.
        if(!legacy.equals(safe)) {
            Uri old=child(folder,legacy);
            if(old!=null) {
                Uri modern=child(folder,safe);
                if(modern==null) {
                    String previous=read(old);
                    if(!ArchiveMarkdownIndexes.matchesOwner(previous,archiveId,chatId))
                        throw new IOException("Legacy notebook identity is ambiguous; original preserved");
                    String migrated=ArchiveMarkdownIndexes.replaceGeneratedRegion(previous,generated);
                    publishDocument(folder,safe,migrated,archiveId,chatId);
                    return;
                }
                // A verified modern notebook owns future updates; legacy stays untouched.
            }
        }
        publishDocument(folder,safe,generated,archiveId,chatId);
    }

    private void publishDocument(Uri folder,String name,String generated,
                                 String archiveId,String chatId)throws IOException{
        Uri existing=child(folder,name);
        String output=generated;
        if(existing!=null) {
            String previous=read(existing);
            if(chatId!=null && !ArchiveMarkdownIndexes.matchesOwner(previous,archiveId,chatId))
                throw new IOException("Existing Markdown notebook identity is ambiguous; original preserved");
            output=ArchiveMarkdownIndexes.replaceGeneratedRegion(previous,generated);
        }
        String stageName=".md-stage-"+UUID.randomUUID().toString()+".md";
        Uri stage=DocumentsContract.createDocument(resolver,folder,"text/markdown",stageName);
        if(stage==null)throw new IOException("Cannot stage the Markdown index in this directory");
        try(ParcelFileDescriptor descriptor=resolver.openFileDescriptor(stage,"rwt")){
            if(descriptor==null)throw new IOException("Could not open staged index for writing");
            FileOutputStream out=new FileOutputStream(descriptor.getFileDescriptor());
            byte[] bytes=output.getBytes(StandardCharsets.UTF_8);
            out.write(bytes);out.flush();out.getFD().sync();
        }
        // Confirm provider persisted the entire staged file before renaming anything.
        if(!output.equals(read(stage)))throw new IOException("Staged index verification failed; original preserved");
        Uri backup=null;
        if(existing!=null){
            backup=DocumentsContract.renameDocument(resolver,existing,
                    name+".backup-"+System.currentTimeMillis());
            if(backup==null)throw new IOException("Provider cannot safely rename the existing index; staged copy preserved");
        }
        Uri published=DocumentsContract.renameDocument(resolver,stage,name);
        if(published==null)throw new IOException("Provider could not publish staged index. Prior version kept as backup.");
        if(!output.equals(read(published)))
            throw new IOException("Published index failed verification. Previous version was preserved as backup.");
    }

    private Uri child(Uri dir,String name)throws IOException{
        String treeId=DocumentsContract.getTreeDocumentId(root);
        String folderId=DocumentsContract.getDocumentId(dir);
        Uri listing=DocumentsContract.buildChildDocumentsUriUsingTree(root,folderId==null?treeId:folderId);
        try(Cursor c=resolver.query(listing,new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(c==null)throw new IOException("Storage provider cannot list annotation directory");
            while(c.moveToNext()){
                String display=c.getString(1);
                if(name.equals(display))return DocumentsContract.buildDocumentUriUsingTree(root,c.getString(0));
            }
        }catch(SecurityException revoked){throw new IOException("Notes folder permission has been revoked",revoked);}
        return null;
    }
    private String read(Uri document)throws IOException{
        try(InputStream in=resolver.openInputStream(document)){
            if(in==null)throw new IOException("Provider returned no index content");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buffer=new byte[16384];int count;
            while((count=in.read(buffer))>=0){
                if(count==0)continue;
                if(out.size()+count>24*1024*1024)throw new IOException("Existing Markdown index is too large to update safely");
                out.write(buffer,0,count);
            }
            try {
                return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(out.toByteArray())).toString();
            } catch(CharacterCodingException malformed){
                throw new IOException("Existing Markdown index contains malformed UTF-8; original preserved",malformed);
            }
        }
    }
}