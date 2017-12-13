package org.dsa.iot.coap;

import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.server.resources.CoapExchange;

import java.io.UnsupportedEncodingException;

public class Constants {
    public static final String CONN = "conn";
    public static final String REMOTE_NAME = "RemoteDSA";
    public static final String MAIN_SERVER_NAME = "helloWorld";
    public static final String RID_PREFIX = "__rid_";
    public static final String REMOTE_RID_FIELD = "RemoteRid";

    public static JsonObject makeCloseReponse(int rid) {
        JsonObject obj = new JsonObject();
        obj.put("rid", rid);
        obj.put("stream", "closed");
        return obj;
    }

    public static byte[] jsonToBytes(JsonObject json) {
        byte[] bytes = new byte[0];
        try {
            bytes = json.toString().getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return bytes;
    }

    public static JsonObject extractPayload(CoapResponse response) {
        return new JsonObject(new String(response.getPayload()));
    }

    public static JsonObject extractPayload(CoapExchange exchange) {
        return new JsonObject(new String(exchange.getRequestPayload()));
    }
}
