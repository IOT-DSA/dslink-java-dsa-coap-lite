package org.dsa.iot.coap.handlers.coap;

import org.dsa.iot.coap.CoapLinkHandler;
import org.dsa.iot.coap.Constants;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;

/**
 * @author James (Juris) Puchin
 * Created on 12/20/2017
 */
public class AsynchBatchListener implements CoapHandler {
    CoapLinkHandler linkHandler;

    public AsynchBatchListener(CoapLinkHandler linkHandler) {
        this.linkHandler = linkHandler;
    }

    private boolean notBlank(JsonArray json) {
        if (json == null || json.size() < 1) return false;
        //System.out.println("GOT ARRAY:" + json);
        JsonObject obj = json.get(0);
        String val = obj.get(Constants.BLANK_KEY);
        return !(val != null && val.equals(Constants.BLANK_VAL));
    }

    @Override
    public void onLoad(CoapResponse response) {
        JsonArray jsonAr = Constants.extractPayloadArray(response);

        if (notBlank(jsonAr)) {
            //if (json.get("rid") != null && json.get("rid").equals(0)) System.out.println("GOT UPDATE:" + json); //DEBUG
            for (Object o : jsonAr) {
                JsonObject json = (JsonObject) o;
                //System.out.println("GOT MESSAGE:" + json); //DEBUG
                linkHandler.getResponderLink().getWriter().writeResponse(json);
                //TODO: handle killing listeners on close and stream close
            }
        }
    }

    @Override
    public void onError() {
        throw new RuntimeException("Error: Coap AsynchListener Failed!");
    }
}