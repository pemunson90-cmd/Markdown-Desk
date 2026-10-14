package com.example.markdowndesk;

import java.io.*;
import java.util.*;

/** Explicit candidate/read capability. It is never installed until the whole bundle validates. */
final class HistoryTombstoneView {
    final HistoryMaintenanceRecovery owner;
    final HistoryPruneIntent intent;
    private final Set<String> members;
    HistoryTombstoneView(HistoryMaintenanceRecovery owner,HistoryPruneIntent intent){
        this.owner=owner;this.intent=intent;Set<String> keys=new HashSet<>();
        for(HistoryPruneIntent.Member member:intent.members)keys.add(member.key());members=Collections.unmodifiableSet(keys);
    }
    boolean contains(String role,String filename){return members.contains(role+"/"+filename);}
    void requireVisible(String role,String filename)throws IOException {
        if(contains(role,filename))throw new IOException("Source is covered by committed maintenance");
    }
    void filter(String role,HistoryStorage.FilesView files){files.records.removeIf(record->contains(role,record.filename));}
    HistoryTombstoneView revalidate()throws IOException {return owner.validateInstalled(this);}
}
