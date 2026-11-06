package com.example.markdowndesk.workspace.archive;

/** Bounded presentation window for an arbitrarily long ordered conversation.
 *
 * This is a UI-memory bound, NOT a content limit. As the reader scrolls, it
 * appends/prepends adjacent messages and releases offscreen DOM nodes. All
 * messages remain in the indexed database and can be loaded in either direction.
 * Inclusive start, exclusive end; ordinal 0 is the first message.
 */
public final class ArchiveViewport {
    public static final int PAGE = 12;
    public static final int MAX_VISIBLE = 36;

    public static final class Load {
        public final int offset, count, direction;
        Load(int offset, int count, int direction) {
            this.offset=offset;this.count=count;this.direction=direction;
        }
    }
    public static final class Change {
        public final int removedAtFront, removedAtBack, start, end;
        Change(int front,int back,int start,int end){
            removedAtFront=front;removedAtBack=back;this.start=start;this.end=end;
        }
    }

    private final int total;
    private int start, end;
    public ArchiveViewport(int messageCount, int targetOrdinal){
        if(messageCount<0)throw new IllegalArgumentException("Negative conversation length");
        total=messageCount;
        int target=messageCount==0?0:Math.max(0,Math.min(targetOrdinal,messageCount-1));
        start=(target/PAGE)*PAGE;
        end=Math.min(total,start+PAGE);
    }
    /** A search/deep link may begin in a later section of one very long
     * message. Neighboring messages must still start at their beginning.
     */
    public static int firstPart(int ordinal,int targetedOrdinal,int targetedPart){
        return ordinal==targetedOrdinal?Math.max(0,targetedPart):0;
    }
    public int start(){return start;}
    public int end(){return end;}
    public int total(){return total;}
    public boolean hasBefore(){return start>0;}
    public boolean hasAfter(){return end<total;}
    public Load before(){
        if(!hasBefore())return null;
        int begin=Math.max(0,start-PAGE);
        return new Load(begin,start-begin,-1);
    }
    public Load after(){
        if(!hasAfter())return null;
        return new Load(end,Math.min(PAGE,total-end),1);
    }
    /** Must be called only after a matching database batch has been loaded.
     * Safe even when a provider returned fewer than the requested items.
     */
    public Change insert(Load load, int actualCount){
        if(load==null||actualCount<0||actualCount>load.count)
            throw new IllegalArgumentException("Invalid viewport load result");
        if(load.direction==1){
            if(load.offset!=end)throw new IllegalStateException("Stale forward page");
            end+=actualCount;
            int front=Math.max(0,end-start-MAX_VISIBLE);
            start+=front;
            return new Change(front,0,start,end);
        }
        if(load.direction==-1){
            if(load.offset+load.count!=start)throw new IllegalStateException("Stale backward page");
            if(actualCount!=load.count)
                throw new IllegalStateException("Incomplete backward page cannot preserve sequence");
            start=load.offset;
            int back=Math.max(0,end-start-MAX_VISIBLE);
            end-=back;
            return new Change(0,back,start,end);
        }
        throw new IllegalArgumentException("Unknown direction");
    }
}