package org.dsa.iot.coap.resources;

import org.dsa.iot.dslink.util.json.JsonObject;

/**
 * @author James (Juris) Puchin
 * Created on 12/15/2017
 */
public interface UpdateResourceInterface {

    public void postDSAUpdate(JsonObject json);

}
