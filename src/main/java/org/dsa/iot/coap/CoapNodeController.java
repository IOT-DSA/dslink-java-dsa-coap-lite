package org.dsa.iot.coap;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.dsa.iot.coap.utils.Tables;
import org.dsa.iot.coap.utils.Values;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.Writable;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.EditorType;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.actions.ResultType;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.node.value.ValueUtils;
import org.dsa.iot.dslink.util.json.EncodingFormat;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoapNodeController {

    private static final Logger LOG = LoggerFactory.getLogger(CoapNodeController.class);

    private CoapClientController controller;
    private int handles = 0;
    private boolean hasHandle = false;
    private Node node;
    private CoapClient client;
    private String coapPath;

    private boolean isInitialized = false;

    private CoapObserveRelation relation;

    private boolean hasEverLoaded = false;

    public CoapNodeController(CoapClientController controller, Node node, String coapPath) {
        this.controller = controller;
        this.node = node;
        this.coapPath = coapPath;

        node.setSerializable(false);
        node.setMetaData(this);
    }

    public void init() {
        if (isInitialized && client != null) {
            return;
        }

        isInitialized = true;

        client = controller.getClient(coapPath);

        node.getListener().setOnListHandler(event -> {
            handles++;
            checkHandles();
        });

        node.getListener().setOnListClosedHandler(event -> {
            handles--;
            checkHandles();
        });

        node.getListener().setOnSubscribeHandler(event -> {
            handles++;
            checkHandles();
        });

        node.getListener().setOnUnsubscribeHandler(event -> {
            handles--;
            checkHandles();
        });

        if (node.getLink().getSubscriptionManager().hasValueSub(node)) {
            handles++;
            checkHandles();
        }

        if (node.getLink().getSubscriptionManager().hasPathSub(node)) {
            handles++;
            checkHandles();
        }
    }

    public void checkHandles() {
        if (handles <= 0 && hasHandle) {
            closeHandles();
            handles = 0;
        } else if (!hasHandle) {
            startHandles();
        }
    }

    public void updateListData(JsonArray listArray) {
        for (Object o : listArray) {
            if (o instanceof JsonArray) {
                JsonArray m = (JsonArray) o;

                if (m.size() < 2) {
                    continue;
                }

                String key = m.get(0);
                Object mvalue;

                if (m.size() > 1) {
                    mvalue = m.get(1);
                } else {
                    mvalue = new JsonObject();
                }

                Value value = ValueUtils.toValue(mvalue);

                if (value == null) {
                    value = new Value((String) null);
                }

                //noinspection StatementWithEmptyBody
                if (key.equals("$is")) {
                    //node.setProfile(value.getString());
                } else if (key.equals("$type")) {
                    ValueType type = ValueType.toValueType(value.getString());
                    if (!type.equals(node.getValueType())) {
                        node.setValueType(type);
                    }
                } else if (key.equals("$name")) {
                    if (node.getDisplayName() == null || !node.getDisplayName()
                                                              .equals(value.getString())) {
                        node.setDisplayName(value.getString());
                    }
                } else if (key.equals("$invokable")) {
                    Permission perm = Permission.toEnum(value.getString());
                    Action act = getOrCreateAction(node, perm, false);
                    if (act.getPermission() == null || !act.getPermission().getJsonName()
                                                           .equals(perm.getJsonName())) {
                        act.setPermission(perm);
                    }
                } else if (key.equals("$columns")) {
                    if (mvalue instanceof JsonArray) {
                        JsonArray array = (JsonArray) mvalue;
                        Action act = getOrCreateAction(node, Permission.NONE, false);
                        iterateActionMetaData(act, array, true);
                    }
                } else if (key.equals("$writable")) {
                    String string = value.getString();
                    if (node.getWritable() == null || !node.getWritable().toJsonName()
                                                           .equals(string)) {
                        node.setWritable(Writable.toEnum(string));
                    }
                } else if (key.equals("$params")) {
                    if (mvalue instanceof JsonArray) {
                        JsonArray array = (JsonArray) mvalue;
                        Action act = getOrCreateAction(node, Permission.NONE, false);
                        iterateActionMetaData(act, array, false);
                    }
                } else if (key.equals("$hidden")) {
                    node.setHidden(value.getBool());
                } else if (key.equals("$result")) {
                    String string = value.getString();
                    Action act = getOrCreateAction(node, Permission.NONE, false);
                    if (act.getResultType() == null || !act.getResultType().getJsonName()
                                                           .equals(string)) {
                        act.setResultType(ResultType.toEnum(string));
                    }
                } else if (key.equals("$$password")) {
                    if (value.getString() != null) {
                        node.setPassword(value.getString().toCharArray());
                    } else {
                        node.setPassword(null);
                    }
                } else if (key.equals("$hasChildren")) {
                    if (value.getBool() != node.getHasChildren()) {
                        node.setHasChildren(value.getBool());
                    }
                } else if (key.startsWith("$$")) {
                    String cname = key.substring(2);
                    if (!Values.isEqual(value, node.getRoConfig(cname))) {
                        node.setRoConfig(cname, value);
                    }
                } else if (key.startsWith("$")) {
                    String cname = key.substring(1);
                    if (!Values.isEqual(value, node.getConfig(cname))) {
                        node.setConfig(cname, value);
                    }
                } else if (key.startsWith("@")) {
                    String cname = key.substring(1);
                    if (!Values.isEqual(value, node.getAttribute(cname))) {
                        node.setAttribute(cname, value);
                    }
                } else {
                    Node child = ((CoapFakeNode) node).getCachedChild(key, false);

                    if (child == null) {
                        NodeBuilder builder = node.createChild(key, false);
                        if (mvalue instanceof JsonObject) {
                            JsonObject co = (JsonObject) mvalue;
                            for (Map.Entry<String, Object> entry : co) {
                                applyCreatedAttribute(builder, entry.getKey(), entry.getValue());
                            }
                        }
                        builder.setSerializable(false);
                        child = builder.build();

                        CoapNodeController nodeController = new CoapNodeController(
                                controller,
                                child,
                                ((CoapFakeNode) child).getCoapPath()
                        );

                        nodeController.init();
                        node.addChild(child);
                    } else {
                        if (mvalue instanceof JsonObject) {
                            JsonObject co = (JsonObject) mvalue;
                            for (Map.Entry<String, Object> entry : co) {
                                applyAttribute(child, entry.getKey(), entry.getValue(), true);
                            }
                        }
                    }
                }
            } else if (o instanceof JsonObject) {
                JsonObject obj = (JsonObject) o;
                if ("remove".equals(obj.get("change"))) {
                    String key = obj.get("name");

                    if (key.equals("$hasChildren")) {
                        node.setHasChildren(false);
                    } else if (key.startsWith("$$")) {
                        node.removeRoConfig(key.substring(2));
                    } else if (key.startsWith("$")) {
                        node.removeConfig(key.substring(1));
                    } else if (key.startsWith("@")) {
                        node.removeAttribute(key.substring(1));
                    } else {
                        try {
                            node.removeChild(CustomURLEncoder.encode(key, "UTF-8"), false);
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        //NodeBuilders.applyMultiCoapChildBuilders((CoapFakeNode) node, childQueue);
    }

    public void applyCreatedAttribute(NodeBuilder n, String key, Object mvalue) {
        Value value = ValueUtils.toValue(mvalue);

        if (value == null) {
            value = new Value((String) null);
        }

        if (key.equals("$type")) {
            ValueType type = ValueType.toValueType(value.getString());
            n.setValueType(type);
        } else if (key.equals("$name")) {
            n.setDisplayName(value.getString());
        } else if (key.equals("$invokable")) {
            Permission perm = Permission.toEnum(value.getString());
            Action act = getOrCreateAction(n.getChild(), perm, true);
            act.setPermission(perm);
        } else if (key.equals("$columns")) {
            JsonArray array = (JsonArray) mvalue;
            Action act = getOrCreateAction(n.getChild(), Permission.NONE, true);
            iterateActionMetaData(act, array, true);
        } else if (key.equals("$hasChildren")) {
            n.setHasChildren(value.getBool());
        } else if (key.equals("$writable")) {
            String string = value.getString();
            n.setWritable(Writable.toEnum(string));
        } else if (key.equals("$params")) {
            JsonArray array = (JsonArray) mvalue;
            Action act = getOrCreateAction(n.getChild(), Permission.NONE, true);
            iterateActionMetaData(act, array, false);
        } else if (key.equals("$hidden")) {
            n.setHidden(value.getBool());
        } else if (key.equals("$result")) {
            String string = value.getString();
            Action act = getOrCreateAction(n.getChild(), Permission.NONE, true);
            act.setResultType(ResultType.toEnum(string));
        } else if (key.startsWith("$$")) {
            n.setRoConfig(key.substring(2), value);
        } else if (key.startsWith("$")) {
            if (!key.equals("$is")) {
                n.setConfig(key.substring(1), value);
            }
        } else if (key.startsWith("@")) {
            n.setAttribute(key.substring(1), value);
        }
    }

    public void applyAttribute(Node n, String key, Object mvalue, boolean isChild) {
        Value value = ValueUtils.toValue(mvalue);

        if (value == null) {
            value = new Value((String) null);
        }

        if (key.equals("$type")) {
            ValueType type = ValueType.toValueType(value.getString());
            if (!type.equals(n.getValueType())) {
                n.setValueType(type);
            }
        } else if (key.equals("$hasChildren")) {
            n.setHasChildren(value.getBool());
        } else if (key.equals("$password")) {
            if (value.getString() != null) {
                n.setPassword(value.getString().toCharArray());
            } else {
                n.setPassword(null);
            }
        } else if (key.equals("$name")) {
            if (n.getDisplayName() == null || !n.getDisplayName().equals(value.getString())) {
                n.setDisplayName(value.getString());
            }
        } else if (key.equals("$invokable")) {
            Permission perm = Permission.toEnum(value.getString());
            Action act = getOrCreateAction(n, perm, isChild);
            if (act.getPermission() == null || !act.getPermission().getJsonName()
                                                   .equals(perm.getJsonName())) {
                act.setPermission(perm);
            }
        } else if (key.equals("$columns")) {
            JsonArray array = (JsonArray) mvalue;
            Action act = getOrCreateAction(n, Permission.NONE, isChild);
            iterateActionMetaData(act, array, true);
        } else if (key.equals("$writable")) {
            String string = value.getString();
            n.setWritable(Writable.toEnum(string));
        } else if (key.equals("$params")) {
            JsonArray array = (JsonArray) mvalue;
            Action act = getOrCreateAction(n, Permission.NONE, isChild);
            iterateActionMetaData(act, array, false);
        } else if (key.equals("$hidden")) {
            n.setHidden(value.getBool());
        } else if (key.equals("$result")) {
            String string = value.getString();
            Action act = getOrCreateAction(n, Permission.NONE, isChild);
            act.setResultType(ResultType.toEnum(string));
        } else if (key.startsWith("$$")) {
            String cname = key.substring(2);
            Value lastValue = node.getRoConfig(cname);

            if (!Values.isEqual(value, lastValue)) {
                node.setRoConfig(cname, value);
            }
        } else if (key.startsWith("$")) {
            if (!key.equals("$is")) {
                String cname = key.substring(1);
                Value lastValue = node.getConfig(cname);

                if (!Values.isEqual(value, lastValue)) {
                    node.setConfig(cname, value);
                }
            }
        } else if (key.startsWith("@")) {
            String cname = key.substring(1);
            Value lastValue = node.getAttribute(cname);
            if (!Values.isEqual(value, lastValue)) {
                node.setAttribute(cname, value);
            }
        }
    }

    public void updateValueData(JsonArray valueArray) {
        Value val = ValueUtils.toValue(valueArray.get(0), valueArray.get(1));

        if (val != null) {
            if (val.getType() != null && node.getValueType() == null ||
                    !val.getType().getRawName().equals(node.getValueType().getRawName())) {
                node.setValueType(val.getType());
            }

            node.setValue(val);
        } else {
            node.setValue(null);
        }
    }

    public void startHandles() {
        hasHandle = true;

        if (relation != null) {
            relation.proactiveCancel();
            relation = null;
        }

        relation = client.observe(new NodeCoapHandler());
    }

    public void closeHandles() {
        hasHandle = false;

        if (relation != null) {
            relation.proactiveCancel();
            relation = null;
        }
    }

    public void loadNow() {
        hasEverLoaded = true;

        init();

        CoapResponse response = client.get();
        NodeCoapHandler handler = new NodeCoapHandler();

        if (response != null) {
            handler.onLoad(response);
        } else {
            LOG.warn("Loading eagerly failed for " + coapPath);
            hasEverLoaded = false;
            controller.getExecutor().schedule(() -> {
                LOG.warn("Retrying eager loading for " + coapPath);
                loadIfNeeded();
            }, 2, TimeUnit.SECONDS);
        }
    }

    public void loadIfNeeded() {
        if (!hasEverLoaded) {
            loadNow();
        }
    }

    private Action getOrCreateAction(Node node, Permission perm, boolean isChild) {
        Action action = node.getAction();
        if (action != null) {
            return action;
        }

        action = getRawAction(node, perm, isChild);
        node.setAction(action);
        return action;
    }

    private Action getRawAction(final Node node, Permission perm, final boolean isChild) {
        return new Action(perm, event -> {
            JsonObject params = event.getParameters();
            JsonObject obj = new JsonObject();
            obj.put("params", params);

            JsonObject wrapper = new JsonObject();
            wrapper.put("invoke", obj);

            event.getTable().sendReady();

            CoapClient c;
            if (!isChild) {
                c = client;
            } else {
                try {
                    c = controller.getClient(
                            coapPath + "/" + CustomURLEncoder.encode(node.getName(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }

            c.post(new CoapHandler() {
                @Override
                public void onLoad(CoapResponse response) {
                    JsonObject obj = new JsonObject(EncodingFormat.MESSAGE_PACK,
                                                    response.getPayload());
                    Tables.decodeFullTable(event.getTable(), obj);
                    event.getTable().close();
                }

                @Override
                public void onError() {
                }
            }, wrapper.encode(EncodingFormat.MESSAGE_PACK), 0);
        });
    }

    private void iterateActionMetaData(Action act,
                                       JsonArray array,
                                       boolean isCol) {
        ArrayList<Parameter> out = new ArrayList<>();
        for (Object anArray : array) {
            JsonObject data = (JsonObject) anArray;
            String name = data.get("name");

            if (out.stream().anyMatch((c) -> c.getName().equals(name))) {
                continue;
            }

            String type = data.get("type");
            ValueType valType = ValueType.toValueType(type);
            Parameter param = new Parameter(name, valType);

            String editor = data.get("editor");
            if (editor != null) {
                param.setEditorType(EditorType.make(editor));
            }
            Object def = data.get("default");
            if (def != null) {
                param.setDefaultValue(ValueUtils.toValue(def));
            }
            String placeholder = data.get("placeholder");
            if (placeholder != null) {
                param.setPlaceHolder(placeholder);
            }
            String description = data.get("description");
            if (description != null) {
                param.setDescription(description);
            }

            out.add(param);
        }

        if (!out.isEmpty()) {
            if (isCol) {
                act.setColumns(out);
            } else {
                act.setParams(out);
            }
        }
    }

    public class NodeCoapHandler implements CoapHandler {

        @Override
        public void onLoad(CoapResponse response) {
            if (response == null) {
                return;
            }

            try {
                JsonObject resp = new JsonObject(EncodingFormat.MESSAGE_PACK,
                                                 response.getPayload());
                System.out.println("onLoadResponse: " + resp);
                JsonArray listArray = resp.get("list");
                JsonArray valueArray = resp.get("value");

                if (listArray != null) {
                    System.out.println("CoapHandlerList: " + listArray); //DEBUG
                    updateListData(listArray);
                }

                if (valueArray != null) {
                    System.out.println("CoapHandlerValue: " + valueArray); //DEBUG
                    updateValueData(valueArray);
                }
            } catch (Exception e) {
                LOG.error("Error while handling COAP response at " + coapPath, e);
            }
        }

        @Override
        public void onError() {
            LOG.error("Error while handling COAP response for " + coapPath);
        }
    }
}
