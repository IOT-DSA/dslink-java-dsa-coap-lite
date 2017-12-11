package org.dsa.iot.coap.resources;

import org.eclipse.californium.core.CoapResource;

/**
 * @author James (Juris) Puchin
 * Created on 12/10/2017
 */
public class RidUpdateResource extends CoapResource {

    int rid;
    public RidUpdateResource(int rid) {
        // set resource identifier
        super("rid_" + Integer.toString(rid));
        this.rid = rid;

        // set display name
        getAttributes().setTitle("rid_" + Integer.toString(rid));
    }
}
