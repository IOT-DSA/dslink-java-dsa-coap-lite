package org.dsa.iot.coap;

import org.dsa.iot.dslink.link.Linkable;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;

public class CoapFakeNode extends Node {
    private static final Logger LOG = LoggerFactory.getLogger(CoapFakeNode.class);

    private CoapClientController controller;

    public String getCoapPath() {
        return coapPath;
    }

    private String coapPath;

    public CoapFakeNode(String name, Node parent, Linkable link, CoapClientController controller, String coapPath) {
        super(name, parent, link);

        this.controller = controller;
        this.coapPath = coapPath;
        LOG.debug("Created node at " + getPath());
    }

    @Override
    public Node getChild(String name) {
        CoapFakeNode child = getCachedChild(name);

        if (child == null) {
            child = (CoapFakeNode) createChild(name).build();
        }

        CoapNodeController nodeController = child.getMetaData();

        if (nodeController == null) {
            nodeController = new CoapNodeController(
                    controller,
                    child,
                    child.coapPath
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
