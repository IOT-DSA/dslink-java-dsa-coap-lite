package org.dsa.iot.coap.resources;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.server.resources.CoapExchange;

/**
 * @author James (Juris) Puchin
 * Created on 12/5/2017
 */
public class DSACoapResource extends CoapResource {
    public DSACoapResource(String name) {
        super(name);
    }

    @Override
    public void handleGET(CoapExchange exchange) {
        super.handleGET(exchange);

    }
}
