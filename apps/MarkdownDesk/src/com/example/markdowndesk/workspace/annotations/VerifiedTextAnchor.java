package com.example.markdowndesk.workspace.annotations;

/** Source-bound, UTF-16 quote reanchoring. A moved passage may resolve only
 * if its exact quote + up to 64-char context remains UNIQUE in the new value.
 * Missing or ambiguous content returns -1; never guess based on old offset.
 */
public final class VerifiedTextAnchor {
    private VerifiedTextAnchor(){}
    public static int locate(String source,String quote,String before,String after,int oldOffset){
        if(source==null||quote==null||quote.isEmpty()||quote.length()>65536)return -1;
        String prefix=before==null?"":before,suffix=after==null?"":after;
        if(prefix.length()>64||suffix.length()>64)return -1;
        int matched=-1,next=0;
        while(next<=source.length()-quote.length()){
            int at=source.indexOf(quote,next);
            if(at<0)break;
            next=at+1;
            if(at<prefix.length() || at+quote.length()+suffix.length()>source.length())continue;
            if(!source.regionMatches(at-prefix.length(),prefix,0,prefix.length()))continue;
            if(!source.regionMatches(at+quote.length(),suffix,0,suffix.length()))continue;
            if(matched>=0)return -1;
            matched=at;
        }
        return matched;
    }
    public static boolean matchesSelected(String value,String quote,String before,String after,
                                         int start,int end){
        if(value==null||quote==null||start<0||end<start||end-start!=quote.length())return false;
        return start==locate(value,quote,before,after,start);
    }
}
