package org.mothership.suite;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import java.security.MessageDigest;

/** Strict current-signer pinning; a signing-key rotation needs an explicit protocol update. */
public final class SuiteTrust {
    private SuiteTrust() {}
    @SuppressWarnings("deprecation")
    public static boolean trusted(Context context,String packageName,String expected) {
        try {
            PackageManager pm=context.getPackageManager();
            Signature[] signatures;
            if(Build.VERSION.SDK_INT>=28){
                PackageInfo info=pm.getPackageInfo(packageName,PackageManager.GET_SIGNING_CERTIFICATES);
                if(info.signingInfo==null)return false;
                signatures=info.signingInfo.getApkContentsSigners();
            }else signatures=pm.getPackageInfo(packageName,PackageManager.GET_SIGNATURES).signatures;
            return signatures!=null&&signatures.length==1&&expected.equals(EditorProtocol.hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())));
        }catch(Exception e){return false;}
    }
    public static boolean uidIsMothership(Context context,int uid){
        try{return context.getPackageManager().getApplicationInfo(EditorProtocol.MOTHERSHIP,0).uid==uid&&trusted(context,EditorProtocol.MOTHERSHIP,EditorProtocol.MOTHERSHIP_CERT);}catch(Exception e){return false;}
    }
}
