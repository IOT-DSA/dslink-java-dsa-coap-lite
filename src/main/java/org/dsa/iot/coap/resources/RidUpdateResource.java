package org.dsa.iot.coap.resources;

import org.dsa.iot.coap.Constants;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author James (Juris) Puchin
 * Created on 12/10/2017
 */
public class RidUpdateResource extends CoapResource implements UpdateResourceInterface {

    private final DSACoapServer homeServer;
    private final int localRid;
    private final int remoteRid;

    private boolean lossless;
    private final AtomicBoolean waiting;
    private Queue<JsonObject> messageQue;

    private JsonObject latest;
    private AtomicInteger willToLive = new AtomicInteger(Constants.LIFE_TIME);

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
        synchronized (waiting) {
            exchange.respond(latest.toString());
            //System.out.println("REPOOOOONSE:" + exchange.advanced().getResponse()); //DEBUG
            //System.out.println("RID UPDATE SENDING:" + latest); //DEBUG

            if (goodDayToDie(latest)) {
                selfDestruct();
                return;
            }

            if (lossless) {
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
        //System.out.println("RID UPDATE HEARD:" + json); //DEBUG


        if (willToLive.decrementAndGet() % 100 == 0) {
            if (getObserverCount() < 1) {
                if (willToLive.get() < 0) {
                    homeServer.sendToLocalBroker(localRid, Constants.makeCloseReponse(localRid));
                    selfDestruct();
                }
            } else {
                willToLive.set(Constants.LIFE_TIME);
            }
        }


        if (lossless) {
            synchronized (waiting) {
                messageQue.add(json);
                if (!waiting.get()) {
                    latest = messageQue.poll();
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
