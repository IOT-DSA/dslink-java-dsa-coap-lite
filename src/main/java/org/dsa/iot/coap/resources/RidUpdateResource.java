package org.dsa.iot.coap.resources;

import org.dsa.iot.coap.Constants;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;

/**
 * @author James (Juris) Puchin
 * Created on 12/10/2017
 */
public class RidUpdateResource extends CoapResource {

    private int localRid;
    private int remoteRid;
    private JsonObject latest;

    private void clearData() {
        latest = new JsonObject();
        latest.put(Constants.BLANK_KEY, Constants.BLANK_VAL);
    }

    RidUpdateResource(int localRid, int remoteRid) {

        // set resource identifier
        super(Constants.RID_PREFIX + Integer.toString(localRid));
        this.localRid = localRid;
        this.remoteRid = remoteRid;
        clearData();

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
