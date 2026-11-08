package com.example.markdowndesk.archive;

/** Explicit, fail-closed passage resolver for regenerated exports.
 * The original message identity must match first; this only locates a selected
 * phrase within that message, never guesses which different message is correct.
 */
public final class ArchiveAnchors {
    public enum Status { FOUND, AMBIGUOUS, UNRESOLVED }
    public static final class Result {
        public final Status status;
        public final int start, end, matches;
        Result(Status status,int start,int end,int matches){
            this.status=status;this.start=start;this.end=end;this.matches=matches;
        }
    }
    public static Result resolve(String messageId,String currentMessageId,
                                 String fullText,ArchiveJournal.Annotation a){
        if(messageId==null||!messageId.equals(currentMessageId)||fullText==null||a==null
                ||a.quote==null||a.quote.isEmpty())return new Result(Status.UNRESOLVED,-1,-1,0);
        int pos=0,valid=0,found=-1,total=0;
        while((pos=fullText.indexOf(a.quote,pos))>=0){
            total++;
            String before=a.prefix==null?"":a.prefix;
            String after=a.suffix==null?"":a.suffix;
            boolean pref=before.isEmpty()||fullText.substring(Math.max(0,pos-before.length()),pos).endsWith(before);
            boolean suff=after.isEmpty()||fullText.substring(pos+a.quote.length(),Math.min(fullText.length(),pos+a.quote.length()+after.length())).startsWith(after);
            if(pref&&suff){valid++;found=pos;}
            pos+=Math.max(1,a.quote.length());
        }
        if(valid==1)return new Result(Status.FOUND,found,found+a.quote.length(),total);
        if(valid>1||total>1)return new Result(Status.AMBIGUOUS,-1,-1,total);
        return new Result(Status.UNRESOLVED,-1,-1,total);
    }
    private ArchiveAnchors(){}
}