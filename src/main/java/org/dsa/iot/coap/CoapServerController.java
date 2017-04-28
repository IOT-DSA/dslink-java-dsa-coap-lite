package org.dsa.iot.coap;

import org.dsa.iot.coap.resources.NodeResource;
import org.dsa.iot.coap.resources.RootResource;
import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;

import java.net.Inet4Address;
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
        if (!node.hasChild("remove", false)) {
            node
                    .createChild("remove", false)
                    .setDisplayName("Remove")
                    .setSerializable(false)
                    .setAction(new Action(Permission.WRITE, new DeleteCoapClientAction()))
                    .build();
        }

        if (!node.hasChild("status", false)) {
            node
                    .createChild("status", false)
                    .setDisplayName("Status")
                    .setSerializable(false)
                    .setValueType(ValueType.STRING)
                    .build();
        }

        stat("Server Setup");

        port = node.getConfig("coap_port").getNumber().intValue();
        server = new CoapServer();
        addEndpoints();

        server.add(new RootResource());

        NodeResource rootNode = new NodeResource(this, "/");
        server.add(rootNode);

        server.start();
        stat("Server Started");
    }

    public void stat(String name) {
        Node statusNode = node.getChild("status", false);
        if (statusNode != null) {
            statusNode.setValue(new Value(name));
        }
    }

    private void addEndpoints() {
        EndpointManager.getEndpointManager().getNetworkInterfaces().stream()
                .filter(addr -> addr instanceof Inet4Address || addr.isLoopbackAddress())
                .forEach(addr -> {
            InetSocketAddress bindToAddress = new InetSocketAddress(addr, port);
            server.addEndpoint(new CoapEndpoint(bindToAddress));
        });
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

            node.delete(false);
        }
    }
}
