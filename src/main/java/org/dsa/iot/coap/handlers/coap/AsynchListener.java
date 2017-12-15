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
        String val = json.get(Constants.BLANK_KEY);
        return !(val != null && val.equals(Constants.BLANK_VAL));
    }

    @Override
    public void onLoad(CoapResponse response) {
        JsonObject json = Constants.extractPayload(response);
        if (json.get("rid") == null) System.out.println("No RID!" + json); //DEBUG
        else if (json.get("rid").equals(0)) System.out.println("GOT UPDATE:" + json); //DEBUG
        if (notBlank(json)) linkHandler.getResponderLink().getWriter().writeResponse(json);
    }

    @Override
    public void onError() {
        System.err.println("Error");
    }
}
