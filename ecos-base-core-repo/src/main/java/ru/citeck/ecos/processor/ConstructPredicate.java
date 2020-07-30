package ru.citeck.ecos.processor;


import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConstructPredicate extends AbstractDataBundleLine {

    private static final String PREDICATE = "predicate";
    private static final String JSON_DATA = "jsondata";
    private static final String ARGS = "args";

    @Override
    public DataBundle process(DataBundle input) {
        Map<String, Object> oldModel = input.needModel();
        HashMap<String, Object> args = (HashMap<String, Object>) oldModel.get(ARGS);

        Map<String, Object> newModel = new HashMap<>(oldModel);
        newModel.put(PREDICATE, getPredicate(args));
        return new DataBundle(newModel);
    }

    private String getPredicate(HashMap<String, Object> args) {
        String jsonData = (String) args.get(JSON_DATA);
        JsonNode node;
        try {
            node = new ObjectMapper().readTree(jsonData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        JsonNode predicateNode = node.get(PREDICATE);
        return predicateNode != null ? predicateNode.toString() : null;
    }
}
