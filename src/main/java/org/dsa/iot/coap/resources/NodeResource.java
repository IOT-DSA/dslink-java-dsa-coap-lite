package org.dsa.iot.coap.resources;

import org.dsa.iot.coap.CoapServerController;
import org.dsa.iot.coap.CustomURLEncoder;
import org.dsa.iot.coap.utils.Tables;
import org.dsa.iot.commons.Container;
import org.dsa.iot.dslink.link.Requester;
import org.dsa.iot.dslink.methods.StreamState;
import org.dsa.iot.dslink.methods.requests.InvokeRequest;
import org.dsa.iot.dslink.methods.requests.ListRequest;
import org.dsa.iot.dslink.methods.requests.SubscribeRequest;
import org.dsa.iot.dslink.methods.responses.InvokeResponse;
import org.dsa.iot.dslink.methods.responses.ListResponse;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.value.SubscriptionValue;
import org.dsa.iot.dslink.node.value.ValueUtils;
import org.dsa.iot.dslink.util.Objects;
import org.dsa.iot.dslink.util.SubData;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.EncodingFormat;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.core.server.resources.ResourceObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class NodeResource extends CoapResource {
    private static final Logger LOG = LoggerFactory.getLogger(NodeResource.class);

    private CoapServerController controller;
    private int count = 0;
    private int listRid;
    private boolean listening = false;
    private boolean isSubscribed = false;
    private String nodePath;

    private static String getResourceName(String path) {
        String[] parts = path.split("/");
        try {
            return CustomURLEncoder.encode(parts[parts.length - 1], "UTF-8").replaceAll(" ", "%20");
        } catch (UnsupportedEncodingException e) {
            return parts[parts.length - 1];
        }
    }

    public NodeResource(CoapServerController controller, String path) {
        super(path.equals("/") ? "dsa" : getResourceName(path));

        path = path.replaceAll("%20", " ").replaceAll("//", "/");

        getAttributes().setTitle(path);
        getAttributes().setObservable();
        getAttributes().addResourceType("observe");
        setObservable(true);

        this.controller = controller;
        this.nodePath = path;
    }

    private boolean isSynchronized = false;

    @Override
    public void handleGET(CoapExchange exchange) {
        if (!isSynchronized) {
            count++;
            check();
            latch = new CountDownLatch(1);
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            isSynchronized = true;
            count--;
        }

        JsonObject obj = new JsonObject(object);
        byte[] encoded = obj.encode(EncodingFormat.MESSAGE_PACK);
        exchange.respond(CoAP.ResponseCode.VALID, encoded);
    }

    @Override
    public void handlePOST(final CoapExchange exchange) {
        super.handlePOST(exchange);
        JsonObject req = new JsonObject(EncodingFormat.MESSAGE_PACK, exchange.getRequestPayload());

        JsonObject invokeRequest = req.get("invoke");
        JsonObject invokeCloseRequest = req.get("invokeClose");
        final Requester requester = controller.getRequesterLink().getRequester();

        if (invokeRequest != null) {
            JsonObject params = invokeRequest.get("params");
            InvokeRequest requesterInvokeRequest = new InvokeRequest(getDsaPath(), params);
//            final Container<Integer> rid = new Container<>();
//            rid.setValue(requester.invoke(requesterInvokeRequest, new Handler<InvokeResponse>() {
//                @Override
//                public void handle(InvokeResponse event) {
//                    JsonObject invokeResponseObject = object.get("invoke");
//                    if (invokeResponseObject == null) {
//                        invokeResponseObject = new JsonObject();
//                        object.put("invoke", invokeResponseObject);
//                    }
//                    JsonObject returned = Tables.encodeFullTable(event, event.getTable());
//                    invokeResponseObject.put("" + rid.getValue(), returned);
//                    changed();
//                }
//            }));
//
//            JsonObject obj = new JsonObject();
//            obj.put("rid", rid);
//            exchange.respond(CoAP.ResponseCode.VALID, obj.encode(EncodingFormat.MESSAGE_PACK));

            final Container<InvokeResponse> lastResponse = new Container<>();

            final Container<ScheduledFuture> future = new Container<>();
            final Container<Boolean> isDone = new Container<>(false);

            final int rid = requester.invoke(requesterInvokeRequest, event -> {
                lastResponse.setValue(event);

                if (event.getState() == StreamState.CLOSED) {
                    if (future.getValue() != null && !future.getValue().isDone()) {
                        future.getValue().cancel(false);
                    }

                    if (!isDone.getValue()) {
                        JsonObject tc = Tables.encodeFullTable(event, event.getTable());
                        isDone.setValue(true);
                        exchange.respond(
                                CoAP.ResponseCode.VALID,
                                tc.encode(EncodingFormat.MESSAGE_PACK)
                        );
                    } else {
                        LOG.warn("Action exchange is already complete.");
                    }
                }
            });

            future.setValue(Objects.getDaemonThreadPool().schedule(() -> {
                InvokeResponse response = lastResponse.getValue();
                if (response == null || response.getState() != StreamState.CLOSED) {
                    isDone.setValue(true);
                    exchange.respond(CoAP.ResponseCode.GATEWAY_TIMEOUT);
                    requester.closeStream(rid, event -> {
                    });
                }
            }, 5000, TimeUnit.MILLISECONDS));
        } else if (invokeCloseRequest != null) {
            int rid = invokeCloseRequest.get("rid");
            requester.closeStream(rid, event -> {
                byte[] bytes = new JsonObject().encode(EncodingFormat.MESSAGE_PACK);
                exchange.respond(CoAP.ResponseCode.VALID, bytes);
            });
        } else {
            byte[] bytes = new JsonObject().encode(EncodingFormat.MESSAGE_PACK);
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST, bytes);
        }
    }

    @Override
    public synchronized void addObserver(ResourceObserver observer) {
        super.addObserver(observer);

        count++;

        check();
    }

    @Override
    public void addObserveRelation(ObserveRelation relation) {
        super.addObserveRelation(relation);

        count++;

        check();
    }

    @Override
    public void removeObserveRelation(ObserveRelation relation) {
        super.removeObserveRelation(relation);

        count--;

        check();
    }

    @Override
    public synchronized void removeObserver(ResourceObserver observer) {
        super.removeObserver(observer);

        count--;

        check();
    }

    public String getDsaPath() {
        return nodePath;
    }

    public void check() {
        Requester requester = controller.getRequesterLink().getRequester();

        if (count > 0 && !listening) {
            listening = true;

            ListRequest listRequest = new ListRequest(getDsaPath());
            listRid = requester.list(listRequest, new ListHandler());
        } else if (count <= 0 && listening) {
            requester.closeStream(listRid, event -> {
            });
        }
    }

    private CountDownLatch latch;

    public class ListHandler implements Handler<ListResponse> {
        @Override
        public void handle(ListResponse event) {
            isSynchronized = true;

            if (latch != null) {
                latch.countDown();
            }

            Requester requester = controller.getRequesterLink().getRequester();

            object.put("list", event.getJsonResponse(null).get("updates"));

            for (Node node : event.getUpdates().keySet()) {
                boolean removed = event.getUpdates().get(node);
                if (removed) {
                    try {
                        String name = CustomURLEncoder.encode(node.getName(), "UTF-8").replaceAll(" ", "%20");
                        if (getRealChild(name) != null) {
                            delete(getRealChild(name));
                            childs.remove(name);
                        }
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        String name = CustomURLEncoder.encode(node.getName(), "UTF-8").replaceAll(" ", "%20");
                        if (getRealChild(name) == null) {
                            add(new NodeResource(controller, node.getPath()));
                        }
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (event.getNode().getValueType() != null) {
                if (!isSubscribed) {
                    isSubscribed = true;
                    SubscribeRequest subscribeRequest = new SubscribeRequest(new HashSet<SubData>() {{
                        add(new SubData(getDsaPath(), 0));
                    }});

                    try {
                        requester.subscribe(subscribeRequest, new SubscribeHandler());
                    } catch (Exception e) {
                        if (!e.getMessage().contains("already subscribed")) {
                            throw e;
                        }
                    }
                }
            } else {
                if (isSubscribed) {
                    isSubscribed = false;
                    requester.unsubscribe(getDsaPath(), event1 -> {
                    });
                }
            }

            changed();
        }
    }

    public class SubscribeHandler implements Handler<SubscriptionValue> {
        @Override
        public void handle(SubscriptionValue event) {
            JsonArray array = new JsonArray();
            array.add(ValueUtils.toObject(event.getValue()));
            array.add(event.getValue().getTimeStamp());
            object.put("value", array);
            changed();
        }
    }

    private ConcurrentHashMap<String, Object> object = new ConcurrentHashMap<>();
    private Map<String, Resource> childs = new HashMap<>();

    @Override
    public Resource getChild(String name) {
        Resource child = super.getChild(name);
        if (child == null) {
            if (childs.containsKey(name)) {
                child = childs.get(name);
            } else {
                String nodePath = (getDsaPath() + "/" + name).replaceAll("%20", " ").replaceAll("\\+", " ").replaceAll("//", "/");
                child = new NodeResource(controller, nodePath);
                add(child);
                childs.put(name, child);
            }
        }
        return child;
    }

    public Resource getRealChild(String name) {
        return super.getChild(name);
    }
}
