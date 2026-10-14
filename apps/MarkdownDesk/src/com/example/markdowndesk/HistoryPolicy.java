package com.example.markdowndesk;

/** Read-only graph configuration descriptor. Does not change any native admission policy. */
public final class HistoryPolicy {
    public final long quotaBytes;
    public final int automaticCount;
    public HistoryPolicy(long quotaBytes,int automaticCount) {
        if(quotaBytes<0||automaticCount<0)throw new IllegalArgumentException("Invalid history policy descriptor");
        this.quotaBytes=quotaBytes;this.automaticCount=automaticCount;
    }
}
