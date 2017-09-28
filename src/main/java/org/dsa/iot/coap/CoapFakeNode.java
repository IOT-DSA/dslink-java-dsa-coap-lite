package org.dsa.iot.coap;

import java.io.UnsupportedEncodingException;
import org.dsa.iot.dslink.link.Linkable;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoapFakeNode extends Node {

    private static final Logger LOG = LoggerFactory.getLogger(CoapFakeNode.class);

    private CoapClientController controller;

    public String getCoapPath() {
        return coapPath;
    }

    private String coapPath;

    public CoapFakeNode(String name, Node parent,
                        Linkable link,
                        CoapClientController controller,
                        String coapPath,
                        boolean encodeName) {
        super(name, parent, link, encodeName);

        this.controller = controller;
        this.coapPath = coapPath;
        LOG.debug("Created node at " + getPath());
    }

    @Override
    public Node getChild(String name, boolean encodeName) {
        CoapFakeNode child = getCachedChild(name, encodeName);

        if (child == null) {
            child = (CoapFakeNode) createChild(name, encodeName).build();
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
    public boolean hasChild(String name, boolean encodeName) {
        return true;
    }

    @Override
    public NodeBuilder createChild(String name, boolean encodeName) {
        return createChild(name, null, encodeName);
    }

    @Override
    public NodeBuilder createChild(String name, String profile) {
        return createChild(name, profile, false);
    }

    public NodeBuilder createChild(String name, String profile, boolean encodeName) {
        NodeBuilder b = null;
        try {
            b = new NodeBuilder(this, new CoapFakeNode(
                    name,
                    this,
                    getLink(),
                    controller,
                    coapPath + "/" + CustomURLEncoder.encode(name, "UTF-8").replaceAll(" ", "%20"),
                    encodeName
            ));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        if (b != null && profile != null) {
            b.setProfile(profile);
        }
        return b;
    }

    public CoapFakeNode getCachedChild(String name, boolean encodeName) {
        return (CoapFakeNode) super.getChild(name, encodeName);
    }

    public CoapClientController getController() {
        return controller;
    }
}
