package org.dsa.iot.coap.controllers;

import org.dsa.iot.coap.CoapLinkHandler;
import org.dsa.iot.coap.Constants;
import org.dsa.iot.coap.handlers.coap.AsynchListener;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.Objects;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CoapClientController {

    private static final int RETRIES = 50;
    private static final Logger LOG = LoggerFactory.getLogger(CoapClientController.class);

    private Node node;
    private URI uri;
    private CoapLinkHandler coapLinkHandler;

    private Endpoint endpoint;

    private ScheduledFuture connectionFuture;

    private Map<String, CoapClient> clients = new ConcurrentHashMap<>();

    public CoapClientController(Node node, CoapLinkHandler coapLinkHandler) {
        this.node = node;
        this.coapLinkHandler = coapLinkHandler;
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

//        if (!node.hasChild("ping", false)) {
//            node
//                    .createChild("ping", false)
//                    .setDisplayName("Ping")
//                    .setSerializable(false)
//                    .setAction(new Action(Permission.WRITE, new PingAction()))
//                    .build();
//        }

//        if (!node.hasChild("post", false)) {
//            node
//                    .createChild("post", false)
//                    .setDisplayName("Post")
//                    .setSerializable(false)
//                    .setAction(new Action(Permission.WRITE, new PostAction()))
//                    .build();
//        }
    }

    public void init() {
        initDefaultNodes();

//        try {
//            String[] spl = node.getConfig("coap_url").getString().split(":");
//            port = new Integer(spl[spl.length-1]);
//            //port = node.getConfig("coap_port").getNumber().intValue();
//            clientServer = new ClientSideServer(node);
//            clientServer.addEndpoints(port);
//            clientServer.start();
//        } catch (SocketException e) {
//            System.err.println("Failed to initialize server: " + e.getMessage());
//        }
        Objects.getDaemonThreadPool().schedule(this::setupSubscriptionObserver, 0, TimeUnit.SECONDS);

        node.getChild("status", false).setValue(new Value("Ready"));
    }

    private void setupSubscriptionObserver() {
        JsonObject ridReq = new JsonObject();
        ridReq.put(Constants.GIMME, Constants.RID_ZERO_HANDLE);
        CoapResponse resp = postToRemote(ridReq);
        JsonObject cont = Constants.extractPayloadObject(resp);
        String rid0ID = cont.get(Constants.GIMME);

        String uri = getUriPrefix() + rid0ID;
        CoapClient client = new CoapClient(uri);
        CoapObserveRelation observation = client.observe(new AsynchListener(coapLinkHandler));
        coapLinkHandler.add0Observer(observation);
    }

    private void makeEndpoint() {
        if (endpoint != null) {
            endpoint.stop();
            endpoint.clear();
            endpoint = null;
        }

        endpoint = new CoapEndpoint();
        //endpoint.setExecutor(executor);

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
    public CoapClient getClient(final String path) {
        //TODO: possibly do creation here
//        if (clients.get(path) == null) {
//            CoapClient client = new CoapClient(uri.resolve(path.replace(" ", "%20")));
//            client.setExecutor(executor);
//            client.setEndpoint(endpoint);
//            client.setTimeout(10000);
//            clients.put(path, client);
//        }
        return clients.get(path);
    }

    public void doError(String msg) {
        node.removeChild("broker", false);

        node.getChild("status", false).setValue(new Value(msg));

        connectionFuture = Objects.getDaemonThreadPool().schedule(this::init, 2, TimeUnit.SECONDS);
    }

    public CoapResponse postToRemote(JsonObject json) {
        //System.out.println("Sent: " + json); //DEBUG
        byte[] input = Constants.jsonToBytes(json);
        CoapResponse resp = null;
        int tries = 0;
        while (resp == null && tries++ < RETRIES) resp = getClient().post(input, 0);
        //System.out.println("Got response to: " + json + "\n" + resp); //DEBUG
        return resp;
    }

    public String getUriPrefix() {
        return node.getConfig("coap_url").getString() + "/";
    }

    public CoapClient getClient() {
        String url = getUriPrefix() + Constants.MAIN_SERVER_NAME;
        CoapClient client = clients.get(url);
        if (client == null) {
            try {
                uri = new URI(url);
            } catch (URISyntaxException e) {
                LOG.error("Failed to parse COAP URL.", e);
                doError(e.getMessage());
            }

            //System.out.println(uri); //DEBUG
            client = new CoapClient(uri);
            //client.useCONs();
            //client.useEarlyNegotiation(64);
            clients.put(url, client);
        }
        return client;
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

    ///////////////////////////////////////////////////////////////////////////
    // Testing Methods
    ///////////////////////////////////////////////////////////////////////////

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

    private void helloWorldGET() {

        CoapResponse response = getClient().get();

        if (response != null) {
            System.out.println(Utils.prettyPrint(response));
        } else {
            System.out.println("No response received.");
        }
    }

    private void helloWorldPOST() {
        JsonObject obj = new JsonObject();

        obj.put("hi", "there");

        postToRemote(obj);
    }
}
