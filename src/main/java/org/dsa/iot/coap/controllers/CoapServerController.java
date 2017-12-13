package org.dsa.iot.coap.controllers;

import org.dsa.iot.coap.resources.DSACoapServer;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonObject;

import java.net.SocketException;

public class CoapServerController {
    private Node node;
    private DSACoapServer server;
    private int port;

    public CoapServerController(Node node) {
        this.node = node;
    }

    private void initDefaultNodes() {
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
    }

    public void init() {
        initDefaultNodes();
        setStatus("Server Setup");

        try {
            port = node.getConfig("coap_port").getNumber().intValue();
            server = new DSACoapServer(node);
            server.addEndpoints(port);
            server.start();
        } catch (SocketException e) {
            System.err.println("Failed to initialize server: " + e.getMessage());
        }

        setStatus("Server Started");
    }

    public void setStatus(String name) {
        Node statusNode = node.getChild("status", false);
        if (statusNode != null) {
            statusNode.setValue(new Value(name));
        }
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
