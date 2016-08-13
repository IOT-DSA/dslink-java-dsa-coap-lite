package org.dsa.iot.coap.utils;

import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;

import java.util.Objects;

public class Values {
    public static boolean isEqual(Value a, Value b) {
        if (a != null && b == null) {
            return false;
        }

        if (a == null && b != null) {
            return false;
        }

        if (a == null) {
            return true;
        }

        if (!a.getType().equals(b.getType())) {
            return false;
        }

        if (a.getType() == ValueType.STRING) {
            return !(a.getString() == null && b.getString() != null) &&
                    !(b.getString() != null && b.getString() == null) &&
                    a.getString().equals(b.getString());

        } else if (a.getType() == ValueType.BOOL) {
            return a.getBool() == b.getBool();
        } else if (a.getType() == ValueType.NUMBER) {
            return Objects.equals(a.getNumber(), b.getNumber());
        }

        return false;
    }
}
