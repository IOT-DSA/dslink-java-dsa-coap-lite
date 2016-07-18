package org.dsa.iot.coap.utils;

import org.dsa.iot.dslink.methods.responses.InvokeResponse;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.actions.table.Row;
import org.dsa.iot.dslink.node.actions.table.Table;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.node.value.ValueUtils;
import org.dsa.iot.dslink.util.json.EncodingFormat;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;

import java.util.List;

public class Tables {
    public static JsonObject encodeFullTable(InvokeResponse response, Table table) {
        JsonObject obj = new JsonObject();

        if (table.getMode() != null) {
            obj.put("mode", table.getMode().getName());
        } else {
            obj.put("mode", Table.Mode.APPEND.getName());
        }
        obj.put("status", response.getState().getJsonName());

        JsonArray array = new JsonArray();
        List<Row> rows = table.getRows(true);

        if (rows != null) {
            for (Row row : rows) {
                JsonArray rowArray = new JsonArray();
                for (Value val : row.getValues()) {
                    rowArray.add(ValueUtils.toObject(val));
                }
                array.add(rowArray);
            }
        }

        obj.put("rows", array);

        JsonArray colArray = new JsonArray();

        if (table.getColumns() != null) {
            for (Parameter col : table.getColumns()) {
                colArray.add(encodeActionParameter(col));
            }
        }

        obj.put("columns", colArray);

        return obj;
    }

    public static JsonObject encodeActionParameter(Parameter parameter) {
        JsonObject obj = new JsonObject();
        obj.put("name", parameter.getName());
        obj.put("type", parameter.getType().getRawName());
        if (parameter.getEditorType() != null) {
            obj.put("editor", parameter.getEditorType().toJsonString());
            if (parameter.getEditorType().getMeta() != null) {
                obj.put("editorMeta", parameter.getEditorType().getMeta());
            }
        }

        if (parameter.getDescription() != null) {
            obj.put("description", parameter.getDescription());
        }

        if (parameter.getDefault() != null) {
            obj.put("default", ValueUtils.toObject(parameter.getDefault()));
        }

        if (parameter.getPlaceHolder() != null) {
            obj.put("placeholder", parameter.getPlaceHolder());
        }
        return obj;
    }

    public static void decodeFullTable(Table table, JsonObject obj) {
        JsonArray rows = obj.get("rows");
        JsonArray columns = obj.get("columns");
        iterateActionColumns(table, columns);

        if (rows != null) {
            for (Object row : rows) {
                if (row instanceof JsonArray) {
                    Row r = new Row();
                    for (Object x : (JsonArray) row) {
                        r.addValue(ValueUtils.toValue(x));
                    }
                    table.addRow(r);
                }
            }
        }
    }

    private static void iterateActionColumns(Table table,
                                              JsonArray array) {
        if (table.getColumns() == null || table.getColumns().isEmpty()) {
            if (array != null) {
                for (Object anArray : array) {
                    JsonObject data = (JsonObject) anArray;
                    String name = data.get("name");
                    String type = data.get("type");
                    ValueType valType = ValueType.toValueType(type);
                    Parameter param = new Parameter(name, valType);
                    table.addColumn(param);
                }
            }
        }
    }
}
