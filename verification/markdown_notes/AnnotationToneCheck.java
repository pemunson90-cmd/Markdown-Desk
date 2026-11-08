package com.example.markdowndesk.workspace.annotations;

public final class AnnotationToneCheck {
    static void ok(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    public static void main(String[] args){
        ok(AnnotationTone.labels().length==6,"six fixed colors");
        ok("violet".equals(AnnotationTone.fromTags(null)),"legacy null defaults violet");
        ok("violet".equals(AnnotationTone.fromTags("continuity, style")),"legacy tags default");
        ok("rose".equals(AnnotationTone.fromTags("art, md-color:rose,work")),"exact reserved tag");
        ok("blue".equals(AnnotationTone.fromTags("md-color:BLUE")),"case-insensitive lookup");
        ok("violet".equals(AnnotationTone.fromTags("md-color:injected;url(x)")),"no arbitrary CSS");
        String updated=AnnotationTone.withTone("continuity, md-color:violet,Voice,md-color:slate","mint");
        ok("continuity,Voice,md-color:mint".equals(updated),"replace old tones, retain authored tags");
        ok(AnnotationTone.index(updated)==2,"reopen selected color");
        ok("md-color:amber".equals(AnnotationTone.withTone("","amber")),"empty tags allowed");
        try{AnnotationTone.withTone("work","magenta<script>");throw new AssertionError("unsafe color accepted");}
        catch(IllegalArgumentException expected){}
        try{AnnotationTone.labels()[0]="evil";}catch(Exception ignored){}
        ok("Violet".equals(AnnotationTone.labels()[0]),"labels are immutable copies");
        System.out.println("PASS annotation tone: six fixed colors, legacy tags and safe replacement");
    }
}
