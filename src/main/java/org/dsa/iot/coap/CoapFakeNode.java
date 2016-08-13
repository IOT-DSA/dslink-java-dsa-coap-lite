package org.dsa.iot.coap;

import org.dsa.iot.dslink.link.Linkable;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;

import java.io.UnsupportedEncodingException;

public class CoapFakeNode extends Node {
    private CoapClientController controller;

    public String getCoapPath() {
        return coapPath;
    }

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
        }

        if (!(child instanceof CoapFakeNode)) {
            return child;
        }

        CoapNodeController nodeController = child.getMetaData();

        if (nodeController == null) {
            nodeController = new CoapNodeController(
                    controller,
                    child,
                    ((CoapFakeNode) child).coapPath
            );

            nodeController.init();
        }

        nodeController.loadIfNeeded();

        return child;
    }

    @Override
    public boolean hasChild(String name) {
        return true;
    }

    @Override
    public NodeBuilder createChild(String name, String profile) {
        NodeBuilder b = null;
        try {
            b = new NodeBuilder(this, new CoapFakeNode(
                    name,
                    this,
                    getLink(),
                    controller,
                    coapPath + "/" + CustomURLEncoder.encode(name, "UTF-8").replaceAll(" ", "%20")
            ));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        if (b != null && profile != null) {
            b.setProfile(profile);
        }
        return b;
    }

    public CoapFakeNode getCachedChild(String name) {
        return (CoapFakeNode) super.getChild(name);
    }

    public CoapClientController getController() {
        return controller;
    }
}
