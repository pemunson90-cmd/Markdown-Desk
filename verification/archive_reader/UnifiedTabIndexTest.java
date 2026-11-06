package com.example.markdowndesk.workspace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class UnifiedTabIndexTest {
    static void ok(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] ignored){
        List<UnifiedTabIndex.Entry> md=new ArrayList<>(),json=new ArrayList<>();
        for(int i=0;i<14;i++){
            md.add(new UnifiedTabIndex.Entry(UnifiedTabIndex.Kind.MARKDOWN,"id"+i,"Write "+i,i==0,i==2));
            json.add(new UnifiedTabIndex.Entry(UnifiedTabIndex.Kind.ARCHIVE,"id"+i,"Read "+i,i==1,false));
        }
        List<UnifiedTabIndex.Entry> all=UnifiedTabIndex.merge(md,json);
        ok(all.size()==24,"preserve 12 per workspace, 24 total");
        ok(all.get(0).active&&all.get(2).dirty,"markdown activity state");
        ok(all.get(13).active&&all.get(13).kind==UnifiedTabIndex.Kind.ARCHIVE,"separate type identity");
        md.add(md.get(0));
        ok(UnifiedTabIndex.merge(md,json).size()==24,"deduplicate type/id pairs");
        try{all.clear();throw new AssertionError("registry list mutable");}
        catch(UnsupportedOperationException expected){}
        ok(UnifiedTabIndex.merge(Arrays.asList(md.get(0)),Arrays.asList(json.get(0))).size()==2,"same IDs allowed across source types");
        System.out.println("PASS: unified tab identity, retention and source types");
    }
}