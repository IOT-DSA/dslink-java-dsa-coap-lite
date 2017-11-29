package org.dsa.iot.coap;

import org.dsa.iot.coap.actions.CreateCoapClientAction;
import org.dsa.iot.coap.actions.CreateCoapServerAction;
import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.DSLinkHandler;
import org.dsa.iot.dslink.connection.DataHandler;
import org.dsa.iot.dslink.methods.StreamState;
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class CoapHandler extends DSLinkHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CoapHandler.class);

    private Node rootNode;
    private DSLink requesterLink;
    private DSLink responderLink;
    private HashSet<String>pathHash = new HashSet<>();

    private void tryToCreatePath(String path) {
        pathHash.add(path);
        Node thisNode = rootNode;
        boolean superRoot = true;
        for (String n : path.split("/")) {
            if (n.equals("")) continue;
            if (!superRoot && !thisNode.hasChild(n, false)) {
                Object contrl =  thisNode.getMetaData();
                if (contrl instanceof CoapClientController) {
                    ((CoapClientController) contrl).waitForConnection();
                } else if (contrl instanceof CoapNodeController) {
                    ((CoapNodeController) contrl).kindaList();
                }
            }
            if (thisNode.hasChild(n, false)) {
                thisNode = thisNode.getChild(n,false);
            } else {
                break;
            }
            superRoot = false;
        }
    }

    @Override
    public void onResponderInitialized(DSLink link) {
        super.onResponderInitialized(link);
        responderLink = link;

        rootNode = checkRootNode(link);

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
        //Custom request handler capable of building fake nodes if they are missing
        responderLink.getWriter().setReqHandler(new Handler<DataHandler.DataReceived>() {
            @Override
            public void handle(DataHandler.DataReceived event) {
                final JsonArray data = event.getData();
                List<JsonObject> responses = new LinkedList<>();
                for (Object object : data) {
                    JsonObject json = (JsonObject) object;
                    //Check if subscribe request is to a non-existent node, attempt to build
                    if (json.contains("method") && json.get("method").equals("subscribe")) {
                        JsonArray paths = json.get("paths");
                        for (Object path : paths) {
                            String p = ((JsonObject) path).get("path");
                            if (!pathHash.contains(p)) tryToCreatePath(p);
                        }
                    }

                    try {
                        JsonObject resp = responderLink.getResponder().parse(json);
                        responses.add(resp);
                    } catch (Exception e) {
                        JsonObject resp = new JsonObject();
                        Integer rid = json.get("rid");
                        if (rid != null) {
                            resp.put("rid", rid);
                        }
                        resp.put("stream", StreamState.CLOSED.getJsonName());

                        JsonObject err = new JsonObject();
                        err.put("msg", e.getMessage());
                        { // Build stack trace
                            StringWriter writer = new StringWriter();
                            e.printStackTrace(new PrintWriter(writer));
                            err.put("detail", writer.toString());
                        }
                        resp.put("error", err);
                        responses.add(resp);
                    }
                }

                Integer msgId = event.getMsgId();
                responderLink.getWriter().writeRequestResponses(msgId, responses);
            }
        });
    }

    public Node checkRootNode(DSLink link) {
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

        isRequesterInited = true;
    }

    private boolean isRequesterInited = false;

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
