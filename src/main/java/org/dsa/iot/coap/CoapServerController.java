package org.dsa.iot.coap;

import org.dsa.iot.coap.resources.NodeResource;
import org.dsa.iot.coap.resources.RootResource;
import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.util.handler.Handler;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class CoapServerController {
    private Node node;
    public CoapServer server;
    private int port;
    private CoapHandler handler;

    public CoapServerController(Node node, CoapHandler handler) {
        this.node = node;
        this.handler = handler;
    }

    public void init() {
        if (!node.hasChild("_@remove")) {
            node
                    .createChild("_@remove")
                    .setDisplayName("Remove")
                    .setSerializable(false)
                    .setAction(new Action(Permission.WRITE, new DeleteCoapClientAction()))
                    .build();
        }

        port = node.getConfig("coap_port").getNumber().intValue();
        server = new CoapServer();
        addEndpoints();

        server.add(new RootResource());

        NodeResource rootNode = new NodeResource(this, "/");
        server.add(rootNode);

        server.start();
    }

    private void addEndpoints() {
        for (InetAddress addr : EndpointManager.getEndpointManager().getNetworkInterfaces()) {
            // only binds to IPv4 addresses and localhost
            if (addr instanceof Inet4Address || addr.isLoopbackAddress()) {
                InetSocketAddress bindToAddress = new InetSocketAddress(addr, port);
                server.addEndpoint(new CoapEndpoint(bindToAddress));
            }
        }
    }

    public DSLink getRequesterLink() {
        return handler.getRequesterLink();
    }

    public DSLink getResponderLink() {
        return handler.getResponderLink();
    }

    public class DeleteCoapClientAction implements Handler<ActionResult> {
        @Override
        public void handle(ActionResult event) {
            if (server != null) {
                server.stop();
            }

            node.delete();
        }
    }
}
