package org.dsa.iot.coap.resources;

import org.dsa.iot.coap.Constants;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author James (Juris) Puchin
 * Created on 12/10/2017
 */
public class RidUpdateResource extends CoapResource implements UpdateResourceInterface {

    private final DSACoapServer homeServer;
    private final int localRid;
    private Queue<JsonObject> messageQue;
    private boolean lossless;
    private final AtomicBoolean waiting;
    private int remoteRid;
    private JsonObject latest;

    private void clearData() {
        latest = new JsonObject();
        latest.put(Constants.BLANK_KEY, Constants.BLANK_VAL);
        if (lossless) {
            messageQue = new ArrayDeque<>();
            waiting.set(false);
        }
    }

    RidUpdateResource(DSACoapServer homeServer, int localRid, int remoteRid, boolean lossless) {
        // set resource identifier
        super(Constants.RID_PREFIX + Integer.toString(localRid));
        this.localRid = localRid;
        this.homeServer = homeServer;
        this.remoteRid = remoteRid;
        this.lossless = lossless;

        waiting = new AtomicBoolean(false);

        clearData();

        //TODO: verify these settings
        setObservable(true);
        setObserveType(CoAP.Type.CON);
        getAttributes().setObservable();

        // set display name
        getAttributes().setTitle(Constants.RID_PREFIX + Integer.toString(localRid));
    }

    private boolean goodDayToDie(JsonObject json) {
        String str = json.get("stream");
        if (str != null && str.equals("closed")) {
            selfDestruct();
            return true;
        }
        return false;
    }

    private void selfDestruct() {
        homeServer.destroyRidResource(localRid);
        homeServer.retireRemoteRid(remoteRid);
    }

    @Override
    public void handleGET(CoapExchange exchange) {
        exchange.respond(latest.toString());
        System.out.println("I AM SENDING THIS:" + latest); //DEBUG

        if (goodDayToDie(latest)) return;

        if (lossless) {
            synchronized (waiting) {
                if (messageQue.isEmpty()) {
                    waiting.set(false);
                } else {
                    latest = messageQue.poll();
                    changed();
                }
            }
        }
    }

    public void postDSAUpdate(JsonObject json) {
        json.put("rid", remoteRid);

        if (lossless) {
            synchronized (waiting) {
                messageQue.add(json);
                if (!waiting.get()) {
                    waiting.set(true);
                    changed();
                }
            }
        } else {
            latest = json;
            changed();
        }
    }
}
