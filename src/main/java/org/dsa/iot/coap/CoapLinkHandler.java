package org.dsa.iot.coap;

import org.dsa.iot.coap.actions.CreateCoapClientAction;
import org.dsa.iot.coap.actions.CreateCoapServerAction;
import org.dsa.iot.coap.controllers.CoapClientController;
import org.dsa.iot.coap.controllers.CoapServerController;
import org.dsa.iot.coap.handlers.dsa.CoapRequestHandler;
import org.dsa.iot.coap.handlers.dsa.CoapResponseHandler;
import org.dsa.iot.coap.resources.DSACoapServer;
import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.DSLinkHandler;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CoapLinkHandler extends DSLinkHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CoapLinkHandler.class);

    private Node rootNode;
    private DSLink requesterLink;
    private DSLink responderLink;
    private final Set<Integer> usedRids = new HashSet<>();


    private int lastRid = 0;
    private CoapRequestHandler requestHandler;
    private boolean isRequesterInited = false;

    private Map<Integer, DSACoapServer> ridsToControllers = new ConcurrentHashMap<>();
    
    public boolean handleRemoteDSAMessage(JsonObject json) {
        System.out.println("Cuaght REMOTE: \n" + json);
        Integer rid = json.get("rid");
        if (rid == null) return false;
        DSACoapServer server = ridsToControllers.get(rid);
        if (server == null) return false;
        server.sendRemoteResponse(json);
        return true;
    }

    public void registerNewRid(int localRid, DSACoapServer server) {
        ridsToControllers.put(localRid, server);
    }

    public void retireLocalRid(int localRid) {
        synchronized (usedRids) {
            usedRids.remove(localRid);
        }
    }
    
    @Override
    public void onResponderInitialized(DSLink link) {
        super.onResponderInitialized(link);
        responderLink = link;

        rootNode = initRootNode(link);

        requestHandler = new CoapRequestHandler(this, rootNode);
        responderLink.getWriter().setReqHandler(requestHandler);

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
        requesterLink.getWriter().setRespHandler(new CoapResponseHandler(this));
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
        node.setMetaData(controller);

        try {
            controller.init();
        } catch (Exception e) {
            LOG.error("Failed to setup COAP client.", e);
            node.getParent().removeChild(node, false);
        }
    }

    public void setupCoapServer(Node node) {
        CoapServerController controller = new CoapServerController(node);
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

    public int genLocalRid() {
        int nextRid;
        synchronized (usedRids) {
            nextRid = lastRid + 1;
            while (usedRids.contains(nextRid)) { if (nextRid++ < 0) nextRid = 0;}
            lastRid = nextRid;
        }
        return nextRid;
    }
}
