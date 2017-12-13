package org.dsa.iot.coap.handlers.coap;

import org.dsa.iot.coap.CoapLinkHandler;
import org.dsa.iot.coap.Constants;
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

    @Override
    public void onLoad(CoapResponse response) {
        System.out.println("I Heard THIS:");
        if (response.getPayload() != null) System.out.println(new String(response.getPayload()));
        linkHandler.getResponderLink().getWriter().writeResponse(Constants.extractPayload(response));
    }

    @Override
    public void onError() {
        System.err.println("Error");
    }
}
