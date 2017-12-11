package org.dsa.iot.coap.resources;

import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;

import static org.eclipse.californium.core.coap.CoAP.ResponseCode.CHANGED;

/**
 * @author James (Juris) Puchin
 * Created on 12/10/2017
 */
public class RidUpdateResource extends CoapResource {

    int rid;
    JsonObject latest;

    public RidUpdateResource(int rid) {
        // set resource identifier
        super("rid_" + Integer.toString(rid));
        this.rid = rid;

        //TODO: verify these settings
        setObservable(true);
        setObserveType(CoAP.Type.CON);
        getAttributes().setObservable();

        // set display name
        getAttributes().setTitle("rid_" + Integer.toString(rid));
    }

    void putLatestUpdate(JsonObject update) {
        latest = update;
        changed();
    }

    @Override
    public void handlePUT(CoapExchange exchange) {
        // ...
        exchange.respond(CHANGED);
        changed(); // notify all observers
    }

}
