package org.dsa.iot.coap;

/**
 * @author James (Juris) Puchin
 * Created on 12/10/2017
 */
public class ClientSideObserver {
    CoapRequestHandler handler;

    public ClientSideObserver(CoapRequestHandler handl) {
        handler = handl;
        
    }
}
