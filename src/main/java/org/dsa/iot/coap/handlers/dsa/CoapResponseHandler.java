package org.dsa.iot.coap.handlers.dsa;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dsa.iot.coap.CoapLinkHandler;
import org.dsa.iot.coap.controllers.CoapServerController;
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

    CoapLinkHandler handleLink;


    public CoapResponseHandler(CoapLinkHandler handle) {
        handleLink = handle;
    }

    @Override
    public void handle(DataReceived event) {

        JsonArray array = event.getData();
        JsonObject json = null;
        for (Object object : array) {
            try {
                json = (JsonObject) object;
                //System.out.println("HANDELED RESPONSE:" + json); //DEBUG
                if (!handleLink.handleRemoteDSAMessage(json))
                    handleLink.getRequesterLink().getRequester().parse(json);
            } catch (RuntimeException e) {
                if (json != null) {
                    LOG.error("Failed to parse json", json, e);
                } else {
                    LOG.error("Failed to parse json is Null", e);
                }
            }
        }
        handleLink.getRequesterLink().getWriter().writeAck(event.getMsgId());
    }
}