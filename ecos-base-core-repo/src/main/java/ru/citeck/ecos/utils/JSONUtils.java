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
package ru.citeck.ecos.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.POJONode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JSONUtils {

    public static Object convertJSON(Object obj) {
        if(obj == null) {
            return null;
        }
        if(obj instanceof org.json.simple.JSONObject) {
            return convertJSON((org.json.simple.JSONObject) obj);
        }
        if(obj instanceof org.json.simple.JSONArray) {
            return convertJSON((org.json.simple.JSONArray) obj);
        }
        if(obj instanceof org.json.JSONObject) {
            return convertJSON((org.json.JSONObject) obj);
        }
        if(obj instanceof org.json.JSONArray) {
            return convertJSON((org.json.JSONArray) obj);
        }
        if(obj == org.json.JSONObject.NULL) {
            return null;
        }
        return obj;
    }

    public static List<Object> convertJSON(org.json.JSONArray jsonArray) {
        List<Object> converted = new ArrayList<>(jsonArray.length());
        for(int i = 0, ii = jsonArray.length(); i < ii; i++) {
            converted.add(i, convertJSON(jsonArray.opt(i)));
        }
        return converted;
    }

    public static Map<String, Object> convertJSON(org.json.JSONObject jsonObject) {
        Map<String, Object> converted = new HashMap<>(jsonObject.length());
        for(String name : org.json.JSONObject.getNames(jsonObject)) {
            converted.put(name, convertJSON(jsonObject.opt(name)));
        }
        return converted;
    }

    public static List<Object> convertJSON(org.json.simple.JSONArray jsonArray) {
        List<Object> converted = new ArrayList<>(jsonArray.size());
        for(Object child : jsonArray) {
            converted.add(convertJSON(child));
        }
        return converted;
    }

    public static Map<String, Object> convertJSON(org.json.simple.JSONObject jsonObject) {
        Map<?, ?> jsonMap = (Map<?,?>) jsonObject;
        Map<String, Object> converted = new HashMap<>(jsonObject.size());
        for (Map.Entry<?, ?> entry : jsonMap.entrySet()) {
            Object key = entry.getKey();
            converted.put(
                    key != null ? key.toString() : null, 
                    convertJSON(entry.getValue())
            );
        }
        return converted;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static Object jsonCopy(Object obj) {
        if (obj instanceof Map) {
            Map<?,?> map = (Map<?,?>) obj;
            Map copy = new HashMap(map.size());
            for (Map.Entry entry : map.entrySet()) {
                Object key = entry.getKey();
                copy.put(key, jsonCopy(entry.getValue()));
            }
            return copy;
        }

        if (obj instanceof List) {
            List list = (List) obj;
            List copy = new ArrayList(list.size());
            for (Object child : list) {
                copy.add(jsonCopy(child));
            }
            return copy;
        }
        return obj;
    }

    public static Object parseJSON(String jsonString) {
        Object jsonObject = org.json.simple.JSONValue.parse(jsonString);
        return convertJSON(jsonObject);
    }

    public static Object prepareToSerialize(Object x) {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bout);
            out.writeObject(x);
            out.close();
            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(bin);
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    public static <T> T getPojo(JsonNode jsonNode, Class<T> clazz, ObjectMapper mapper) {
        if (jsonNode == null || jsonNode instanceof NullNode) {
            return null;
        }
        if (jsonNode instanceof POJONode) {
            Object pojo = ((POJONode) jsonNode).getPojo();
            if (pojo != null && clazz.isAssignableFrom(pojo.getClass())) {
                return (T) pojo;
            }
        }
        try {
            return mapper.treeToValue(jsonNode, clazz);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
