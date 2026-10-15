package com.example.markdowndesk;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Immutable bounded scalar selection. D supplies its real read lease and payload factories later.
 * Selection values alone grant no source, UI accepted-input, publication or URI authority. */
final class PortableSelection {
    final List<PrivateStateCodec.Tab> tabs;
    final String activeSourceTabId;
    final PrivateStateCodec.Recovery recovery;
    final Map<String,Object> preferences;
    private final PreparationSources owner;private final HistoryStorage.Budget budget;
    private final List<String> sourceHashes;
    private PortableSelection(PreparationSources owner,HistoryStorage.Budget budget,List<PrivateStateCodec.Tab> tabs,
            String active,PrivateStateCodec.Recovery recovery,Map<String,Object> preferences,List<String> hashes) {
        this.owner=owner;this.budget=budget;this.tabs=Collections.unmodifiableList(tabs);activeSourceTabId=active;
        this.recovery=recovery;this.preferences=Collections.unmodifiableMap(preferences);sourceHashes=Collections.unmodifiableList(hashes);
    }
    static PortableSelection freeze(PreparationSources owner,HistoryStorage.Budget budget,List<PrivateStateCodec.Tab> tabs,
            String active,PrivateStateCodec.Recovery recovery,Map<String,Object> preferences)throws IOException {
        if(owner==null||budget==null||tabs==null||tabs.size()>12||preferences==null||preferences.size()!=8)throw new IOException("Incomplete portable scalar selection");
        owner.reserveMetadata(budget,(256L<<10)+65536L*(tabs.size()+(recovery==null?0:1)));
        List<PrivateStateCodec.Tab> copy=new ArrayList<>(tabs);List<String> hashes=new ArrayList<>(tabs.size());Set<String> ids=new HashSet<>();
        for(PrivateStateCodec.Tab tab:copy){if(tab==null||tab.sourceTabId==null||tab.sourceTabId.isEmpty()||!ids.add(tab.sourceTabId))throw new IOException("Duplicate/missing selected source tab");
            hashes.add(sourceHash(tab.sourceTabId));}
        if((copy.isEmpty())!=(active==null)||active!=null&&!ids.contains(active))throw new IOException("Original active tab is outside selection");
        Map<String,Object> prefs=new LinkedHashMap<>(preferences);PrivateStateCodec.writePreferences(prefs,new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}});
        return new PortableSelection(owner,budget,copy,active,recovery,prefs,hashes);
    }
    void require(PreparationSources expected,HistoryStorage.Budget supplied,List<PrivateFramePreflight.ClosedFrame> frames)throws IOException {
        if(owner!=expected||budget!=supplied||frames==null||frames.size()!=tabs.size()+(recovery==null?0:1)+1)throw new IOException("Foreign portable selection/frames");
        int at=0;
        for(int i=0;i<tabs.size();i++){
            PrivateFramePreflight.ClosedFrame f=frames.get(at++);
            if(f==null||!f.belongsTo(owner)||f.kind!=PrivateStateCodec.Kind.SESSION_TAB||f.ordinal!=i||!sourceHashes.get(i).equals(f.sourceTabIdHash))throw new IOException("Preflight differs from original selected tab/order");
        }
        if(recovery!=null){PrivateFramePreflight.ClosedFrame f=frames.get(at++);if(f==null||!f.belongsTo(owner)||f.kind!=PrivateStateCodec.Kind.RECOVERY||f.ordinal!=0)throw new IOException("Selected recovery differs from preflight");}
        PrivateFramePreflight.ClosedFrame pref=frames.get(at);if(pref==null||!pref.belongsTo(owner)||pref.kind!=PrivateStateCodec.Kind.PREFERENCES||pref.ordinal!=0)throw new IOException("Selected preferences differ from preflight");
    }
    int activeOrdinal(){for(int i=0;i<tabs.size();i++)if(tabs.get(i).sourceTabId.equals(activeSourceTabId))return i;return -1;}
    private static String sourceHash(String id)throws IOException {
        if(id.length()>PrivateStateCodec.MAX_STRING_BYTES)throw new IOException("Selected tab ID bound");
        try{ByteBuffer b=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(id));
            if(b.remaining()>PrivateStateCodec.MAX_STRING_BYTES)throw new IOException("Selected tab ID byte bound");
            java.security.MessageDigest hash=HistoryStorage.digest();hash.update(b);return HistoryStorage.hex(hash.digest());
        }catch(CharacterCodingException invalid){throw new IOException("Selected tab ID UTF8",invalid);}
    }
}
