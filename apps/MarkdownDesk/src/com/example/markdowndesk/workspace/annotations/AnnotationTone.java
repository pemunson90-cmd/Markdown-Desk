package com.example.markdowndesk.workspace.annotations;

import java.util.Locale;

/** An opt-in visual tone inside the existing AJN2 comma-tag field.
 * No journal schema change, no source mutation and no user-supplied CSS.
 * Missing/invalid tone defaults to violet for older saved annotations.
 */
public final class AnnotationTone {
    private static final String PREFIX="md-color:";
    private static final String[] COLORS={"violet","rose","mint","amber","blue","slate"};
    private static final String[] LABELS={"Violet","Rose","Mint","Amber","Blue","Slate"};
    private AnnotationTone(){}

    public static String[] labels(){return LABELS.clone();}
    public static String byIndex(int index){return COLORS[index>=0&&index<COLORS.length?index:0];}

    public static String fromTags(String tags){
        if(tags==null||tags.isEmpty())return COLORS[0];
        String found=COLORS[0];
        for(String item:tags.split(",",-1)){
            String token=item.trim().toLowerCase(Locale.ROOT);
            if(token.startsWith(PREFIX)){
                String candidate=token.substring(PREFIX.length());
                for(String allowed:COLORS)
                    if(allowed.equals(candidate)){found=allowed;break;}
            }
        }
        return found;
    }
    public static int index(String tags){
        String tone=fromTags(tags);
        for(int i=0;i<COLORS.length;i++)if(COLORS[i].equals(tone))return i;
        return 0;
    }
    public static String withTone(String tags,String tone){
        String normalized=tone==null?"":tone.trim().toLowerCase(Locale.ROOT);
        boolean allowed=false;
        for(String color:COLORS)if(color.equals(normalized)){allowed=true;break;}
        if(!allowed)throw new IllegalArgumentException("Unknown annotation color");
        StringBuilder out=new StringBuilder();
        if(tags!=null&&!tags.isEmpty()){
            for(String item:tags.split(",",-1)){
                if(item.trim().toLowerCase(Locale.ROOT).startsWith(PREFIX))continue;
                if(item.isEmpty())continue;
                if(out.length()>0)out.append(',');
                out.append(item); // Retain authored tags, including their case/spaces.
            }
        }
        if(out.length()>0)out.append(',');
        return out.append(PREFIX).append(normalized).toString();
    }
}
