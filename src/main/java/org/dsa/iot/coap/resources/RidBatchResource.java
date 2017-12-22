package org.dsa.iot.coap.resources;

import org.dsa.iot.coap.Constants;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author James (Juris) Puchin
 * Created on 12/20/2017
 */
public class RidBatchResource extends CoapResource implements UpdateResourceInterface {

    private final DSACoapServer homeServer;
    private final int localRid;
    private final int remoteRid;

    private final AtomicBoolean first = new AtomicBoolean(true);
    private final AtomicBoolean waiting = new AtomicBoolean(false);
    private JsonArray messageQue;

    private final AtomicInteger willToLive = new AtomicInteger(Constants.LIFE_TIME);

    private static String makeBlankAr() {
        JsonObject obj = new JsonObject();
        obj.put(Constants.BLANK_KEY, Constants.BLANK_VAL);
        JsonArray blank = new JsonArray();
        blank.add(obj);
        return blank.toString();
    }

    private void clearData() {
        synchronized (first) {
            messageQue = new JsonArray();
            waiting.set(false);
            first.set(true);
        }
    }

    RidBatchResource(DSACoapServer homeServer, int localRid, int remoteRid) {
        // set resource identifier
        super(Constants.RID_PREFIX + Integer.toString(localRid));
        this.localRid = localRid;
        this.homeServer = homeServer;
        this.remoteRid = remoteRid;

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
        return str != null && str.equals("closed");
    }

    private void selfDestruct() {
        homeServer.destroyRidResource(localRid);
        homeServer.retireRemoteRid(remoteRid);
    }

    @Override
    public void handleGET(CoapExchange exchange) {
        synchronized (first) {
            if (first.get()) {
                exchange.respond(makeBlankAr());
                first.set(false);
            } else {
                exchange.respond(messageQue.toString());
                //System.out.println("REPOOOOONSE:" + exchange.advanced().getResponse()); //DEBUG
                //System.out.println("RID" + remoteRid + "QUE SENDING:" + messageQue); //DEBUG

                for (Object o : messageQue) {
                    if (goodDayToDie((JsonObject) o)) {
                        selfDestruct();
                        return;
                    }
                }
                messageQue = new JsonArray();
                waiting.set(false);
            }
        }
    }

    public void postDSAUpdate(JsonObject json) {
        json.put("rid", remoteRid);
        //System.out.println("RID UPDATE ADDED TO QUE:" + json);

        synchronized (first) {
            messageQue.add(json);
            if (!waiting.get()) changed();
            waiting.set(true);
        }

        //Prevent zombie apocalypse by making zombies sefDestruct
        synchronized (willToLive) {
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
        }
    }
}
