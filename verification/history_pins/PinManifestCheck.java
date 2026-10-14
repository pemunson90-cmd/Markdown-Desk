package com.example.markdowndesk;
import java.io.*;
import java.security.MessageDigest;
import static com.example.markdowndesk.HistoryPinRecord.*;
import static com.example.markdowndesk.HistoryPinsCheck.*;
/** A 16MiB input is streamed under a 16MiB JVM heap, with no raw manifest retention. */
public final class PinManifestCheck {
    static InputStream zeros(long count){return new InputStream(){long remaining=count;public int read(){return remaining-->0?0:-1;}public int read(byte[] bytes,int offset,int length){if(remaining==0)return -1;int n=(int)Math.min(length,remaining);java.util.Arrays.fill(bytes,offset,offset+n,(byte)0);remaining-=n;return n;}};}
    public static void main(String[] args)throws Exception {
        HistoryPins.ManifestIdentity identity=HistoryPins.ManifestIdentity.read(Domain.NATIVE_HISTORY_V1,MAX_MANIFEST_COUNT,zeros(MAX_MANIFEST_BYTES));
        MessageDigest digest=HistoryStorage.digest();byte[] buffer=new byte[8192];for(long n=0;n<MAX_MANIFEST_BYTES;n+=buffer.length)digest.update(buffer);
        check(identity.value.length==MAX_MANIFEST_BYTES&&identity.value.hash.equals(HistoryStorage.hex(digest.digest())),"streamed exact maximum identity");
        rejects(()->HistoryPins.ManifestIdentity.read(Domain.NATIVE_HISTORY_V1,0,zeros(MAX_MANIFEST_BYTES+1)),"manifest byte cap");
        rejects(()->HistoryPins.ManifestIdentity.read(Domain.NATIVE_HISTORY_V1,MAX_MANIFEST_COUNT+1,zeros(0)),"manifest count cap");
        rejects(()->HistoryPins.ManifestIdentity.read(Domain.NATIVE_HISTORY_V1,0,new InputStream(){public int read(){return 0;}public int read(byte[] b,int o,int n){return 0;}}),"no-progress stream rejected");
        System.out.println("History pin manifest: "+checks+" checks PASS (16MiB heap)");
    }
}
