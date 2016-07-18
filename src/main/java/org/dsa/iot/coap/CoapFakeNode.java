package org.dsa.iot.coap;

import org.dsa.iot.dslink.link.Linkable;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;

public class CoapFakeNode extends Node {
    private CoapClientController controller;
    private String coapPath;

    public CoapFakeNode(String name, Node parent, Linkable link, CoapClientController controller, String coapPath) {
        super(name, parent, link);
        this.controller = controller;
        this.coapPath = coapPath;
    }

    @Override
    public Node getChild(String name) {
        Node child = super.getChild(name);
        if (child == null) {
            child = createChild(name).build();
            CoapNodeController nodeController = new CoapNodeController(
                    controller,
                    child,
                    ((CoapFakeNode) child).coapPath
            );

            nodeController.init();
            nodeController.loadNow();
        }
        return child;
    }

    @Override
    public boolean hasChild(String name) {
        return true;
    }

    @Override
    public NodeBuilder createChild(String name, String profile) {
        NodeBuilder b = new NodeBuilder(this, new CoapFakeNode(
                name,
                this,
                getLink(),
                controller,
                coapPath + "/" + name
        ));

        if (profile != null) {
            b.setProfile(profile);
        }
        return b;
    }
}
