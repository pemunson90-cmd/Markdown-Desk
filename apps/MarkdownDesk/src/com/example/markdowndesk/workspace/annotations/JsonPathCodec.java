package com.example.markdowndesk.workspace.annotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded, unambiguous JSON Inspector path identity. No Android dependency.
 * A path is a root $ followed only by escaped property keys or array indexes.
 * This is a location in an already-authorized content URI, NOT a file grant.
 */
public final class JsonPathCodec {
    public static final int MAX_STEPS=64,MAX_PATH=8192,MAX_KEY=512;
    public static final class Step {
        public final String key;
        public final int index;
        private Step(String key,int index){this.key=key;this.index=index;}
        public static Step field(String name){
            if(name==null||name.length()>MAX_KEY)throw new IllegalArgumentException("Invalid field");
            return new Step(name,-1);
        }
        public static Step element(int at){
            if(at<0)throw new IllegalArgumentException("Negative array index");
            return new Step(null,at);
        }
    }
    private JsonPathCodec(){}

    public static String format(List<Step> path){
        if(path==null||path.size()>MAX_STEPS)throw new IllegalArgumentException("JSON path too deep");
        StringBuilder out=new StringBuilder("$");
        for(Step step:path){
            if(step==null)throw new IllegalArgumentException("Missing JSON path component");
            if(step.key!=null){
                if(step.key.length()>MAX_KEY)throw new IllegalArgumentException("JSON key too long");
                out.append("[\"");
                for(int i=0;i<step.key.length();i++){
                    char c=step.key.charAt(i);
                    if(c=='"'||c=='\\')out.append('\\').append(c);
                    else if(c=='\n')out.append("\\n");
                    else if(c=='\r')out.append("\\r");
                    else if(c=='\t')out.append("\\t");
                    else if(c<32){
                        String hex=Integer.toHexString(c);
                        out.append("\\u");
                        for(int k=hex.length();k<4;k++)out.append('0');
                        out.append(hex);
                    }else out.append(c);
                }
                out.append("\"]");
            }else {
                if(step.index<0)throw new IllegalArgumentException("Invalid array index");
                out.append('[').append(step.index).append(']');
            }
            if(out.length()>MAX_PATH)throw new IllegalArgumentException("JSON path identity too long");
        }
        return out.toString();
    }

    public static List<Step> parse(String path){
        if(path==null||path.isEmpty()||path.length()>MAX_PATH||path.charAt(0)!='$')
            throw new IllegalArgumentException("Invalid JSON path identity");
        ArrayList<Step> steps=new ArrayList<>();
        int p=1;
        while(p<path.length()){
            if(steps.size()>=MAX_STEPS||path.charAt(p++)!='[')
                throw new IllegalArgumentException("Invalid JSON path shape");
            if(p>=path.length())throw new IllegalArgumentException("Unterminated JSON path");
            if(path.charAt(p)=='"'){
                p++;
                StringBuilder key=new StringBuilder();
                boolean closed=false;
                while(p<path.length()){
                    char c=path.charAt(p++);
                    if(c=='"'){closed=true;break;}
                    if(c=='\\'){
                        if(p>=path.length())throw new IllegalArgumentException("Truncated path escape");
                        char esc=path.charAt(p++);
                        if(esc=='"'||esc=='\\')c=esc;
                        else if(esc=='n')c='\n';
                        else if(esc=='r')c='\r';
                        else if(esc=='t')c='\t';
                        else if(esc=='u'){
                            if(path.length()-p<4)throw new IllegalArgumentException("Truncated unicode escape");
                            int v=0;
                            for(int h=0;h<4;h++){
                                int digit=Character.digit(path.charAt(p++),16);
                                if(digit<0)throw new IllegalArgumentException("Bad unicode escape");
                                v=(v<<4)|digit;
                            }
                            c=(char)v;
                        }else throw new IllegalArgumentException("Unexpected path escape");
                    }else if(c<32)throw new IllegalArgumentException("Unescaped JSON path control");
                    if(key.length()>=MAX_KEY)throw new IllegalArgumentException("JSON path field too long");
                    key.append(c);
                }
                if(!closed||p>=path.length()||path.charAt(p++)!=']')
                    throw new IllegalArgumentException("Unterminated JSON path field");
                steps.add(Step.field(key.toString()));
            }else {
                int start=p,number=0;
                if(path.charAt(p)=='0')p++;
                else {
                    while(p<path.length()&&path.charAt(p)>='0'&&path.charAt(p)<='9'){
                        int digit=path.charAt(p++)-'0';
                        if(number>(Integer.MAX_VALUE-digit)/10)
                            throw new IllegalArgumentException("JSON array index overflow");
                        number=number*10+digit;
                    }
                }
                if(p==start||p>=path.length()||path.charAt(p++)!=']')
                    throw new IllegalArgumentException("Invalid JSON path array index");
                steps.add(Step.element(number));
            }
        }
        if(!format(steps).equals(path))
            throw new IllegalArgumentException("Noncanonical JSON path identity");
        return Collections.unmodifiableList(steps);
    }
}
