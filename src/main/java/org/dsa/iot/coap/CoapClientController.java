package org.dsa.iot.coap;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.Objects;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.dsa.iot.shared.SharedObjects;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
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

    private Endpoint endpoint;
    private ScheduledThreadPoolExecutor executor = SharedObjects.createDaemonThreadPool(8);

    private ScheduledFuture connectionFuture;

    private Map<String, CoapClient> clients = new HashMap<>();
    private CoapClient client = null;

    public CoapClientController(Node node) {
        this.node = node;
        executor.setMaximumPoolSize(128);
        executor.setKeepAliveTime(2, TimeUnit.MINUTES);
    }

    public String getRootPath() {
        return node.getPath();
    }

    public Node getNode() {
        return node;
    }

    private void initDefaultNodes() {
        if (!node.hasChild("remove", false)) {
            node
                    .createChild("remove", false)
                    .setDisplayName("Remove")
                    .setSerializable(false)
                    .setAction(new Action(Permission.WRITE, new DeleteCoapClientAction()))
                    .build();
        }

        if (!node.hasChild(Constants.REMOTE_NAME, false)) {
            node
                    .createChild(Constants.REMOTE_NAME, false)
                    .setDisplayName(Constants.REMOTE_NAME)
                    .setSerializable(false)
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

        if (!node.hasChild("ping", false)) {
            node
                    .createChild("ping", false)
                    .setDisplayName("Ping")
                    .setSerializable(false)
                    .setAction(new Action(Permission.WRITE, new PingAction()))
                    .build();
        }

        if (!node.hasChild("post", false)) {
            node
                    .createChild("post", false)
                    .setDisplayName("Post")
                    .setSerializable(false)
                    .setAction(new Action(Permission.WRITE, new PostAction()))
                    .build();
        }
    }

    public void init() {
        initDefaultNodes();

        node.getChild("status", false).setValue(new Value("Ready"));
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

    /**
     * Get CoapClient with the specified path, creates Coap Client if missing.
     *
     * @param path
     * @return
     */
    public CoapClient getHelloWorldClient(final String path) {
        //System.out.println("Got client: " + path); //DEBUG
        if (clients.get(path) == null) {
            CoapClient client = new CoapClient(uri.resolve(path.replace(" ", "%20")));
            client.setExecutor(executor);
            client.setEndpoint(endpoint);
            client.setTimeout(10000);
            clients.put(path, client);
        }
        return clients.get(path);
    }

    public void doError(String msg) {
        node.removeChild("broker", false);

        node.getChild("status", false).setValue(new Value(msg));

        connectionFuture = Objects.getDaemonThreadPool().schedule(this::init, 2, TimeUnit.SECONDS);
    }

    public ScheduledThreadPoolExecutor getExecutor() {
        return executor;
    }

    public void emit(JsonObject json) {

        System.out.println("Emit: " + json);
        byte[] input = new byte[0];
        try {
            input = json.toString().getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        CoapResponse response = getHelloWorldClient().post(input, 0);
        if (response != null) {
            System.out.println(Utils.prettyPrint(response));
        } else {
            System.out.println("No response received.");
        }
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

    CoapClient getHelloWorldClient() {
        String url = node.getConfig("coap_url").getString() + "/helloWorld";

        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            LOG.error("Failed to parse COAP URL.", e);
            doError(e.getMessage());
        }

        System.out.println(uri);
        client = new CoapClient(uri);

        return client;
    }

    private void helloWorldGET() {

        CoapResponse response = getHelloWorldClient().get();

        if (response != null) {
            System.out.println(Utils.prettyPrint(response));
        } else {
            System.out.println("No response received.");
        }
    }

    private void helloWorldPOST() {
        JsonObject obj = new JsonObject();

        obj.put("hi", "there");

        emit(obj);
    }

    public class PingAction implements Handler<ActionResult> {

        @Override
        public void handle(ActionResult event) {
            helloWorldGET();
        }
    }

    public class PostAction implements Handler<ActionResult> {

        @Override
        public void handle(ActionResult event) {
            helloWorldPOST();
        }
    }
}
