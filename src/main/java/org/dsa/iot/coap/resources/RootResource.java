package org.dsa.iot.coap.resources;

import org.dsa.iot.coap.Constants;
import org.dsa.iot.dslink.util.json.EncodingFormat;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;

public class RootResource extends CoapResource {
    public RootResource() {
        super(Constants.CONN);
    }

    @Override
    public void handleGET(CoapExchange exchange) {
        JsonObject object = new JsonObject();
        object.put("dsa", "1.0.0");
        exchange.respond(CoAP.ResponseCode.VALID, object.encode(EncodingFormat.MESSAGE_PACK));
    }
}
