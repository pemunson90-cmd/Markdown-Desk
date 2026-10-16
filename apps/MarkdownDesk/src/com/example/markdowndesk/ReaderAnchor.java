package com.example.markdowndesk;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Source-block anchors only. Caller supplies an immutable snapshot for the entire call.
 * No source snapshot or encoded whole-document copy is retained or created here. */
final class ReaderAnchor {
    static final int MAX_SOURCE=8<<20,MAX_QUOTE=160,MAX_CONTEXT=64;
    enum Kind {TEXT,HEADING}
    enum Status {EXACT,RELOCATED,UNRESOLVED}
    interface Cancellation {void check()throws IOException;}
    static final class Anchor {
        final Kind kind;
        final String sourceHash,quote,prefix,suffix;
        final int sourceLength,offset;
        private Anchor(Kind kind,String sourceHash,int sourceLength,int offset,String quote,String prefix,String suffix){
            this.kind=kind;this.sourceHash=sourceHash;this.sourceLength=sourceLength;this.offset=offset;
            this.quote=quote;this.prefix=prefix;this.suffix=suffix;
        }
    }
    static final class Resolution {
        final Status status;final int offset,matchedLength,hashedUnits;final long searchComparisons;
        private Resolution(Status status,int offset,int matchedLength,int hashedUnits,long comparisons){
            this.status=status;this.offset=offset;this.matchedLength=matchedLength;
            this.hashedUnits=hashedUnits;searchComparisons=comparisons;
        }
    }
    private ReaderAnchor(){}
    static Anchor capture(CharSequence source,int offset,Kind kind,Cancellation cancellation)throws IOException {
        if(source==null||kind==null||offset<0||offset>source.length())throw invalid();
        String hash=fingerprint(source,cancellation);
        int at=boundaryDown(source,offset),end=boundaryDown(source,Math.min(source.length(),at+MAX_QUOTE));
        int before=boundaryUp(source,Math.max(0,at-MAX_CONTEXT)),after=boundaryDown(source,Math.min(source.length(),end+MAX_CONTEXT));
        Anchor captured=new Anchor(kind,hash,source.length(),at,copy(source,at,end),copy(source,before,at),copy(source,end,after));
        cancellation.check();return captured;
    }
    /** Hydration accepts only bounded, internally consistent metadata; it confers no source authority. */
    static Anchor restore(Kind kind,String hash,int length,int offset,String quote,String prefix,String suffix)throws IOException {
        if(kind==null||hash==null||hash.length()!=64||length<0||length>MAX_SOURCE||offset<0||offset>length)throw invalid();
        for(int i=0;i<hash.length();i++){char c=hash.charAt(i);if(!(c>='0'&&c<='9'||c>='a'&&c<='f'))throw invalid();}
        bounded(quote,MAX_QUOTE);bounded(prefix,MAX_CONTEXT);bounded(suffix,MAX_CONTEXT);
        if(prefix.length()>offset||quote.length()>length-offset||suffix.length()>length-offset-quote.length()
                ||length==0&&(!quote.isEmpty()||!prefix.isEmpty()||!suffix.isEmpty())
                ||length>0&&quote.isEmpty()&&offset!=length)throw invalid();
        return new Anchor(kind,hash,length,offset,quote,prefix,suffix);
    }
    static Resolution resolve(Anchor anchor,CharSequence source,Cancellation cancellation)throws IOException {
        if(anchor==null)throw invalid();String hash=fingerprint(source,cancellation);int length=source.length();
        if(length==anchor.sourceLength&&hash.equals(anchor.sourceHash)){
            if(anchor.offset>length||boundaryDown(source,anchor.offset)!=anchor.offset
                    ||anchor.offset+anchor.quote.length()>length
                    ||boundaryDown(source,anchor.offset+anchor.quote.length())!=anchor.offset+anchor.quote.length())throw invalid();
            String context=anchor.prefix+anchor.quote+anchor.suffix;int start=anchor.offset-anchor.prefix.length();
            if(start<0||context.length()>length-start)throw invalid();
            for(int i=0;i<context.length();i++)if(source.charAt(start+i)!=context.charAt(i))throw invalid();
            return completed(new Resolution(Status.EXACT,anchor.offset,anchor.quote.length(),length,0),cancellation);
        }
        if(anchor.sourceLength==0)return completed(new Resolution(Status.UNRESOLVED,-1,0,length,0),cancellation);
        String pattern=anchor.prefix+anchor.quote+anchor.suffix;
        if(pattern.isEmpty())return completed(new Resolution(Status.UNRESOLVED,-1,0,length,0),cancellation);
        // Count every pattern/search character comparison. Prefix construction <2M;
        // search <2N. No repeated per-candidate context scans and no source substring.
        Progress progress=new Progress(cancellation);int[] failure=new int[pattern.length()];
        for(int i=1,j=0;i<pattern.length();){progress.comparison();if(pattern.charAt(i)==pattern.charAt(j)){failure[i++]=++j;}else if(j>0)j=failure[j-1];else i++;}
        int found=-1;
        for(int i=0,j=0;i<length;){
            progress.comparison();if(source.charAt(i)==pattern.charAt(j)){
                i++;j++;
                if(j==pattern.length()){
                    int candidate=i-pattern.length()+anchor.prefix.length();
                    if(boundaryDown(source,candidate)==candidate&&boundaryDown(source,candidate+anchor.quote.length())==candidate+anchor.quote.length()){
                        if(found>=0)return completed(new Resolution(Status.UNRESOLVED,-1,0,length,progress.comparisons),cancellation);found=candidate;
                    }
                    j=failure[j-1];
                }
            }else if(j>0)j=failure[j-1];else i++;
        }
        return completed(new Resolution(found<0?Status.UNRESOLVED:Status.RELOCATED,found,found<0?0:anchor.quote.length(),length,progress.comparisons),cancellation);
    }
    /** One non-finally terminal gate, including exact and early-unresolved paths. */
    private static Resolution completed(Resolution result,Cancellation cancellation)throws IOException {
        cancellation.check();return result;
    }
    private static final class Progress {
        final Cancellation cancel;long comparisons;
        Progress(Cancellation cancel)throws IOException{this.cancel=cancel;cancel.check();}
        void comparison()throws IOException{if((comparisons++&4095)==0)cancel.check();}
    }
    private static String fingerprint(CharSequence source,Cancellation cancellation)throws IOException {
        if(source==null||cancellation==null||source.length()>MAX_SOURCE)throw invalid();
        cancellation.check();MessageDigest digest;
        try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException absent){throw new IOException("Reading fingerprint unavailable.");}
        byte[] buffer=new byte[8192];int used=0,next=0;long bytes=0;
        for(int i=0;i<source.length();){
            if(i>=next){cancellation.check();next=i+4096;}
            char ch=source.charAt(i++);int cp=ch;
            if(Character.isHighSurrogate(ch)){
                if(i==source.length()||!Character.isLowSurrogate(source.charAt(i)))throw invalid();cp=Character.toCodePoint(ch,source.charAt(i++));
            }else if(Character.isLowSurrogate(ch))throw invalid();
            int size=cp<128?1:cp<2048?2:cp<65536?3:4;bytes+=size;if(bytes>MAX_SOURCE)throw invalid();
            if(used+size>buffer.length){digest.update(buffer,0,used);used=0;}
            if(size==1)buffer[used++]=(byte)cp;
            else{
                if(size==2)buffer[used++]=(byte)(0xc0|(cp>>>6));
                else if(size==3){buffer[used++]=(byte)(0xe0|(cp>>>12));buffer[used++]=(byte)(0x80|((cp>>>6)&63));}
                else{buffer[used++]=(byte)(0xf0|(cp>>>18));buffer[used++]=(byte)(0x80|((cp>>>12)&63));buffer[used++]=(byte)(0x80|((cp>>>6)&63));}
                buffer[used++]=(byte)(0x80|(cp&63));
            }
        }
        digest.update(buffer,0,used);StringBuilder hex=new StringBuilder(64);for(byte b:digest.digest()){hex.append(Character.forDigit((b>>>4)&15,16));hex.append(Character.forDigit(b&15,16));}cancellation.check();return hex.toString();
    }
    private static int boundaryDown(CharSequence source,int at){return at>0&&at<source.length()&&Character.isLowSurrogate(source.charAt(at))&&Character.isHighSurrogate(source.charAt(at-1))?at-1:at;}
    private static int boundaryUp(CharSequence source,int at){return at>0&&at<source.length()&&Character.isLowSurrogate(source.charAt(at))&&Character.isHighSurrogate(source.charAt(at-1))?at+1:at;}
    private static String copy(CharSequence source,int start,int end){StringBuilder text=new StringBuilder(end-start);for(int i=start;i<end;i++)text.append(source.charAt(i));return text.toString();}
    private static void bounded(String text,int max)throws IOException {
        if(text==null||text.length()>max)throw invalid();
        for(int i=0;i<text.length();i++){char ch=text.charAt(i);if(Character.isHighSurrogate(ch)){if(++i==text.length()||!Character.isLowSurrogate(text.charAt(i)))throw invalid();}else if(Character.isLowSurrogate(ch))throw invalid();}
    }
    private static IOException invalid(){return new IOException("Reading place is invalid or exceeds its limit.");}
}
