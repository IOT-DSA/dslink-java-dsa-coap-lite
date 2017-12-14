package org.dsa.iot.coap.handlers.dsa;

import org.dsa.iot.coap.CoapLinkHandler;
import org.dsa.iot.coap.Constants;
import org.dsa.iot.coap.controllers.CoapClientController;
import org.dsa.iot.coap.handlers.coap.AsynchListener;
import org.dsa.iot.dslink.connection.DataHandler.DataReceived;
import org.dsa.iot.dslink.methods.StreamState;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author James (Juris) Puchin
 * Created on 12/4/2017
 */
public class CoapRequestHandler implements Handler<DataReceived> {

    private CoapLinkHandler coapLinkHandler;
    private Node rootNode;
    private Map<Integer, CoapObserveRelation> ridsToObservations = new ConcurrentHashMap<>();

    public void add0Observer(CoapObserveRelation obs) {
        ridsToObservations.put(0, obs);
    }

    public CoapRequestHandler(CoapLinkHandler handle, Node rootNode) {
        this.rootNode = rootNode;
        this.coapLinkHandler = handle;
    }

    private CoapClientController getControllerFromPath(String path) {
        String nodeName = extractNodeName(path);
        return ((CoapClientController) rootNode.getChild(nodeName, false).getMetaData());
    }

    private JsonObject formulateResponse(CoapResponse rawResponse) {
        //TODO: Hacky exception handling?
        if (rawResponse.getPayload() == null) return null;
        String respString = new String(rawResponse.getPayload());
        System.out.printf("Got response: " + respString);
        return Constants.extractPayload(rawResponse);
    }

    private String extractRemotePath(String path) {
        int idx = path.indexOf(Constants.REMOTE_NAME);
        if (idx >= 0) {
            String sub = path.substring(idx + Constants.REMOTE_NAME.length());
            if (sub.length() > 0) return sub;
            else return "/";
        } else {
            return null;
        }
    }

    private String extractNodeName(String path) {
        int idx = path.indexOf(Constants.REMOTE_NAME);
        if (idx >= 0) {
            String[] split = path.substring(0, idx).split("/");
            return split[split.length - 1];
        } else {
            return null;
        }
    }

    @Override
    public void handle(DataReceived event) {
        final JsonArray data = event.getData();
        List<JsonObject> responses = new LinkedList<>();

        for (Object object : data) {
            JsonObject json = (JsonObject) object;
            String path = json.get("path");

            if (json.get("method") != null)
                if(json.get("method").equals("subscribe"))
                    System.out.println(json);

            if (path != null && path.contains(Constants.REMOTE_NAME)) {
                String method = json.get("method");
                json.put("path", extractRemotePath(path));
                //Post to remote and get reponse
                CoapClientController cliContr = getControllerFromPath(path);
                CoapResponse response = cliContr.postToRemote(json);
                //Do method specific steps
                switch (method) {
                    case "unsubscribe":
                        //nothing to do
                        break;
                    case "close":
                        CoapObserveRelation obs = ridsToObservations.remove(json.get("rid"));
                        if (obs != null) obs.proactiveCancel();
                        break;
                    case "subscribe":
                        //Nothing to do
                        System.out.println("SUBSCRIBED!"); //DEBUG
                        break;
                    case "list":
                        JsonObject obj = Constants.extractPayload(response);
                        String uri = cliContr.getUriPrefix() + obj.get(Constants.REMOTE_RID_FIELD);
                        CoapClient client = new CoapClient(uri);
                        //TODO: verify listener
                        CoapObserveRelation observation = client.observe(new AsynchListener(coapLinkHandler));
                        ridsToObservations.put(json.get("rid"), observation);
                        break;
                    case "invoke":
                        //TODO: handle streaming
                    case "remove":
                    case "set":
                    default:
                        JsonObject resp = formulateResponse(response);
                        responses.add(resp);
                }

            } else {
                try {
                    JsonObject resp = coapLinkHandler.getResponderLink().getResponder().parse(json);
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
        }
        Integer msgId = event.getMsgId();
        coapLinkHandler.getResponderLink().getWriter().writeRequestResponses(msgId, responses);
    }

//        List<JsonObject> responses = new LinkedList<>();

//        for (Object object : data) {
//            JsonObject json = (JsonObject) object;
//            Integer rid = json.get("rid");
//            String path = json.get("path");
//            CoapClientController controller = ridsToControllers.get(rid);
//
//            if (controller == null && path != null) {
//                for (CoapClientController client: clients) {
//                    String rootpath = client.getRootPath();
//                    if (path.startsWith(rootpath) || path.startsWith(rootpath.substring(1))) {
//                        controller = client;
//                        ridsToControllers.put(rid, controller);
//                        break;
//                    }
//                }
//            }
//
//            if (controller != null) {
//                if (path != null) {
//                    if (!path.startsWith("/")) {
//                        path = "/" + path;
//                    }
//                    String rootpath = controller.getRootPath();
//                    if (path.startsWith(rootpath)) {
//                        path = path.substring(rootpath.length());
//                        if (path.length() <= 0) {
//                            path = "/";
//                        }
//                    }
//                    json.put("path", path);
//                }
//
//                controller.emit(json);
//
//                if ("close".equals(json.get("method"))) {
//                    ridsToControllers.remove(rid);
//                }
//            } else {
//                //Standard case
//                try {
//                    JsonObject resp = link.getResponder().parse(json);
//                    responses.add(resp);
//                } catch (Exception e) {
//                    JsonObject resp = new JsonObject();
//                    if (rid != null) {
//                        resp.put("rid", rid);
//                    }
//                    resp.put("stream", StreamState.CLOSED.getJsonName());
//
//                    JsonObject err = new JsonObject();
//                    err.put("msg", e.getMessage());
//                    { // Build stack trace
//                        StringWriter writer = new StringWriter();
//                        e.printStackTrace(new PrintWriter(writer));
//                        err.put("detail", writer.toString());
//                    }
//                    resp.put("error", err);
//                    responses.add(resp);
//                }
//                Integer msgId = event.getMsgId();
//                writeResponses(msgId, responses);
//            }
//        }
}