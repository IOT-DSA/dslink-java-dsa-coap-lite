package org.dsa.iot.coap.lite.actions;

import org.dsa.iot.coap.lite.CoapLinkHandler;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeManager;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.util.handler.Handler;

public class CreateCoapClientAction implements Handler<ActionResult> {
    @Override
    public void handle(ActionResult event) {
        String name = Node.checkAndEncodeName(event.getParameter("name").getString());
        String url = event.getParameter("url").getString();
        NodeManager nodeManager = event.getNode().getLink().getDSLink().getNodeManager();

        Node node = nodeManager
                .createRootNode(name)
                .setDisplayName(name)
                .setConfig("client", new Value(true))
                .setConfig("coap_url", new Value(url))
                .setSerializable(true)
                .build();

        CoapLinkHandler handler = (CoapLinkHandler) node.getLink().getHandler();
        handler.setupCoapClient(node);
    }
}
