package com.example.markdowndesk.workspace.archive;

import java.util.Random;

public final class ArchiveViewportTest {
    static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    public static void main(String[] args){
        ArchiveViewport empty=new ArchiveViewport(0,0);
        check(empty.start()==0&&empty.end()==0,"empty bounds");
        check(empty.before()==null&&empty.after()==null,"empty loading");
        for(int total:new int[]{1,3,11,12,13,24,35,36,37,70_000}){
            for(int target:new int[]{0,1,11,12,34,123,69_999}){
                ArchiveViewport v=new ArchiveViewport(total,target);
                int lastStart=v.start(),lastEnd=v.end();
                check(lastEnd-lastStart<=ArchiveViewport.PAGE,"initial cap");
                while(v.after()!=null){
                    ArchiveViewport.Load load=v.after();
                    check(load.offset==lastEnd,"forward no skips");
                    ArchiveViewport.Change change=v.insert(load,load.count);
                    check(change.start<=change.end&&change.end<=total,"valid forward bounds");
                    check(change.end-change.start<=ArchiveViewport.MAX_VISIBLE,"window capped forward");
                    lastStart=change.start;lastEnd=change.end;
                }
                check(v.end()==total,"all messages reachable going forward");
                while(v.before()!=null){
                    ArchiveViewport.Load load=v.before();
                    check(load.offset+load.count==v.start(),"backward adjacent");
                    ArchiveViewport.Change change=v.insert(load,load.count);
                    check(change.end-change.start<=ArchiveViewport.MAX_VISIBLE,"window capped backward");
                }
                check(v.start()==0,"all messages reachable going backward");
            }
        }
        ArchiveViewport v=new ArchiveViewport(1_000,321);
        ArchiveViewport.Load pending=v.after();
        v.insert(pending,pending.count);
        try{v.insert(pending,pending.count);throw new AssertionError("accepted stale request");}
        catch(IllegalStateException expected){}
        System.out.println("PASS: 70,000-message bounded bidirectional viewport, exact boundaries and stale pages");
    }
}