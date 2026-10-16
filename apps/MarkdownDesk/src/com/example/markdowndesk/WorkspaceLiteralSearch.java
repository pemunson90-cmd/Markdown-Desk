package com.example.markdowndesk;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import java.io.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Synchronous selected-tree literal search; no UI, writes, threads or grant mutations.
 * Bounds app-retained metadata/work, not provider/CursorWindow or whole Java heap. */
final class WorkspaceLiteralSearch {
    enum State {COMPLETE,LIMITED,CANCELLED,FAILED}
    enum Reason {DIRECTORIES,FILES,ROWS,DEPTH,BYTES,MATCHES,METADATA,DIRECTORY_PARTIAL,UNREADABLE,ACCESS_REVOKED,CLOSE_UNCERTAIN,CYCLE}
    enum CloseDisposition {CLOSED,UNCERTAIN}
    static final class Limits {
        final int directories,files,rows,depth,matches,queryUnits,snippetUnits,metadataUnits;
        final long bytes,fileBytes;
        Limits(int directories,int files,int rows,int depth,long bytes,long fileBytes,int matches,int queryUnits,int snippetUnits,int metadataUnits){
            if(directories<1||directories>128||files<1||files>512||rows<1||rows>4096||depth<1||depth>16
                    ||bytes<1||bytes>(16L<<20)||fileBytes<1||fileBytes>(8L<<20)||matches<1||matches>100
                    ||queryUnits<1||queryUnits>128||snippetUnits<1||snippetUnits>160||metadataUnits<1||metadataUnits>262144)
                throw new IllegalArgumentException("Invalid workspace search limits");
            this.directories=directories;this.files=files;this.rows=rows;this.depth=depth;this.bytes=bytes;this.fileBytes=fileBytes;
            this.matches=matches;this.queryUnits=queryUnits;this.snippetUnits=snippetUnits;this.metadataUnits=metadataUnits;
        }
        static Limits defaults(){return new Limits(128,512,4096,16,16L<<20,8L<<20,100,128,160,262144);}
    }
    static final class Match {
        final int offsetUtf16,lengthUtf16,line;final String snippet;
        private Match(Pending p){offsetUtf16=p.offset;lengthUtf16=p.queryLength;line=p.line;int n=p.used;if(n>0&&Character.isHighSurrogate(p.text[n-1]))n--;snippet=new String(p.text,0,n);}
    }
    static final class FileHit {
        final Uri uri;final String documentId,displayName,hash;final long byteLength;final List<Match> matches;
        private FileHit(Work file,String hash,long bytes,List<Match> matches){uri=file.uri;documentId=file.id;displayName=file.label;this.hash=hash;byteLength=bytes;this.matches=Collections.unmodifiableList(matches);}
    }
    static final class Diagnostic {
        final Uri uri;final Reason reason;
        private Diagnostic(Uri uri,Reason reason){this.uri=uri;this.reason=reason;}
    }
    static final class Result {
        final Uri tree;final String literal;final State state;final Set<Reason> reasons;
        final List<FileHit> hits;final List<Diagnostic> diagnostics;
        final long bytesRead;final int directoryAttempts,fileAttempts,scannedRows,metadataUnits,candidateFiles,admittedDirectories,skippedTypes;
        private Result(Run run){tree=run.engine.tree;literal=run.query;state=run.cancelled?State.CANCELLED:run.failed?State.FAILED:run.limited?State.LIMITED:State.COMPLETE;
            reasons=Collections.unmodifiableSet(EnumSet.copyOf(run.reasons));hits=Collections.unmodifiableList(run.hits);diagnostics=Collections.unmodifiableList(run.diagnostics);
            bytesRead=run.bytes;directoryAttempts=run.directories;fileAttempts=run.files;scannedRows=run.rows;metadataUnits=run.metadata;candidateFiles=run.admittedFiles;admittedDirectories=run.admittedDirectories;skippedTypes=run.skippedTypes;}
    }
    /** Only this engine mints outcomes. A future process broker must retain this
     * outcome and fail-stop admission if actual owned-file closure is uncertain. */
    static final class Attempt {
        private Run run;private Result result;private InputStream unresolved;
        private WorkspaceTreeReader.Listing cursorReceipt;private Throwable closeFailure;private boolean used;
        private Attempt(Run run){this.run=run;}
        Result execute(){
            synchronized(this){if(used)throw new IllegalStateException("Workspace search attempt is one-use");used=true;}
            try{run.execute();result=new Result(run);return result;}
            finally{
                unresolved=run.ownedInput;closeFailure=run.closeFailure;
                cursorReceipt=run.cursorListing;run=null;
            }
        }
        Result result(){if(!used||result==null)throw new IllegalStateException("No completed search result");return result;}
        CloseDisposition closeDisposition(){return unresolved==null&&cursorReceipt==null?CloseDisposition.CLOSED:CloseDisposition.UNCERTAIN;}
    }
    private static final class Work {
        final WorkspaceTreeReader.Directory directory;final Uri uri;final String id,label;final int depth;
        Work(WorkspaceTreeReader.Directory d,Uri uri,String id,String label,int depth){directory=d;this.uri=uri;this.id=id;this.label=label;this.depth=depth;}
    }
    private static final class Stop extends IOException {}
    private static final class Oversized extends IOException {}
    private final ContentResolver resolver;private final Uri tree;private final WorkspaceTreeReader reader;
    WorkspaceLiteralSearch(ContentResolver applicationResolver,Uri selectedTree){reader=new WorkspaceTreeReader(applicationResolver,selectedTree);resolver=applicationResolver;tree=selectedTree;}
    Attempt prepareSearch(String literal,Limits limits,CancellationSignal cancellation){
        if(literal==null||limits==null||cancellation==null||literal.isEmpty()||literal.length()>limits.queryUnits||literal.length()>limits.snippetUnits)
            throw new IllegalArgumentException("Invalid workspace literal");
        for(int i=0;i<literal.length();i++){char c=literal.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=literal.length()||!Character.isLowSurrogate(literal.charAt(i)))throw new IllegalArgumentException("Malformed literal");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Malformed literal");}
        return new Attempt(new Run(this,literal,limits,cancellation));
    }
    private static final class Run {
        final WorkspaceLiteralSearch engine;final String query;final Limits cap;final CancellationSignal signal;
        final ArrayDeque<Work> queue=new ArrayDeque<>();final Set<String> directoryIds=new HashSet<>(),fileIds=new HashSet<>();
        final List<FileHit> hits=new ArrayList<>();final List<Diagnostic> diagnostics=new ArrayList<>();final EnumSet<Reason> reasons=EnumSet.noneOf(Reason.class);
        final int[] prefix;long bytes;int directories,files,rows,metadata,admittedDirectories,admittedFiles,matchCount,skippedTypes;
        boolean limited,failed,cancelled,stop;InputStream ownedInput;Throwable closeFailure;WorkspaceTreeReader.Listing cursorListing;
        Run(WorkspaceLiteralSearch engine,String query,Limits cap,CancellationSignal signal){this.engine=engine;this.query=query;this.cap=cap;this.signal=signal;prefix=new int[query.length()];for(int i=1,j=0;i<query.length();i++){while(j>0&&query.charAt(i)!=query.charAt(j))j=prefix[j-1];if(query.charAt(i)==query.charAt(j))j++;prefix[i]=j;}}
        void limit(Reason reason){limited=true;reasons.add(reason);}
        void charge(long units)throws Stop {if(units<0||units>cap.metadataUnits-metadata){limit(Reason.METADATA);stop=true;throw new Stop();}metadata+=(int)units;}
        void diagnostic(Work w,Reason reason)throws Stop {failed=true;reasons.add(reason);charge(w.uri.toString().length());diagnostics.add(new Diagnostic(w.uri,reason));}
        void execute(){
            try {
                signal.throwIfCanceled();charge(query.length());String id=android.provider.DocumentsContract.getTreeDocumentId(engine.tree);
                Uri root=android.provider.DocumentsContract.buildDocumentUriUsingTree(engine.tree,id);
                charge(root.toString().length()+2L*id.length());directoryIds.add(id);admittedDirectories=1;
                queue.add(new Work(engine.reader.root(),root,id,id,1));
                while(!queue.isEmpty()&&!stop){signal.throwIfCanceled();Work work=queue.remove();if(work.directory!=null)directory(work);else file(work);}
            }catch(OperationCanceledException failure){cancelled=true;}
            catch(Stop failure){/* Resource refusal already recorded, no uncharged retention. */}
            finally{queue.clear();directoryIds.clear();fileIds.clear();}
            // The final directory close can cancel without another work item.
            // Observe it before Result minting; never release uncertain receipts.
            try{signal.throwIfCanceled();}catch(OperationCanceledException failure){cancelled=true;}
        }
        void directory(Work work)throws Stop {
            if(rows>=cap.rows){limit(Reason.ROWS);stop=true;return;}
            signal.throwIfCanceled();directories++;
            cursorListing=engine.reader.prepareList(work.directory,new WorkspaceTreeReader.Limits(Math.min(256,cap.rows-rows),100,65536,4096),signal);
            WorkspaceTreeReader.Page page;
            try{page=cursorListing.read();}
            finally{if(cursorListing.closeDisposition()==WorkspaceTreeReader.CloseDisposition.CLOSED)cursorListing=null;}
            rows=Math.addExact(rows,page.scannedRows);
            if(cursorListing!=null){failed=true;stop=true;reasons.add(Reason.CLOSE_UNCERTAIN);return;}
            if(page.state==WorkspaceTreeReader.State.CANCELLED){cancelled=true;stop=true;return;}
            if(page.state==WorkspaceTreeReader.State.LIMITED)limit(Reason.DIRECTORY_PARTIAL);
            if(page.state==WorkspaceTreeReader.State.FAILED){diagnostic(work,page.error==WorkspaceTreeReader.Error.ACCESS_REVOKED?Reason.ACCESS_REVOKED:Reason.DIRECTORY_PARTIAL);if(page.error==WorkspaceTreeReader.Error.ACCESS_REVOKED){stop=true;return;}}
            for(WorkspaceTreeReader.Entry entry:page.entries){
                signal.throwIfCanceled();
                if(!engine.tree.getAuthority().equals(entry.uri.getAuthority())){diagnostic(work,Reason.DIRECTORY_PARTIAL);stop=true;return;}
                if(entry.directory!=null){
                    if(directoryIds.contains(entry.documentId)){reasons.add(Reason.CYCLE);continue;}
                    if(work.depth>=cap.depth){limit(Reason.DEPTH);continue;}
                    if(admittedDirectories>=cap.directories){limit(Reason.DIRECTORIES);continue;}
                    charge(entry.uri.toString().length()+2L*entry.documentId.length()+entry.displayName.length());
                    directoryIds.add(entry.documentId);admittedDirectories++;queue.add(new Work(entry.directory,entry.uri,entry.documentId,entry.displayName,work.depth+1));
                }else {
                    String name=entry.displayName.toLowerCase(Locale.ROOT);
                    if(!entry.mimeType.startsWith("text/")&&!name.endsWith(".md")&&!name.endsWith(".markdown")&&!name.endsWith(".txt")){skippedTypes++;continue;}
                    if(admittedFiles>=cap.files){limit(Reason.FILES);continue;}admittedFiles++;
                    if(fileIds.contains(entry.documentId)){reasons.add(Reason.CYCLE);continue;}
                    charge(entry.uri.toString().length()+2L*entry.documentId.length()+entry.displayName.length());
                    fileIds.add(entry.documentId);queue.add(new Work(null,entry.uri,entry.documentId,entry.displayName,work.depth));
                }
            }
            // Page and its entries have no retained owner after conversion above.
        }
        void file(Work work)throws Stop {
            signal.throwIfCanceled();files++;
            InputStream input=null;ScanInput scan=null;Matcher matcher=new Matcher(this);boolean valid=false;java.lang.Error bodyFatal=null;
            try {
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                input=engine.resolver.openInputStream(work.uri);ownedInput=input;if(input==null)throw new IOException("Null document stream");
                signal.throwIfCanceled();scan=new ScanInput(this,input,digest);
                byte[] head=new byte[3];int count=0;for(;count<3;count++){int n=scan.read();if(n<0)break;head[count]=(byte)n;}
                int start=count==3&&(head[0]&255)==239&&(head[1]&255)==187&&(head[2]&255)==191?3:0;
                InputStream joined=new SequenceInputStream(new ByteArrayInputStream(head,start,count-start),scan);
                Reader decoded=new InputStreamReader(joined,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
                char[] chars=new char[4096];int n;
                while((n=decoded.read(chars))!=-1){signal.throwIfCanceled();if(n==0)throw new IOException("Stalled decode");for(int i=0;i<n;i++)matcher.accept(chars[i]);}
                signal.throwIfCanceled();valid=true;
            }catch(OperationCanceledException failure){cancelled=true;stop=true;}
            catch(Stop failure){/* no unvalidated hits escape */}
            catch(Oversized failure){diagnostic(work,Reason.UNREADABLE);}
            catch(SecurityException failure){diagnostic(work,Reason.ACCESS_REVOKED);stop=true;}
            catch(IOException|NoSuchAlgorithmException|RuntimeException failure){diagnostic(work,Reason.UNREADABLE);}
            catch(java.lang.Error fatal){bodyFatal=fatal;throw fatal;}
            finally {
                if(input!=null)try{input.close();ownedInput=null;}
                catch(IOException|RuntimeException failure){closeFailure=failure;valid=false;failed=true;reasons.add(Reason.CLOSE_UNCERTAIN);stop=true;}
                catch(java.lang.Error fatal){closeFailure=fatal;if(bodyFatal==null)throw fatal;}
            }
            signal.throwIfCanceled();
            if(valid&&!cancelled&&!stop&&!matcher.pending.isEmpty()){
                charge(work.uri.toString().length()+work.id.length()+work.label.length()+64L);
                List<Match> matches=new ArrayList<>(matcher.pending.size());
                for(Pending pending:matcher.pending){charge(pending.used);matches.add(new Match(pending));}
                hits.add(new FileHit(work,hex(scan.digest.digest()),scan.count,matches));matchCount+=matches.size();
            }
            if(matcher.capped){limit(Reason.MATCHES);stop=true;}
        }
    }
    private static final class ScanInput extends InputStream {
        final Run run;final InputStream source;final MessageDigest digest;final byte[] one=new byte[1];long count;boolean eof;
        ScanInput(Run run,InputStream source,MessageDigest digest){this.run=run;this.source=source;this.digest=digest;}
        public int read()throws IOException {int n=read(one,0,1);return n<0?-1:one[0]&255;}
        public int read(byte[] bytes,int offset,int length)throws IOException {
            if(length==0)return 0;if(eof)return -1;run.signal.throwIfCanceled();
            long remaining=run.cap.bytes-run.bytes;
            if(remaining==0){run.limit(Reason.BYTES);run.stop=true;throw new Stop();}
            int request=(int)Math.min(length,Math.min(remaining,run.cap.fileBytes-count+1));
            int n=source.read(bytes,offset,request);
            if(n<0){eof=true;return -1;}if(n==0||n>request)throw new IOException("Stalled/invalid document read");
            run.bytes+=n;count+=n;run.signal.throwIfCanceled();
            if(count>run.cap.fileBytes)throw new Oversized();digest.update(bytes,offset,n);return n;
        }
    }
    private static final class Pending {
        final int offset,queryLength,line;final char[] text;int used;
        Pending(int offset,int length,int line,int capacity){this.offset=offset;queryLength=length;this.line=line;text=new char[capacity];}
    }
    private static final class Matcher {
        final Run run;final char[] ring;final int[] lines;final List<Pending> pending=new ArrayList<>();int position,matched,line=1,firstGrowing;boolean previousCR,capped;
        Matcher(Run run){this.run=run;ring=new char[run.cap.snippetUnits];lines=new int[ring.length];}
        void accept(char c)throws Stop {
            for(int i=firstGrowing;i<pending.size();i++){Pending p=pending.get(i);if(position>=p.offset+p.queryLength&&p.used<p.text.length)p.text[p.used++]=c;}
            while(firstGrowing<pending.size()&&pending.get(firstGrowing).used==pending.get(firstGrowing).text.length)firstGrowing++;
            int slot=position%ring.length;ring[slot]=c;lines[slot]=previousCR&&c=='\n'?line-1:line;
            while(matched>0&&c!=run.query.charAt(matched))matched=run.prefix[matched-1];if(c==run.query.charAt(matched))matched++;
            if(matched==run.query.length()){
                if(run.matchCount+pending.size()<run.cap.matches){
                    run.charge(run.cap.snippetUnits);int start=position-run.query.length()+1;
                    Pending p=new Pending(start,run.query.length(),lines[start%ring.length],ring.length);
                    int context=(ring.length-run.query.length())/2,from=Math.max(0,start-context);
                    if(Character.isLowSurrogate(ring[from%ring.length]))from++;
                    for(int at=from;at<=position;at++)p.text[p.used++]=ring[at%ring.length];pending.add(p);
                }
                if(run.matchCount+pending.size()==run.cap.matches)capped=true;
                matched=run.prefix[matched-1];
            }
            if(c=='\r'){line++;previousCR=true;}else {if(c=='\n'&&!previousCR)line++;previousCR=false;}position++;
        }
    }
    private static String hex(byte[] hash){char[] text=new char[64];for(int i=0;i<hash.length;i++){int b=hash[i]&255;text[2*i]="0123456789abcdef".charAt(b>>>4);text[2*i+1]="0123456789abcdef".charAt(b&15);}return new String(text);}
}
