/*
 * Copyright (C) 2008-2015 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.processor;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.MediaType;

import ru.citeck.ecos.webscripts.processor.DataBundleProcessorWebscript;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * If input data come from form data and(or) query string, try to convert to JSON
 *
 * @author Alexey Moiseev <alexey.moiseev@citeck.ru>
 */
public class FormToJSONParametersFilter extends AbstractDataBundleLine {

    public static final String ARGS_PARAMETER_WITH_JSON = "jsondata";

    @Override
    public DataBundle process(DataBundle input) {
        Map<String, Object> model = input.needModel();

        HashMap<String, Object> newModel = new HashMap<>(model);
        String jsonStr = checkAndConvertFromArgsToJSON(model);
        InputStream newIS = null;

        if (jsonStr != null) {
            newIS = new ByteArrayInputStream(jsonStr.getBytes(StandardCharsets.UTF_8));
            newModel.put(ProcessorConstants.KEY_MIMETYPE, MediaType.APPLICATION_JSON.toString());
        } else {
            InputStream is = input.needInputStream();
            ByteArrayOutputStream os = copyInputStream(is);
            newIS = new ByteArrayInputStream(os.toByteArray());

            try {
                os.close();
            } catch (IOException e) {
                Logger.getLogger(FormToJSONParametersFilter.class).error(e.getMessage(), e);
            }
        }

        return new DataBundle(newIS, newModel);
    }

    @SuppressWarnings("unchecked")
    private String checkAndConvertFromArgsToJSON(Map<String, Object> model) {
        Object keyMimetype = model.get(ProcessorConstants.KEY_MIMETYPE);
        if (keyMimetype == null || !keyMimetype.toString().equals(MediaType.MULTIPART_FORM_DATA.toString())) {
            return null;
        }

        Map<String, String> args = (Map<String, String>) model.get(DataBundleProcessorWebscript.KEY_ARGS);

        if (args == null) {
            return null;
        }

        // get data from one field or entire args
        if (args.get(ARGS_PARAMETER_WITH_JSON) != null) {
            return args.get(ARGS_PARAMETER_WITH_JSON);
        }

        JSONObject data = new JSONObject();

        for (Map.Entry<String, String> entry : args.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (value != null) {
                value = value.trim();

                if ((value.startsWith("[")) && (value.endsWith("]"))) {
                    try {
                        JSONArray arr = new JSONArray(value);
                        data.put(key, arr);
                    } catch (JSONException e) {
                        Logger.getLogger(FormToJSONParametersFilter.class).warn("Unable parse/put " + value + " as JSONArray");
                    }
                } else if ((value.startsWith("{")) && (value.endsWith("}"))) {
                    try {
                        JSONObject obj = new JSONObject(value);
                        data.put(key, obj);
                    } catch (JSONException e) {
                        Logger.getLogger(FormToJSONParametersFilter.class).warn("Unable parse/put " + value + " as JSONObject");
                    }
                } else if ((value.equalsIgnoreCase("true")) || (value.equalsIgnoreCase("false"))) {
                    try {
                        Boolean b = Boolean.valueOf(value);
                        data.put(key, b.booleanValue());
                    } catch (Exception e) {
                        Logger.getLogger(FormToJSONParametersFilter.class).warn("Unable parse/put " + value + " as boolean");
                    }
                } else if (value.matches("\\-?\\d+")) {
                    try {
                        Long l = Long.valueOf(value);
                        data.put(key, l.longValue());
                    } catch (Exception e) {
                        Logger.getLogger(FormToJSONParametersFilter.class).warn("Unable parse/put " + value + " as long");
                    }
                } else if (value.matches("\\-?\\d+\\.\\d+")) {
                    try {
                        Double d = Double.valueOf(value);
                        data.put(key, d.doubleValue());
                    } catch (Exception e) {
                        Logger.getLogger(FormToJSONParametersFilter.class).warn("Unable parse/put " + value + " as double");
                    }
                } else {
                    try {
                        data.put(key, value);
                    } catch (JSONException e) {
                        Logger.getLogger(FormToJSONParametersFilter.class).warn("Unable parse/put " + value + " as String");
                    }
                }
            }
        }

        return data.toString();
    }

    private ByteArrayOutputStream copyInputStream(InputStream is) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            IOUtils.copy(is, baos);
        } catch (IOException e) {
            Logger.getLogger(FormToJSONParametersFilter.class).error("Cannot copy input stream", e);
        }

        return baos;
    }
}
