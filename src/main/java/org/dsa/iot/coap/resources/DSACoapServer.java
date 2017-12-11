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
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.server.resources.CoapExchange;

import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;


public class DSACoapServer extends CoapServer {

    private Node homeNode;
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
     * Constructor for a new Hello-World server. Here, the resources
     * of the server are initialized.
     */
    public DSACoapServer(Node homeNode) throws SocketException {
        this.homeNode = homeNode;
        // provide an instance of a Hello-World resource
        add(new HelloWorldResource());
    }

    /*
     * Definition of the Hello-World Resource
     */
    class HelloWorldResource extends CoapResource {

        public HelloWorldResource() {

            // set resource identifier
            super("helloWorld");

            // set display name
            getAttributes().setTitle("Hello-World Resource");
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

            JsonObject responseJson = new JsonObject(new String(exchange.getRequestPayload()));

            CoapLinkHandler linkHand = ((CoapLinkHandler) homeNode.getLink().getHandler());
            int thisRid = linkHand.generateNewRid();
            int remoteRid = responseJson.get("rid");

            RidUpdateResource ridRes = new RidUpdateResource(thisRid);

            localToRemoteRidHash.put(thisRid, remoteRid);
            pendingResponseesHash.put(thisRid, exchange);
            openRidsHash.put(thisRid, ridRes);

            responseJson.put("rid", thisRid);
            linkHand.getRequesterLink().getWriter().writeRequest(responseJson, false);

            byte[] bytes = new byte[0];
            try {
                bytes = responseJson.toString().getBytes("utf-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

//            		CoapClient client = this.createClient(exchange.getRequestText());
//		CoapObserveRelation relation;
//		relation = client.observe(handler);
//		synchronized(ridRes.relationStorage) {
//			relationStorage.put(new InetSocketAddress(exchange.getSourceAddress(), exchange.getSourcePort()), relation);
//		}
//
//		Response response = new Response(ResponseCode.VALID);
//		exchange.respond(response);

            exchange.respond(CoAP.ResponseCode.CREATED, bytes);
        }
    }
}