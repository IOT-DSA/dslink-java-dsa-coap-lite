package org.dsa.iot.coap;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;

import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;


public class ClientSideServer extends CoapServer {

    private Map<Integer, CoapResource> resourceRidMap = new HashMap<>();
    private Node homeNode;

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
    public ClientSideServer(Node homeNode) throws SocketException {
        this.homeNode = homeNode;
        add(new ClientMainResource());
    }

    public void addResource(int rid) {
        resourceRidMap.put(rid, new ClientListenerResource(Integer.toString(rid)));
        add(resourceRidMap.get(rid));
    }

    public void removeResource(int rid) {
        CoapResource res = resourceRidMap.remove(rid);
        if (res != null) remove(res);
    }

    class ClientListenerResource extends CoapResource {

        public ClientListenerResource(String ridStr) {
            // set resource identifier
            super(ridStr);
            // set display name
            getAttributes().setTitle(ridStr);
        }

        @Override
        public void handlePOST(final CoapExchange exchange) {
            System.out.println("Received POST: " + new String(exchange.getRequestPayload())); //DEBUG

            JsonObject responseJson = new JsonObject(new String(exchange.getRequestPayload()));

            byte[] bytes = new byte[0];
            try {
                bytes = responseJson.toString().getBytes("utf-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            exchange.respond(CoAP.ResponseCode.VALID, bytes);
        }
    }

    class ClientMainResource extends CoapResource {

        public ClientMainResource() {
            // set resource identifier
            super(homeNode.getName());
            // set display name
            getAttributes().setTitle(homeNode.getName());
        }

        @Override
        public void handlePOST(final CoapExchange exchange) {
            System.out.println("Received POST: " + new String(exchange.getRequestPayload())); //DEBUG

            JsonObject responseJson = new JsonObject(new String(exchange.getRequestPayload()));

            byte[] bytes = new byte[0];
            try {
                bytes = responseJson.toString().getBytes("utf-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            exchange.respond(CoAP.ResponseCode.VALID, bytes);
        }
    }
}
