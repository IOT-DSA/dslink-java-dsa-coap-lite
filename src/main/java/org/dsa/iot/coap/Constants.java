package org.dsa.iot.coap;

import org.dsa.iot.dslink.util.json.JsonObject;

public class Constants {
    public static final String CONN = "conn";
    public static final String REMOTE_NAME = "RemoteDSA";
    public static final String MAIN_SERVER_NAME = "helloWorld";

    public static JsonObject makeCloseReponse(int rid) {
        JsonObject obj = new JsonObject();
        obj.put("rid", rid);
        obj.put("stream", "closed");
        return obj;
    }
}
