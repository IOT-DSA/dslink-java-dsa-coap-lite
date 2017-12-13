/*******************************************************************************
 * Copyright (c) 2015 Institute for Pervasive Computing, ETH Zurich and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Kai Hudalla (Bosch Software Innovations GmbH) - add endpoints for all IP addresses
 ******************************************************************************/
package org.dsa.iot.coap.resources;

import org.dsa.iot.coap.CoapLinkHandler;
import org.dsa.iot.coap.Constants;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.server.resources.CoapExchange;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;


public class DSACoapServer extends CoapServer {

    private CoapLinkHandler coapLinkHandler;
    private Map<Integer, Integer> localToRemoteRidHash = new HashMap<>();
    private Map<Integer, CoapExchange> pendingResponseesHash = new HashMap<>();
    private Map<Integer, RidUpdateResource> openRidsHash = new HashMap<>();
    private Map<Integer, String> remoteResourceHash = new HashMap<>();

    /**
     * Add individual endpoints listening on default CoAP port on all IPv4 addresses of all network interfaces.
     */
    public void addEndpoints(int port) {
        for (InetAddress addr : EndpointManager.getEndpointManager().getNetworkInterfaces()) {
            // only binds to IPv4 addresses and localhost
            if (addr instanceof Inet4Address || addr.isLoopbackAddress()) {
                InetSocketAddress bindToAddress = new InetSocketAddress(addr, port);
                addEndpoint(new CoapEndpoint(bindToAddress));
            }
        }
    }

    /*
     * Constructor for a new gateway server. It's job is to handle new incoming
     * requests and to create new resources for streaming requests.
     */
    public DSACoapServer(Node homeNode) throws SocketException {
        coapLinkHandler = ((CoapLinkHandler) homeNode.getLink().getHandler());
        // provide an instance of a Hello-World resource
        add(new GatewayResource(this));
    }

    public void sendRemoteResponse(JsonObject json) {
        RidUpdateResource res = openRidsHash.get(json.get("rid"));
        res.postDSAUpdate(json);
    }

    private void createNewRidResource(int localRid, int remoteRid, CoapExchange pending) {

        RidUpdateResource ridRes = new RidUpdateResource(localRid, remoteRid);

        localToRemoteRidHash.put(localRid, remoteRid);
        pendingResponseesHash.put(localRid, pending);
        openRidsHash.put(localRid, ridRes);
        coapLinkHandler.registerNewRid(localRid, this);
        add(ridRes);
    }

    private void sendToLocalBroker(int rid, JsonObject json) {
        json.put("rid", rid);
        coapLinkHandler.getRequesterLink().getWriter().writeRequest(json, false);
    }

    private void replyToRemoteBroker(CoapExchange exchange, JsonObject response) {
        exchange.respond(CoAP.ResponseCode.VALID, Constants.jsonToBytes(response));
    }

    private void replyWithNewResource(CoapExchange exchange, int newRid) {
        JsonObject response = new JsonObject();
        response.put(Constants.REMOTE_RID_FIELD, Constants.RID_PREFIX + newRid);
        exchange.respond(CoAP.ResponseCode.CREATED, Constants.jsonToBytes(response));
    }

    /*
     * Resource used to process requests on the main gateway.
     */
    class GatewayResource extends CoapResource {

        DSACoapServer homeServer;

        public GatewayResource(DSACoapServer server) {

            // set resource identifier
            super(Constants.MAIN_SERVER_NAME);
            setObservable(true);
            setObserveType(CoAP.Type.CON);
            getAttributes().setObservable();

            // set display name
            getAttributes().setTitle(Constants.MAIN_SERVER_NAME);

            homeServer = server;
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            System.out.println("Received GET");
//            JsonObject obj = new JsonObject(homeNode.);
//            System.out.println("handleGET: " + obj); //DEBUG
//            byte[] encoded = obj.encode(EncodingFormat.MESSAGE_PACK);
//            exchange.respond(CoAP.ResponseCode.VALID, encoded);

            // respond to the request
            exchange.respond("Hello World!");
        }

        @Override
        public void handlePOST(final CoapExchange exchange) {
            System.out.println("Received POST: " + new String(exchange.getRequestPayload())); //DEBUG

            JsonObject responseJson = Constants.extractPayload(exchange);

            int remoteRid = responseJson.get("rid");
            int thisRid = coapLinkHandler.generateNewRid();

            String method = responseJson.get("method");
            switch (method) {
                case "set":
                    homeServer.sendToLocalBroker(thisRid, responseJson);
                    responseJson = Constants.makeCloseReponse(remoteRid);
                    homeServer.replyToRemoteBroker(exchange,responseJson);
                    break;
                case "remove":
                    //TODO: doRemove(); is the dslink reponsible for keeping track of defunct rids?
                    homeServer.sendToLocalBroker(thisRid, responseJson);
                    responseJson = Constants.makeCloseReponse(remoteRid);
                    homeServer.replyToRemoteBroker(exchange,responseJson);
                    break;
                case "invoke":
                    //Meaningful response
                case "list":
                    //Need to create update servers
                    homeServer.createNewRidResource(thisRid, remoteRid, exchange);
                    homeServer.sendToLocalBroker(thisRid, responseJson);
                    homeServer.replyWithNewResource(exchange,thisRid);
                    break;
                case "subscribe":
                case "unsubscribe":
                    //Need to close update servers
                case "close":
                    //Need to close update servers
                default:
                    homeServer.replyToRemoteBroker(exchange,responseJson);
            }
        }
    }
}