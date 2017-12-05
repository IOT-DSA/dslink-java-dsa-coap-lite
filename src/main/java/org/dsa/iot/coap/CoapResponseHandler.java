package org.dsa.iot.coap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.connection.DataHandler.DataReceived;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author James (Juris) Puchin
 * Created on 12/4/2017
 */
public class CoapResponseHandler implements Handler<DataReceived>{
    private static final Logger LOG = LoggerFactory.getLogger(CoapRequestHandler.class);

    DSLink link;
    Map<Integer, CoapServerController> ridsToControllers = new ConcurrentHashMap<Integer, CoapServerController>();

    CoapResponseHandler(DSLink link) {
        this.link = link;
    }

    @Override
    public void handle(DataReceived event) {

        JsonArray array = event.getData();
        for (Object object : array) {
            try {
                JsonObject json = (JsonObject) object;
                link.getRequester().parse(json);
            } catch (RuntimeException e) {
                LOG.error("Failed to parse json", e);
            }
        }
        link.getWriter().writeAck(event.getMsgId());
    }

}