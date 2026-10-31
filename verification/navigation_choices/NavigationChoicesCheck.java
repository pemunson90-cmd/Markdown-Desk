package com.example.markdowndesk;
import java.util.*;
import java.lang.reflect.*;

/** Reflection lets the first run assert the missing feature before production code exists. */
public final class NavigationChoicesCheck {
    static int checks;
    static Class<?> model,row,snapshot;
    static Object item(int id,String title,String detail)throws Exception{return row.getDeclaredConstructor(int.class,String.class,String.class).newInstance(id,title,detail);}
    static Object choices(List<?> rows)throws Exception{return model.getDeclaredConstructor(List.class).newInstance(rows);}
    static Object filter(Object c,String text)throws Exception{return model.getDeclaredMethod("filter",String.class).invoke(c,text);}
    static List<?> rows(Object s)throws Exception{return (List<?>)snapshot.getDeclaredField("rows").get(s);}
    static Object choose(Object c,Object s,int position)throws Exception{return model.getDeclaredMethod("choose",snapshot,int.class).invoke(c,s,position);}
    static int id(Object r)throws Exception{return (int)row.getDeclaredField("id").get(r);}
    static void eq(Object a,Object b,String why){checks++;if(!Objects.equals(a,b))throw new AssertionError(why+": expected "+a+", got "+b);}
    public static void main(String[] args)throws Exception{
        try{model=Class.forName("com.example.markdowndesk.NavigationChoices");row=Class.forName(model.getName()+"$Row");snapshot=Class.forName(model.getName()+"$Snapshot");}
        catch(ClassNotFoundException missing){throw new AssertionError("Searchable navigation with stable original identities is not implemented",missing);}
        List<Object> input=new ArrayList<>();input.add(item(7,"Save PDF","Print document"));input.add(item(22,"Café","Heading · line 8"));input.add(item(31,"Café","Heading · line 90"));input.add(item(44,"[Draft].md","content://local/other"));
        Object c=choices(input);input.clear();Object all=filter(c,"");eq(4,rows(all).size(),"immutable inventory");
        Object f=filter(c,"pDf PRINT");eq(1,rows(f).size(),"all terms any order");eq(7,id(choose(c,f,0)),"filtered click uses original identity");eq(null,choose(c,f,0),"selection is one-use");eq(0,rows(filter(c,"")).size(),"consumed picker stays closed");
        c=choices(Arrays.asList(item(1,"Café","line 8"),item(2,"Café","line 90"),item(3,"other","case")));
        f=filter(c,"CAFE\u0301");eq(2,rows(f).size(),"canonical Unicode normalization");eq("line 8",row.getDeclaredField("detail").get(rows(f).get(0)),"duplicate detail retained");
        Object latest=filter(c,"90");eq(null,choose(c,f,0),"stale query cannot dispatch");eq(null,choose(c,latest,-1),"negative row rejected");eq(null,choose(c,latest,5),"out-of-range rejected");eq(2,id(choose(c,latest,0)),"invalid clicks do not consume valid choice");
        c=choices(Arrays.asList(item(4,"[Draft].md","folder"),item(5,"other","draft")));
        eq(1,rows(filter(c,"[draft]")).size(),"literal punctuation not regex");eq(0,rows(filter(c,".*")).size(),"regex operators literal");eq(2,rows(filter(c," \t\n ")).size(),"whitespace resets");
        Object foreign=choices(Arrays.asList(item(4,"[Draft].md","folder")));eq(null,choose(c,filter(foreign,""),0),"foreign picker snapshot rejected");
        f=filter(c,"draft folder");eq(1,rows(f).size(),"title and detail terms combine");model.getDeclaredMethod("cancel").invoke(c);eq(null,choose(c,f,0),"cancel rejects selection");
        c=choices(Collections.emptyList());eq(0,rows(filter(c,"x")).size(),"empty inventory");
        Locale before=Locale.getDefault();try{Locale.setDefault(new Locale("tr","TR"));c=choices(Arrays.asList(item(9,"INSERT","Table")));eq(1,rows(filter(c,"insert")).size(),"locale-independent case");}finally{Locale.setDefault(before);}
        c=choices(Arrays.asList(item(9,"𐐀 😀","line 3")));eq(1,rows(filter(c,"𐐨 😀")).size(),"supplementary Unicode case");
        f=filter(c,"");try{((List)rows(f)).clear();throw new AssertionError("mutable snapshot");}catch(UnsupportedOperationException expected){checks++;}
        try{choices(Arrays.asList(item(1,"A",""),item(1,"B","")));throw new AssertionError("duplicate identity accepted");}catch(InvocationTargetException expected){eq(IllegalArgumentException.class,expected.getCause().getClass(),"duplicate IDs rejected");}
        System.out.println("Navigation choices: "+checks+" assertions PASS");
    }
}
