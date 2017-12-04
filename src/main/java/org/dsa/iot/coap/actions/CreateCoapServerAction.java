package org.dsa.iot.coap.actions;

import org.dsa.iot.coap.CoapLinkHandler;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeManager;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.util.handler.Handler;

public class CreateCoapServerAction implements Handler<ActionResult> {
    @Override
    public void handle(ActionResult event) {
        String name = Node.checkAndEncodeName(event.getParameter("name").getString());
        int port = event.getParameter("port").getNumber().intValue();
        NodeManager nodeManager = event.getNode().getLink().getDSLink().getNodeManager();

        Node node = nodeManager
                .createRootNode(name)
                .setDisplayName(name)
                .setConfig("server", new Value(true))
                .setConfig("coap_port", new Value(port))
                .setSerializable(true)
                .build();

        CoapLinkHandler handler = (CoapLinkHandler) node.getLink().getHandler();
        handler.setupCoapServer(node);
    }
}
