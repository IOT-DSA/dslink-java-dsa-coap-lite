package org.dsa.iot.coap;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.Objects;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.EncodingFormat;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.dsa.iot.shared.SharedObjects;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CoapClientController {
    private static final Logger LOG = LoggerFactory.getLogger(CoapClientController.class);

    private Node node;
    private URI uri;

    public CoapClientController(Node node) {
        this.node = node;
        executor.setMaximumPoolSize(128);
        executor.setKeepAliveTime(2, TimeUnit.MINUTES);
    }

    public Node getNode() {
        return node;
    }

    public void init() {
        connectionFuture = Objects.getDaemonThreadPool().schedule(() -> {
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
                        .setValue(new Value("Unknown"))
                        .build();
            }

            String url = node.getConfig("coap_url").getString();

            try {
                uri = new URI(url);
                makeEndpoint();
            } catch (URISyntaxException e) {
                LOG.error("Failed to parse COAP URL.", e);
                doError(e.getMessage());
            }

            try {
                if (!endpoint.isStarted()) {
                    endpoint.start();
                }
            } catch (IOException e) {
                LOG.error("Failed to start endpoint.", e);
                doError(e.getMessage());
            }

            try {
                CoapResponse root = getClient("/" + Constants.CONN).get();
                if (root == null || !root.isSuccess()) {
                    root = getClient("/__root").get();

                    if (root == null || !root.isSuccess()) {
                        throw new Exception("Failed to connect.");
                    }
                }

                JsonObject object = new JsonObject(EncodingFormat.MESSAGE_PACK, root.getPayload());

                if (!object.contains("dsa")) {
                    throw new Exception("Not a DSA COAP Server.");
                }

                if (!object.get("dsa").toString().equals("1.0.0")) {
                    throw new Exception("DSA-over-COAP v1.0.0 is the only protocol supported.");
                }
            } catch (Exception e) {
                LOG.error("Failed to run DSA COAP handshake.", e);
                doError(e.getMessage());
                return;
            }

            CoapFakeNode liveNode = new CoapFakeNode(
                    "broker",
                    node,
                    node.getLink(),
                    CoapClientController.this,
                    "/dsa"
            );

            node.addChild(liveNode);

            CoapNodeController nodeController = new CoapNodeController(
                    CoapClientController.this,
                    liveNode,
                    "/dsa"
            );
            nodeController.init();
            nodeController.loadIfNeeded();
            node.getChild("status", false).setValue(new Value("Ready"));
        }, 1, TimeUnit.SECONDS);
    }

    private void makeEndpoint() {
        if (endpoint != null) {
            endpoint.stop();
            endpoint.clear();
            endpoint = null;
        }

        endpoint = new CoapEndpoint();
        endpoint.setExecutor(executor);

        for (CoapClient client : clients.values()) {
            client.setEndpoint(endpoint);
        }
    }

    private Endpoint endpoint;
    private ScheduledThreadPoolExecutor executor = SharedObjects.createDaemonThreadPool(8);

    public CoapClient getClient(final String path) {
        if (clients.get(path) == null) {
            CoapClient client = new CoapClient(uri.resolve(path.replace(" ", "%20")));
            client.setExecutor(executor);
            client.setEndpoint(endpoint);
            client.setTimeout(10000);
            clients.put(path, client);
        }
        return clients.get(path);
    }

    private Map<String, CoapClient> clients = new HashMap<>();

    public void doError(String msg) {
        node.removeChild("broker", false);

        node.getChild("status", false).setValue(new Value(msg));

        connectionFuture = Objects.getDaemonThreadPool().schedule(this::init, 2, TimeUnit.SECONDS);
    }

    private ScheduledFuture connectionFuture;

    public ScheduledThreadPoolExecutor getExecutor() {
        return executor;
    }

    public class DeleteCoapClientAction implements Handler<ActionResult> {
        @Override
        public void handle(ActionResult event) {
            if (connectionFuture != null && !connectionFuture.isDone()) {
                connectionFuture.cancel(true);
            }

            if (endpoint != null) {
                endpoint.destroy();
            }

            node.delete(false);
        }
    }
}
