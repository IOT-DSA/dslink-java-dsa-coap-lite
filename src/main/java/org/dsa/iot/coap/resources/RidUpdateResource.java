package org.dsa.iot.coap.resources;

import org.dsa.iot.coap.Constants;
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

    int localRid;
    int remoteRid;
    JsonObject latest;

    RidUpdateResource(int localRid, int remoteRid) {

        // set resource identifier
        super(Constants.RID_PREFIX + Integer.toString(localRid));
        this.localRid = localRid;
        this.remoteRid = remoteRid;

        //TODO: verify these settings
        setObservable(true);
        setObserveType(CoAP.Type.CON);
        getAttributes().setObservable();

        // set display name
        getAttributes().setTitle(Constants.RID_PREFIX + Integer.toString(localRid));
    }

    @Override
    public void handleGET(CoapExchange exchange) {
        exchange.respond(latest.toString());
    }

    public void postDSAUpdate(JsonObject json) {
        json.put("rid", remoteRid);
        latest = json;
        changed();
    }
}
