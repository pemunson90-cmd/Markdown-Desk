package org.mothership.suite;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Both APKs compile this same source. No Android dependencies in byte/commit policy. */
public final class EditorProtocol {
    public static final int VERSION=1, MAX_BYTES=8*1024*1024;
    public static final String MOTHERSHIP="net.patosullivan.mothership";
    public static final String DESK="com.example.markdowndesk";
    public static final String COMPONENT=DESK+".SuiteEditorActivity";
    public static final String CAPS="net.patosullivan.mothership.suite.CAPABILITIES_V1";
    public static final String OPEN="net.patosullivan.mothership.suite.OPEN_V1";
    public static final String REQUEST="suite_request", RESULT="suite_result";
    public static final String DESK_CERT="a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01";
    public static final String MOTHERSHIP_CERT="40ec0aed2bfea49a28b40f716e48c4d091d08102411fb4ca7300eda1e154f78f";
    private EditorProtocol() {}
    public static void require(boolean value,String message){if(!value)throw new IllegalArgumentException(message);}
    public static String id(String value){require(value!=null&&value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"),"Invalid session identity");return value;}
    public static String hash(String value){require(value!=null&&value.matches("[0-9a-f]{64}"),"Invalid content hash");return value;}
    public static String hex(byte[] bytes){StringBuilder out=new StringBuilder(bytes.length*2);for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    public static String sha256(byte[] bytes){try{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    public static String token(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return hex(b);}
    public static byte[] read(InputStream input,int limit)throws IOException {
        require(limit>0&&limit<=MAX_BYTES,"Invalid byte limit");
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[32768];int count;
        while((count=input.read(buffer))!=-1){if(out.size()+count>limit)throw new IOException("Document exceeds editor byte limit");out.write(buffer,0,count);}
        return out.toByteArray();
    }
    public static String decode(byte[] bytes)throws CharacterCodingException {
        require(bytes.length<=MAX_BYTES,"Document exceeds editor byte limit");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    public static byte[] encode(String text)throws CharacterCodingException {
        require(text!=null&&text.length()<=MAX_BYTES,"Document exceeds editor byte limit");
        ByteBuffer buffer=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));
        require(buffer.remaining()<=MAX_BYTES,"Document exceeds editor byte limit");byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;
    }
    public static void target(String kind,String mode,String save){
        require("read".equals(mode)||"edit".equals(mode),"Unsupported editor mode");
        require("owned_note".equals(kind)||"source_version".equals(kind),"Unsupported target");
        if("read".equals(mode)){require("none".equals(save),"Read sessions cannot save back");return;}
        require("owned_note".equals(kind)?"note_revision".equals(save):("owned_draft".equals(save)||"proposed_source_version".equals(save)),"Invalid save target for document kind");
    }
    public static void resultIdentity(String session,String token,String base,String actualSession,String actualToken,String actualBase){
        id(session);hash(base);
        require(session.equals(actualSession),"Result belongs to another session");
        require(token!=null&&token.length()>=32&&actualToken!=null&&MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),actualToken.getBytes(StandardCharsets.UTF_8)),"Result token does not match");
        require(base.equals(actualBase),"Result base does not match");
    }
    public static void requireLaunchable(String state){require(state.equals("staged")||state.equals("launched")||state.equals("recovery_required"),"Review the retained result before reopening");}
    public static void keys(Iterator<String> actual,String allowed){
        Set<String> names=new HashSet<>(Arrays.asList(allowed.split(" ")));
        while(actual.hasNext()){String key=actual.next();require(names.contains(key),"Unexpected metadata field: "+key);}
    }
    public static String inputUri(String session){return "content://"+MOTHERSHIP+".editor/working/"+id(session)+"/input.md";}
    public static String resultUri(String session){return "content://"+DESK+".suite/session/"+id(session)+"/result.md";}
    public enum Decision {COMMIT,UNCHANGED,IDEMPOTENT,CONFLICT}
    public static Decision decision(String base,String result,String alreadyCommitted,boolean editable,boolean sameScope,boolean sameBase){
        hash(base);hash(result);require(sameScope,"Project or branch changed");
        if(alreadyCommitted!=null&&!alreadyCommitted.isEmpty()){
            require(alreadyCommitted.equals(result),"Different result for a completed session");return Decision.IDEMPOTENT;
        }
        if(base.equals(result))return Decision.UNCHANGED;
        require(editable,"Read session returned changed content");
        return sameBase?Decision.COMMIT:Decision.CONFLICT;
    }
}
