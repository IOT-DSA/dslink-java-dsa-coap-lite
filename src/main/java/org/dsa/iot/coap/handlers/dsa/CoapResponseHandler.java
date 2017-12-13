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
        for (Object object : array) {
            try {
                JsonObject json = (JsonObject) object;
                if (!handleLink.handleRemoteDSAMessage(json))
                    handleLink.getRequesterLink().getRequester().parse(json);
            } catch (RuntimeException e) {
                LOG.error("Failed to parse json", e);
            }
        }
        handleLink.getRequesterLink().getWriter().writeAck(event.getMsgId());
    }
}