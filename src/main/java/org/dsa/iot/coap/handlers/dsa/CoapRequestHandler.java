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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author James (Juris) Puchin
 * Created on 12/4/2017
 */
public class CoapRequestHandler implements Handler<DataReceived> {

    private Map<Integer, CoapClientController> ridToController = new ConcurrentHashMap<>();
    private Map<Integer, CoapClientController> sidToController = new ConcurrentHashMap<>();

    private CoapLinkHandler coapLinkHandler;
    private Node rootNode;
    private Map<Integer, CoapObserveRelation> ridToObservation = new ConcurrentHashMap<>();

    public void add0Observer(CoapObserveRelation obs) {
        ridToObservation.put(0, obs);
    }

    public CoapRequestHandler(CoapLinkHandler handle, Node rootNode) {
        this.rootNode = rootNode;
        this.coapLinkHandler = handle;
    }

    private CoapClientController getControllerFromPath(String path) {
        String nodeName = Constants.extractNodeName(path);
        return getControllerFromNodeName(nodeName);
    }

    private CoapClientController getControllerFromNodeName(String nodeName) {
        Node node = rootNode.getChild(nodeName, false);
        return (node != null) ? (CoapClientController) node.getMetaData() : null;
    }

    public static JsonObject formulateResponse(final CoapResponse rawResponse) {
        //TODO: Hacky exception handling?
        if (rawResponse.getPayload() == null) return null;
        //String respString = new String(rawResponse.getPayload());
        //System.out.printf("Got response: " + respString); //DEBUG
        return Constants.extractPayloadObject(rawResponse);
    }

    private void generateAndAddStandardResponses(JsonObject json, List<JsonObject> responses) {
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

    private void parseAndRemoveClients(JsonObject json, JsonArray local, Map<CoapClientController, JsonArray> remote) {
        JsonArray sids = json.get("sids");
        for (Object sid : sids) {
            CoapClientController cont = sidToController.remove(sid);
            if (cont != null) {
                if (remote.containsKey(cont)) remote.get(cont).add(sid);
                else remote.put(cont, new JsonArray().add(sid));
            } else {
                local.add(sid);
            }
        }
    }

    @Override
    public void handle(DataReceived event) {
        final JsonArray data = event.getData();
        List<JsonObject> responses = new LinkedList<>();

        for (Object object : data) {
            JsonObject json = (JsonObject) object;
            String path = json.get("path");
            String method = json.get("method");

            //Handle remote close
            if (method != null && method.equals("close")) {
                int rid = json.get("rid");
                CoapClientController cont = ridToController.remove(rid);
                if (cont != null) {
                    CoapObserveRelation obs = ridToObservation.remove(rid);
                    if (obs != null) obs.proactiveCancel();
                    cont.postToRemote(json);
                } else {
                    generateAndAddStandardResponses(json, responses);
                }
            }
            //Handle remote unsubscribe
            else if (method != null && method.equals("unsubscribe")) {
                JsonArray local = new JsonArray();
                Map<CoapClientController, JsonArray> remoteMap = new HashMap<>();
                parseAndRemoveClients(json, local, remoteMap);
                int rid = json.get("rid");
                if (local.size() > 0) {
                    generateAndAddStandardResponses(Constants.createUnsubReq(local, rid), responses);
                }
                if (remoteMap.size() > 0) {
                    for (Map.Entry<CoapClientController, JsonArray> entry : remoteMap.entrySet()) {
                        entry.getKey().postToRemote(Constants.createUnsubReq(entry.getValue(), rid));
                    }
                }
            }
            //Handle subscriptions
            //TODO: when remote client goes down, need to send close requests to local broker (implement elsewhere, probably)
            else if (method != null && method.equals("subscribe")) {
                JsonArray local = new JsonArray();
                //System.out.println("NEW SUB REQUEST:" + json); //DEBUG
                Map<String, JsonArray> remote = new HashMap<>();
                JsonArray paths = json.get("paths");
                int rid = json.get("rid");
                Constants.sortLocalVsRemote(paths, local, remote);
                //Send local subscription requests
                if (local.size() > 0) {
                    JsonObject localReq = Constants.createSubReq(local, rid);
                    generateAndAddStandardResponses(localReq, responses);
                    //System.out.println(localReq); //DEBUG
                }
                //Send remote subscription requests
                if (remote.size() > 0) {
                    for (Map.Entry<String, JsonArray> ent : remote.entrySet()) {
                        CoapClientController cont = getControllerFromNodeName(ent.getKey());
                        if (cont == null) continue; //TODO: Handle better, when no node present
                        for (Object e : ent.getValue()) {
                            int sid = Constants.getSid(e);
                            sidToController.put(sid, cont);
                        }
                        JsonObject remoteReq = Constants.createSubReq(ent.getValue(), rid);
                        cont.postToRemote(remoteReq);
                        //System.out.println("SENT SUBS TO REMOTE:" + remoteReq); //DEBUG
                    }
                }
            }
            //Handle remote method invocations
            else if (path != null && path.contains(Constants.REMOTE_NAME)) {
                json.put("path", Constants.extractRemotePath(path));
                //Post to remote and get response
                CoapClientController cliContr = getControllerFromPath(path);
                //System.out.println("SENT REQ POST:" + json); //DEBUG
                if (cliContr == null) {
                    System.out.println("PATH IS WRONG!");
                    continue;
                }//Skip in case the path is wrong
                CoapResponse response = cliContr.postToRemote(json);
                //Do method specific steps
                switch (method) {
                    case "invoke":
                    case "list":
                        //create listener for the rid that will transmit list data
                        JsonObject obj = Constants.extractPayloadObject(response);
                        //System.out.println("Listing: " + obj); //DEBUG
                        String uri = cliContr.getUriPrefix() + obj.get(Constants.REMOTE_RID_FIELD);
                        CoapClient client = new CoapClient(uri);
                        //client.useEarlyNegotiation(64); //TODO: Is this needed
                        //TODO: verify listener
                        CoapObserveRelation observation = client.observe(new AsynchListener(coapLinkHandler));
                        int rid = json.get("rid");
                        ridToObservation.put(rid, observation);
                        ridToController.put(rid, cliContr);
                        break;
                    case "remove":
                        //TODO: make sure remove and set work
                    case "set":
                    default:
                        JsonObject resp = formulateResponse(response);
                        responses.add(resp);
                }
            }
            //Handle local method invocations
            else {
                generateAndAddStandardResponses(json, responses);
            }
        }
        Integer msgId = event.getMsgId();
        coapLinkHandler.getResponderLink().getWriter().writeRequestResponses(msgId, responses);
    }
}
