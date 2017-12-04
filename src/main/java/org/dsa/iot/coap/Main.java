package org.dsa.iot.coap;

import org.dsa.iot.dslink.DSLinkFactory;

public class Main {
    public static void main(String[] args) {
        DSLinkFactory.start(args, new CoapLinkHandler());
    }
}
