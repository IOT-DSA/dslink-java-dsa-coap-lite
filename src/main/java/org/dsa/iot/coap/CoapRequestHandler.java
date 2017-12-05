package org.dsa.iot.coap;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.connection.DataHandler.DataReceived;
import org.dsa.iot.dslink.methods.StreamState;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;

/**
 * @author James (Juris) Puchin
 * Created on 12/4/2017
 */
public class CoapRequestHandler implements Handler<DataReceived> {

    DSLink link;
    Set<CoapClientController> clients = new HashSet<CoapClientController>();
    Map<Integer, CoapClientController> ridsToControllers = new ConcurrentHashMap<Integer, CoapClientController>();

    CoapRequestHandler(DSLink link) {
        this.link = link;
    }

    synchronized public void addClient(CoapClientController client) {
        clients.add(client);
    }

    @Override
    public void handle(DataReceived event) {
        final JsonArray data = event.getData();
        List<JsonObject> responses = new LinkedList<>();

        for (Object object : data) {
            JsonObject json = (JsonObject) object;
            Integer rid = json.get("rid");
            String path = json.get("path");
            CoapClientController controller = ridsToControllers.get(rid);

            if (controller == null && path != null) {
                for (CoapClientController client: clients) {
                    String rootpath = client.getRootPath();
                    if (path.startsWith(rootpath) || path.startsWith(rootpath.substring(1))) {
                        controller = client;
                        ridsToControllers.put(rid, controller);
                        break;
                    }
                }
            }

            if (controller != null) {
                if (path != null) {
                    if (!path.startsWith("/")) {
                        path = "/" + path;
                    }
                    String rootpath = controller.getRootPath();
                    if (path.startsWith(rootpath)) {
                        path = path.substring(rootpath.length());
                        if (path.length() <= 0) {
                            path = "/";
                        }
                    }
                    json.put("path", path);
                }

                controller.emit(json);
                if ("close".equals(json.get("method"))) {
                    ridsToControllers.remove(rid);
                }
            } else {
                try {
                    JsonObject resp = link.getResponder().parse(json);
                    responses.add(resp);
                } catch (Exception e) {
                    JsonObject resp = new JsonObject();
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
                Integer msgId = event.getMsgId();
                writeResponses(msgId, responses);
            }
        }
    }

    synchronized void writeResponses(Integer ackId, Collection<JsonObject> objects) {
        link.getWriter().writeRequestResponses(ackId, objects);
    }
}
