package com.example.markdowndesk;
public final class JsonReaderSelectionCheck {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        String value="before 😈 chosen text after";int at=value.indexOf("chosen"),end=at+6;
        JsonReaderSelection s=JsonReaderSelection.capture("content://docs/1","$[\"text\"]",65536,90000,value,at,end);
        check(s!=null&&s.locate("content://docs/1","$[\"text\"]",65536,90000,value)==at,"exact page restoration");
        JsonReaderSelection recreated=new JsonReaderSelection(s.source,s.path,s.pageStart,s.pageTotal,s.start,s.quote,s.prefix,s.suffix,s.digest);
        check(recreated.locate(s.source,s.path,s.pageStart,s.pageTotal,value)==at,"serialized fields restore");
        check(s.locate("content://docs/2",s.path,s.pageStart,s.pageTotal,value)<0,"wrong source");
        check(s.locate(s.source,"$[\"other\"]",s.pageStart,s.pageTotal,value)<0,"wrong path");
        check(s.locate(s.source,s.path,0,s.pageTotal,value)<0,"wrong scalar page");
        check(s.locate(s.source,s.path,s.pageStart,90001,value)<0,"changed scalar length");
        check(s.locate(s.source,s.path,s.pageStart,s.pageTotal,value.replace("after","later"))<0,"changed source never guessed");
        check(s.locate(s.source,s.path,s.pageStart,s.pageTotal,"x"+value)<0,"moved quote never guessed");
        check(JsonReaderSelection.capture(s.source,s.path,0,300,"x".repeat(300),100,101)==null,"ambiguous passage");
        check(JsonReaderSelection.capture(s.source,s.path,0,90000,"x".repeat(70000),0,70000)==null,"bounded Bundle payload");
        check(JsonReaderSelection.capture(s.source,s.path,0,2,"😈",1,2)==null,"no split surrogate");
        System.out.println("PASS JSON selection snapshot: recreation fields, source/path/page/digest binding, Unicode, ambiguity and bounds");
    }
}
