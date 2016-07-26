package org.dsa.iot.coap.utils;

import org.dsa.iot.coap.CoapFakeNode;
import org.dsa.iot.coap.CoapNodeController;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class NodeBuilders {
    public static void applyMultiCoapChildBuilders(CoapFakeNode owner, List<NodeBuilder> builders) {
        List<Node> nodes = new ArrayList<>();
        for (NodeBuilder builder : builders) {
            Node node = builder.getChild();

            try {
                String go = owner.getCoapPath() + "/" + URLEncoder.encode(node.getName(), "UTF-8");
                CoapNodeController nc = new CoapNodeController(owner.getController(), node, go);
                nc.init();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            nodes.add(node);
        }

        owner.addChildren(nodes);
    }
}
