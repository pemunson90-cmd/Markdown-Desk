package android.provider;
import android.net.Uri;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
/** Models DocumentsContract path segment layout and UTF-8 URI encoding only;
 * grant enforcement and Android provider behavior remain external boundaries. */
public final class DocumentsContract {
 public static boolean isTreeUri(Uri u){return u.toString().contains("/tree/");}
 public static String getTreeDocumentId(Uri u){String raw=u.toString().split("/tree/",2)[1].split("/",2)[0];return URLDecoder.decode(raw.replace("+","%2B"),StandardCharsets.UTF_8);}
 private static String encode(String value){StringBuilder out=new StringBuilder();for(byte b:value.getBytes(StandardCharsets.UTF_8)){int c=b&255;if(c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9'||"_-!.~'()*".indexOf(c)>=0)out.append((char)c);else out.append('%').append("0123456789ABCDEF".charAt(c>>>4)).append("0123456789ABCDEF".charAt(c&15));}return out.toString();}
 private static String base(Uri t){return "content://"+t.getAuthority()+"/tree/"+encode(getTreeDocumentId(t));}
 public static Uri buildDocumentUriUsingTree(Uri t,String id){return Uri.parse(base(t)+"/document/"+encode(id));}
 public static Uri buildChildDocumentsUriUsingTree(Uri t,String id){return Uri.parse(base(t)+"/document/"+encode(id)+"/children");}
 public static final class Document {public static final String COLUMN_DOCUMENT_ID="document_id",COLUMN_DISPLAY_NAME="_display_name",COLUMN_MIME_TYPE="mime_type",COLUMN_SIZE="_size",COLUMN_LAST_MODIFIED="last_modified",COLUMN_FLAGS="flags",MIME_TYPE_DIR="vnd.android.document/directory";}
}
