package org.mothership.suite;

import org.json.*;
import java.time.Instant;
import static org.mothership.suite.EditorProtocol.*;

/** Strict DTO validation shared by both Android endpoints; transport contains metadata only. */
public final class EditorJson {
    private EditorJson(){}
    private static String string(JSONObject obj,String key,int minimum,int maximum)throws JSONException{
        Object raw=obj.get(key);require(raw instanceof String,"Expected string: "+key);String value=(String)raw;
        require(value.length()>=minimum&&value.length()<=maximum,"Invalid length: "+key);return value;
    }
    private static long integer(JSONObject obj,String key,long low,long high)throws JSONException{
        Object raw=obj.get(key);require(raw instanceof Integer||raw instanceof Long,"Expected integer: "+key);long value=((Number)raw).longValue();require(value>=low&&value<=high,"Invalid range: "+key);return value;
    }
    public static void request(JSONObject obj)throws JSONException{
        keys(obj.keys(),"protocol_version session_id correlation_token project_id branch_id target mode save_target staging_uri mime_type encoding max_bytes expected_receiver_package expected_receiver_certificate_sha256 created_at");
        integer(obj,"protocol_version",VERSION,VERSION);String session=id(string(obj,"session_id",36,36));string(obj,"correlation_token",32,256);
        string(obj,"project_id",1,200);string(obj,"branch_id",1,200);
        JSONObject target=obj.getJSONObject("target");keys(target.keys(),"kind id base_revision_id base_sha256");
        String kind=string(target,"kind",1,32);string(target,"id",1,200);string(target,"base_revision_id",1,200);hash(string(target,"base_sha256",64,64));
        target(kind,string(obj,"mode",1,8),string(obj,"save_target",1,32));
        require(inputUri(session).equals(string(obj,"staging_uri",1,1024)),"Unexpected working-copy URI");
        String mime=string(obj,"mime_type",1,80);require(mime.equals("text/markdown")||mime.equals("text/plain"),"Unsupported MIME type");
        require(string(obj,"encoding",1,16).equals("UTF-8"),"Unsupported encoding");integer(obj,"max_bytes",1,MAX_BYTES);
        require(string(obj,"expected_receiver_package",1,200).equals(DESK),"Receiver package mismatch");
        require(string(obj,"expected_receiver_certificate_sha256",64,64).equals(DESK_CERT),"Receiver certificate mismatch");
        Instant.parse(string(obj,"created_at",1,80));
    }
    public static void result(JSONObject obj)throws JSONException{
        keys(obj.keys(),"protocol_version session_id correlation_token status base_sha256 result_uri result_sha256 byte_count error_code completed_at");
        integer(obj,"protocol_version",VERSION,VERSION);String session=id(string(obj,"session_id",36,36));string(obj,"correlation_token",32,256);hash(string(obj,"base_sha256",64,64));
        String status=string(obj,"status",1,20);require(status.equals("saved")||status.equals("unchanged")||status.equals("cancelled")||status.equals("failed"),"Unknown result status");
        if(status.equals("saved")||obj.has("result_uri"))require(resultUri(session).equals(string(obj,"result_uri",1,1024)),"Unexpected result URI");
        if(status.equals("saved")||obj.has("result_sha256"))hash(string(obj,"result_sha256",64,64));
        if(status.equals("saved")||obj.has("byte_count"))integer(obj,"byte_count",0,MAX_BYTES);
        if(status.equals("failed")||obj.has("error_code"))string(obj,"error_code",1,80);
        Instant.parse(string(obj,"completed_at",1,80));
    }
}
