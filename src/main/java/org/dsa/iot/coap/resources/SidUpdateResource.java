package org.dsa.iot.coap.resources;

import org.dsa.iot.coap.Constants;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author James (Juris) Puchin
 * Created on 12/15/2017
 */
public class SidUpdateResource extends CoapResource implements UpdateResourceInterface {

    private final int REFRESH_RATE = 1000;
    private long lastRefresh;
    JsonObject latest;
    Map<Integer, Object> currentState;

    private void clearData() {
        latest = new JsonObject();
        currentState = new ConcurrentHashMap<>();
        latest.put(Constants.BLANK_KEY, Constants.BLANK_VAL);
    }

    SidUpdateResource(int localRid) {
        // set resource identifier
        super(Constants.RID_PREFIX + Integer.toString(localRid));

        lastRefresh = System.currentTimeMillis();

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
        long cur = System.currentTimeMillis();
        if (cur - lastRefresh > REFRESH_RATE) {
            latest = Constants.createSidUpd(currentState);
            lastRefresh = cur;
        }
        exchange.respond(latest.toString());
        //System.out.println("I AM SENDING THIS:" + latest); //DEBUG
    }

    public void postDSAUpdate(JsonObject json) {
        json.put("rid", 0);
        latest = json;

        JsonArray updates = json.get("updates");

        for (Object up : updates) {
            int sid = Constants.getSid(up);
            currentState.put(sid, up);
        }

        changed();
    }
}
