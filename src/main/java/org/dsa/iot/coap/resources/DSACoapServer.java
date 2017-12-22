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
import org.dsa.iot.dslink.util.json.JsonArray;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class DSACoapServer extends CoapServer {

    private CoapLinkHandler coapLinkHandler;
    private CoapResource rid0Resource;
    private Map<Integer, CoapResource> openRidsHash = new ConcurrentHashMap<>();
    private Map<Integer, Integer> remoteToLocalSid = new ConcurrentHashMap<>();
    private Map<Integer, Integer> remoteToLocalRid = new ConcurrentHashMap<>();

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
        setExecutor(coapLinkHandler.getExecutor());
        // provide an instance of a Hello-World resource
        add(new GatewayResource(this));

        //Setup rid 0 for subscriptions
        remoteToLocalRid.put(0,0);
    }

    private void createRid0Res() {
        if (rid0Resource == null) {
            int rid0 = coapLinkHandler.genLocalId();
            //rid0Resource = new RidBatchResource(this, rid0, 0);
            rid0Resource = new RidUpdateResource(this, rid0, 0, true);
            //rid0Resource = new SidUpdateResource(rid0);
            add(rid0Resource);
        }
    }

    private void createNewRidResource(int localRid, int remoteRid) {
        //CoapResource ridRes = new RidUpdateResource(this, localRid, remoteRid, true);
        CoapResource ridRes = new RidUpdateResource(this, localRid, remoteRid, true);
        openRidsHash.put(localRid, ridRes);
        coapLinkHandler.registerNewRid(localRid, ridRes);
        add(ridRes);
    }

    public void destroyRidResource(int localRid) {
        CoapResource ridRes = openRidsHash.remove(localRid);
        if (ridRes != null) {
            remove(ridRes);
            ridRes.delete();
        }
    }

    private void localizeSubSids(JsonObject json) {
        createRid0Res();
        JsonArray paths = json.get("paths");
        if (paths != null) {
            for (Object obj : paths) {
                Integer remoteSid = ((JsonObject) obj).get("sid");
                int localSid = coapLinkHandler.genLocalId();
                ((JsonObject) obj).put("sid",localSid);
                coapLinkHandler.registerNewSid(localSid, remoteSid, rid0Resource);
                remoteToLocalSid.put(remoteSid,localSid);
                //System.out.println("Captured SID:" + sid); //DEBUG
            }
        } else {
            throw new RuntimeException("Subscribe request has not paths!");
        }
    }

    private void localizeUnsubSids(JsonObject json) {
        JsonArray remoteSids = json.get("sids");
        JsonArray localSids = new JsonArray();
        for (Object remSid : remoteSids) {
            Integer locSid = remoteToLocalSid.get(remSid);
            if (locSid != null) {
                localSids.add(locSid);
            }
        }
        json.put("sids", localSids);
    }

    public void sendToLocalBroker(int rid, JsonObject json) {
        json.put("rid", rid);
        coapLinkHandler.getRequesterLink().getWriter().writeRequest(json, false);
    }

    private void replyToRemoteBroker(CoapExchange exchange, JsonObject response) {
        exchange.respond(CoAP.ResponseCode.VALID, Constants.jsonToBytes(response));
    }

    private void replyDeletedToRemoteBroker(CoapExchange exchange) {
        exchange.respond(CoAP.ResponseCode.DELETED);
    }

    private void replyWithNewResource(CoapExchange exchange, int newRid) {
        JsonObject response = new JsonObject();
        response.put(Constants.REMOTE_RID_FIELD, Constants.RID_PREFIX + newRid);
        exchange.respond(CoAP.ResponseCode.CREATED, Constants.jsonToBytes(response));
    }

    public int genOrGetLocalRid(int remoteRid) {
        if (remoteToLocalRid.containsKey(remoteRid))
            return remoteToLocalRid.get(remoteRid);

        int nextRid = coapLinkHandler.genLocalId();
        remoteToLocalRid.put(remoteRid,nextRid);
        return nextRid;
    }

    public void retireRemoteRid(int remoteRid) {
        Integer localRid = remoteToLocalRid.remove(remoteRid);
        if (localRid != null) coapLinkHandler.retireLocalId(localRid);
    }

    public void retireRemoteSids(JsonArray remoteSids) {
        for (Object sid : remoteSids) {
            int remoteSid = (int) sid;
            Integer localSid = remoteToLocalSid.remove(remoteSid);
            if (localSid != null) coapLinkHandler.retireLocalId(localSid);
        }
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

        private boolean handleInternalComms(JsonObject json, CoapExchange ex) {
            String req = json.get(Constants.GIMME);
            if (req == null) return false;
            if (req.equals(Constants.RID_ZERO_HANDLE)) {
                if (rid0Resource == null) createRid0Res();
                String r0ID = rid0Resource.getName();
                json.put(Constants.GIMME, r0ID);
                homeServer.replyToRemoteBroker(ex,json);
                return true;
            }
            return false;
        }

        private void forwardAndClose(int thisRid, int remoteRid, JsonObject json, CoapExchange ex) {
            homeServer.sendToLocalBroker(thisRid, json);
            json = Constants.makeCloseReponse(remoteRid);
            homeServer.replyToRemoteBroker(ex,json);
            homeServer.retireRemoteRid(remoteRid);
        }

        @Override
        public void handlePOST(final CoapExchange exchange) {
            //System.out.println("Received POST: " + new String(exchange.getRequestPayload())); //DEBUG

            JsonObject json = Constants.extractPayloadObject(exchange);

            if (handleInternalComms(json, exchange)) return;

            int remoteRid = json.get("rid");
            int thisRid = homeServer.genOrGetLocalRid(remoteRid);

            String method = json.get("method");
            switch (method) {
                case "set":
                    forwardAndClose(thisRid, remoteRid, json, exchange);
                    break;
                case "remove":
                    //TODO: doRemove(); is the dslink reponsible for keeping track of defunct rids?
                    forwardAndClose(thisRid, remoteRid, json, exchange);
                    break;
                case "invoke":
                case "list":
                    //System.out.println("LIST/INVOKE RECEIVED:"+ json); //DEBUG
                    homeServer.createNewRidResource(thisRid, remoteRid);
                    homeServer.sendToLocalBroker(thisRid, json);
                    homeServer.replyWithNewResource(exchange,thisRid);
                    break;
                case "subscribe":
                    //System.out.println("SUBSCRIBE RECEIVED:"+ json); //DEBUG
                    homeServer.localizeSubSids(json);
                    forwardAndClose(thisRid, remoteRid, json, exchange);
                    //System.out.println("SUBSCRIBE FROWARDED:"+ json); //DEBUG
                    break;
                case "unsubscribe":
                    //Need to close update servers
                    //System.out.println("UNSUBSCRIBE RECEIVED:"+ json); //DEBUG
                    homeServer.localizeUnsubSids(json);
                    forwardAndClose(thisRid, remoteRid, json, exchange);
                    homeServer.retireRemoteSids(json.get("sids"));
                    break;
                case "close":
                    //Need to close update servers
                    homeServer.sendToLocalBroker(thisRid, json);
                    homeServer.destroyRidResource(thisRid);
                    homeServer.retireRemoteRid(remoteRid);
                    homeServer.replyDeletedToRemoteBroker(exchange);
                    break;
                default:
                    homeServer.replyToRemoteBroker(exchange,json);
            }
        }
    }
}