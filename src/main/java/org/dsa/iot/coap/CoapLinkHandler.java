package org.dsa.iot.coap;

import org.dsa.iot.coap.actions.CreateCoapClientAction;
import org.dsa.iot.coap.actions.CreateCoapServerAction;
import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.DSLinkHandler;
import org.dsa.iot.dslink.connection.DataHandler;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoapLinkHandler extends DSLinkHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CoapLinkHandler.class);

    private Node rootNode;
    private DSLink requesterLink;
    private DSLink responderLink;

    private CoapRequestHandler requestHandler;

    private boolean isRequesterInited = false;

    @Override
    public void onResponderInitialized(DSLink link) {
        super.onResponderInitialized(link);
        responderLink = link;

        rootNode = initRootNode(link);

        requestHandler = new CoapRequestHandler(responderLink);
        //responderLink.getWriter().setReqHandler(requestHandler);

        if (rootNode.getChildren() == null) {
            return;
        }

        for (Node node : rootNode.getChildren().values()) {
            if (node.getConfig("client") != null && node.getConfig("client").getBool()) {
                setupCoapClient(node);
            }

            if (node.getConfig("server") != null && node.getConfig("server").getBool()) {
                setupCoapServer(node);
            }
        }
    }

    private Node initRootNode(DSLink link) {
        Node rootNode = link.getNodeManager().getSuperRoot();

        if (!rootNode.hasChild("createCoapClient", false)) {
            rootNode
                    .createChild("createCoapClient", false)
                    .setDisplayName("Create COAP Client")
                    .setSerializable(false)
                    .setAction(
                            new Action(Permission.WRITE, new CreateCoapClientAction())
                                    .addParameter(new Parameter("name", ValueType.STRING))
                                    .addParameter(new Parameter("url", ValueType.STRING))
                    )
                    .build();
        }

        if (!rootNode.hasChild("createCoapServer", false)) {
            rootNode
                    .createChild("createCoapServer", false)
                    .setDisplayName("Create COAP Server")
                    .setSerializable(false)
                    .setAction(
                            new Action(Permission.WRITE, new CreateCoapServerAction())
                                    .addParameter(new Parameter("name", ValueType.STRING))
                                    .addParameter(new Parameter("port", ValueType.NUMBER))
                    )
                    .build();
        }

        return rootNode;
    }

    @Override
    public void onRequesterInitialized(DSLink link) {
        super.onRequesterInitialized(link);
        requesterLink = link;
        //requesterLink.getWriter().setRespHandler(new CoapResponseHandler(requesterLink));
        isRequesterInited = true;
    }

    @Override
    public void onRequesterConnected(DSLink link) {
        super.onRequesterConnected(link);
        if (!isRequesterInited) {
            onRequesterInitialized(link);
        }
    }

    @Override
    public boolean isResponder() {
        return true;
    }

    @Override
    public boolean isRequester() {
        return true;
    }

    public void setupCoapClient(Node node) {
        CoapClientController controller = new CoapClientController(node);
        requestHandler.addClient(controller);
        node.setMetaData(controller);

        try {
            controller.init();
        } catch (Exception e) {
            LOG.error("Failed to setup COAP client.", e);
            node.getParent().removeChild(node, false);
        }
    }

    public void setupCoapServer(Node node) {
        CoapServerController controller = new CoapServerController(node, this);
        node.setMetaData(controller);

        try {
            controller.init();
        } catch (Exception e) {
            LOG.error("Failed to setup COAP server.", e);
            node.getParent().removeChild(node, false);
        }
    }

    public DSLink getRequesterLink() {
        return requesterLink;
    }

    public DSLink getResponderLink() {
        return responderLink;
    }
}
