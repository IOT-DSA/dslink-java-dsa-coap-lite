package org.dsa.iot.coap;

import org.dsa.iot.coap.actions.CreateCoapClientAction;
import org.dsa.iot.coap.actions.CreateCoapServerAction;
import org.dsa.iot.coap.controllers.CoapClientController;
import org.dsa.iot.coap.controllers.CoapServerController;
import org.dsa.iot.coap.handlers.dsa.CoapRequestHandler;
import org.dsa.iot.coap.handlers.dsa.CoapResponseHandler;
import org.dsa.iot.coap.resources.RidUpdateResource;
import org.dsa.iot.coap.resources.UpdateResourceInterface;
import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.DSLinkHandler;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.dsa.iot.shared.SharedObjects;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CoapLinkHandler extends DSLinkHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CoapLinkHandler.class);

    private Node rootNode;
    private DSLink requesterLink;
    private DSLink responderLink;
    private final Set<Integer> usedIds = new HashSet<>();
    private static ScheduledThreadPoolExecutor executor;


    private int lastId = 0;
    private CoapRequestHandler requestHandler;
    private boolean isRequesterInited = false;

    private Map<Integer, CoapResource> ridsToResources = new ConcurrentHashMap<>();
    private Map<Integer, CoapResource> sidsToResources = new ConcurrentHashMap<>();
    private Map<Integer, Integer> localToRemoteSid = new ConcurrentHashMap<>();

    public ScheduledThreadPoolExecutor getExecutor() {
        if (executor == null) {
            executor = SharedObjects.createDaemonThreadPool(8);
            executor.setMaximumPoolSize(128);
            executor.setKeepAliveTime(2, TimeUnit.MINUTES);
        }
        return executor;
    }

    public boolean handleRemoteDSAMessage(JsonObject json) {
        Integer rid = json.get("rid");
        if (rid == null) return false;
        else if (rid == 0) {
            return handleSubscriptionUpdate(json);
        }
        CoapResource res = ridsToResources.get(rid);
        if (res == null) return false;
        //System.out.println("Cought REMOTE: \n" + json); //DEBUG
        ((UpdateResourceInterface) res).postDSAUpdate(json);
        return true;
    }

    public void add0Observer(CoapObserveRelation obs) {
        requestHandler.add0Observer(obs);
    }

    private boolean handleSubscriptionUpdate(JsonObject json) {
        //TODO: check all sids, combine remote into batches, fix sids to match remote, send each batch to right resource
        //TODO: pass all locals to be handled locally
        JsonArray updates = json.get("updates");
        Map <CoapResource, JsonArray> resMap = new HashMap<>();
        for (Object update : updates) {
            int sid = Constants.getAndReplaceSid(update, localToRemoteSid);
            CoapResource res = sidsToResources.get(sid);
            if (res != null) {
                if (resMap.containsKey(res)) resMap.get(res).add(update);
                else resMap.put(res, new JsonArray().add(update));
            }
        }

        for (Map.Entry<CoapResource,JsonArray> ent : resMap.entrySet()) {
            ((UpdateResourceInterface) ent.getKey()).postDSAUpdate(Constants.createSidUpd(ent.getValue()));
        }

        return false;
    }

    public int genLocalId() {
        int nextId;
        synchronized (usedIds) {
            nextId = lastId + 1;
            while (usedIds.contains(nextId)) { if (++nextId < 0) nextId = 1;}
            lastId = nextId;
        }
        return nextId;
    }

    public void registerNewRid(int localRid, CoapResource res) {
        ridsToResources.put(localRid, res);
    }

    public void registerNewSid(int localSid, int remoteSid, CoapResource res) {
        sidsToResources.put(localSid, res);
        localToRemoteSid.put(localSid,remoteSid);
    }

    public void retireLocalId(int localId) {
        ridsToResources.remove(localId);
        sidsToResources.remove(localId);
        localToRemoteSid.remove(localId);
        synchronized (usedIds) {
            usedIds.remove(localId);
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
        CoapClientController controller = new CoapClientController(node, this);
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
}
