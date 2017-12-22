package org.dsa.iot.coap.handlers.coap;

import org.dsa.iot.coap.CoapLinkHandler;
import org.dsa.iot.coap.Constants;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;

/**
 * @author James (Juris) Puchin
 * Created on 12/12/2017
 */
public class AsynchListener implements CoapHandler {
    CoapLinkHandler linkHandler;

    public AsynchListener(CoapLinkHandler linkHandler) {
        this.linkHandler = linkHandler;
    }

    private boolean notBlank(JsonObject json) {
        if (json == null) return false;
        String val = json.get(Constants.BLANK_KEY);
        return !(val != null && val.equals(Constants.BLANK_VAL));
    }

    @Override
    public void onLoad(CoapResponse response) {
        try {
        JsonObject json = Constants.extractPayloadObject(response);

            if (notBlank(json)) {
                //if (json.get("rid") != null && json.get("rid").equals(0)) System.out.println("GOT UPDATE:" + json); //DEBUG
                //System.out.println("GOT MESSAGE:" + json); //DEBUG
                linkHandler.getResponderLink().getWriter().writeResponse(json);
            }
        } catch (Exception e) {
            response.advanced().setRejected(true);
            System.out.println("Message rejected!");
        }

        //TODO: handle killing listeners on close and stream close
    }

    @Override
    public void onError() {
        throw new RuntimeException("Error: Coap AsynchListener Failed!");
    }
}
