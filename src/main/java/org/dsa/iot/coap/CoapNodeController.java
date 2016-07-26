package org.dsa.iot.coap;

import org.dsa.iot.coap.utils.NodeBuilders;
import org.dsa.iot.coap.utils.Tables;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.Writable;
import org.dsa.iot.dslink.node.actions.*;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.node.value.ValueUtils;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.EncodingFormat;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CoapNodeController {
    private static final Logger LOG = LoggerFactory.getLogger(CoapNodeController.class);

    private CoapClientController controller;
    private int handles = 0;
    private boolean hasHandle = false;
    private Node node;
    private CoapClient client;
    private String coapPath;

    public CoapNodeController(CoapClientController controller, Node node, String coapPath) {
        this.controller = controller;
        this.node = node;

        node.setSerializable(false);
        this.coapPath = coapPath;
    }

    public void init() {
        client = controller.getClient(coapPath);

        node.getListener().setOnListHandler(new Handler<Node>() {
            @Override
            public void handle(Node event) {
                handles++;
                checkHandles();
            }
        });

        node.getListener().setOnListClosedHandler(new Handler<Node>() {
            @Override
            public void handle(Node event) {
                handles--;
                checkHandles();
            }
        });

        node.getListener().setOnSubscribeHandler(new Handler<Node>() {
            @Override
            public void handle(Node event) {
                handles++;
                checkHandles();
            }
        });

        node.getListener().setOnUnsubscribeHandler(new Handler<Node>() {
            @Override
            public void handle(Node event) {
                handles--;
                checkHandles();
            }
        });
    }

    public void checkHandles() {
        if (handles <= 0 && hasHandle) {
            closeHandles();
            handles = 0;
        } else if (!hasHandle) {
            startHandles();
        }
    }

    private CoapObserveRelation relation;

    public void updateListData(JsonArray listArray) {
        List<NodeBuilder> childQueue = new ArrayList<>();
        for (Object o : listArray) {
            if (o instanceof JsonArray) {
                JsonArray m = (JsonArray) o;

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
                    node.setValueType(ValueType.toValueType(value.getString()));
                } else if (key.equals("$name")) {
                    node.setDisplayName(value.getString());
                } else if (key.equals("$invokable")) {
                    Permission perm = Permission.toEnum(value.getString());
                    Action act = getOrCreateAction(node, perm, false);
                    act.setPermission(perm);
                } else if (key.equals("$columns")) {
                    if (mvalue instanceof JsonArray) {
                        JsonArray array = (JsonArray) mvalue;
                        Action act = getOrCreateAction(node, Permission.NONE, false);
                        iterateActionMetaData(act, array, true);
                    }
                } else if (key.equals("$writable")) {
                    String string = value.getString();
                    node.setWritable(Writable.toEnum(string));
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
                    act.setResultType(ResultType.toEnum(string));
                } else if (key.startsWith("$$")) {
                    node.setRoConfig(key.substring(2), value);
                } else if (key.startsWith("$")) {
                    node.setConfig(key.substring(1), value);
                } else if (key.startsWith("@")) {
                    node.setAttribute(key.substring(1), value);
                } else {
                    Node child = node.getChild(key);

                    if (child == null) {
                        NodeBuilder builder = node.createChild(key);
                        if (mvalue instanceof JsonObject) {
                            JsonObject co = (JsonObject) mvalue;
                            for (Map.Entry<String, Object> entry : co) {
                                applyCreatedAttribute(builder, entry.getKey(), entry.getValue());
                            }
                        }
                        builder.setSerializable(false);
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
                if (obj.contains("change") && obj.get("change").equals("remove")) {
                    String key = obj.get("name");

                    if (key.startsWith("$$")) {
                        node.removeRoConfig(key.substring(2));
                    } else if (key.startsWith("$")) {
                        node.removeConfig(key.substring(1));
                    } else if (key.startsWith("@")) {
                        node.removeAttribute(key.substring(1));
                    } else {
                        try {
                            node.removeChild(URLEncoder.encode(key, "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        NodeBuilders.applyMultiCoapChildBuilders((CoapFakeNode) node, childQueue);
    }

    public void applyCreatedAttribute(NodeBuilder n, String key, Object mvalue) {
        Value value = ValueUtils.toValue(mvalue);

        if (value == null) {
            value = new Value((String) null);
        }

        if (key.equals("$is")) {
            //node.setProfile(value.getString());
        } else if (key.equals("$type")) {
            n.setValueType(ValueType.toValueType(value.getString()));
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
            n.setConfig(key.substring(1), value);
        } else if (key.startsWith("@")) {
            n.setAttribute(key.substring(1), value);
        }
    }

    public void applyAttribute(Node n, String key, Object mvalue, boolean isChild) {
        Value value = ValueUtils.toValue(mvalue);

        if (value == null) {
            value = new Value((String) null);
        }

        if (key.equals("$is")) {
            //node.setProfile(value.getString());
        } else if (key.equals("$type")) {
            n.setValueType(ValueType.toValueType(value.getString()));
        } else if (key.equals("$name")) {
            n.setDisplayName(value.getString());
        } else if (key.equals("$invokable")) {
            Permission perm = Permission.toEnum(value.getString());
            Action act = getOrCreateAction(n, perm, isChild);
            act.setPermission(perm);
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
            n.setRoConfig(key.substring(2), value);
        } else if (key.startsWith("$")) {
            n.setConfig(key.substring(1), value);
        } else if (key.startsWith("@")) {
            n.setAttribute(key.substring(1), value);
        }
    }

    public void updateValueData(JsonArray valueArray) {
        Value val = ValueUtils.toValue(valueArray.get(0), (String) valueArray.get(1));

        if (!val.getType().getRawName().equals(node.getValueType().getRawName())) {
            node.setValueType(val.getType());
        }

        node.setValue(val);
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
        CoapResponse response = client.get();
        NodeCoapHandler handler = new NodeCoapHandler();

        if (response != null) {
            handler.onLoad(response);
        } else {
            LOG.warn("Loading eagerly failed for " + coapPath);
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
        return new Action(perm, new Handler<ActionResult>() {
            @Override
            public void handle(final ActionResult event) {
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
                    c = controller.getClient(coapPath + "/" + Node.checkAndEncodeName(node.getName()));
                }

                c.post(new CoapHandler() {
                    @Override
                    public void onLoad(CoapResponse response) {
                        JsonObject obj = new JsonObject(EncodingFormat.MESSAGE_PACK, response.getPayload());
                        Tables.decodeFullTable(event.getTable(), obj);
                        event.getTable().close();
                    }

                    @Override
                    public void onError() {

                    }
                }, wrapper.encode(EncodingFormat.MESSAGE_PACK), 0);
            }
        });
    }

    private static void iterateActionMetaData(Action act,
                                              JsonArray array,
                                              boolean isCol) {
        ArrayList<Parameter> out = new ArrayList<>();
        for (Object anArray : array) {
            JsonObject data = (JsonObject) anArray;
            String name = data.get("name");
            String type = data.get("type");
            ValueType valType = ValueType.toValueType(type);
            Parameter param = new Parameter(name, valType);
            if (isCol) {
                out.add(param);
            } else {
                String editor = data.get("editor");
                if (editor != null) {
                    param.setEditorType(EditorType.make(editor));
                }
                Object def = data.get("default");
                if (def != null) {
                    param.setDefaultValue(ValueUtils.toValue(def));
                }
                out.add(param);
            }
        }

        if (isCol) {
            act.setColumns(out);
        } else {
            act.setParams(out);
        }
    }

    public class NodeCoapHandler implements CoapHandler {
        @Override
        public void onLoad(CoapResponse response) {
            if (response == null) {
                return;
            }

            try {
                JsonObject resp = new JsonObject(EncodingFormat.MESSAGE_PACK, response.getPayload());
                JsonArray listArray = resp.get("list");
                JsonArray valueArray = resp.get("value");

                if (listArray != null) {
                    updateListData(listArray);
                }

                if (valueArray != null) {
                    updateValueData(valueArray);
                }
            } catch (Exception e) {
                LOG.error("Error while handling COAP response at " + coapPath, e);
            }
        }

        @Override
        public void onError() {
        }
    }
}
