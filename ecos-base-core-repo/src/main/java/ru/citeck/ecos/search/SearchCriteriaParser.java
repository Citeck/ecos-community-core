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
package ru.citeck.ecos.search;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;

import java.util.Iterator;
import java.util.Map;

/**
 * @author Anton Fateev <anton.fateev@citeck.ru>
 */
public class SearchCriteriaParser {

    static final String SKIP = "skipCount";

    static final String LIMIT = "maxItems";

    static final String FIELD_KEY = "field";

    static final String SEPARATOR = "_";

    static final String PREDICATE_KEY = "predicate";

    static final String VALUE_KEY = "value";

    static final String SORT_BY = "sortBy";

    static final String ATTRIBUTE = "attribute";

    static final String ORDER = "order";

    private final int fieldIndexPos = (FIELD_KEY + SEPARATOR).length();

    private SearchCriteriaFactory criteriaFactory;

    public SearchCriteria parse(Object something) {
        if (something instanceof JSONObject) {
            return parse((JSONObject) something);
        }
        if (something instanceof Map) {

            something = Json.getMapper().toString(something);

        } else if (something instanceof ObjectNode) {

            something = something.toString();

        } else if (something instanceof ObjectData || something instanceof DataValue) {

            something = something.toString();

        } else if (something instanceof TextNode) {

            something = ((TextNode) something).asText();
        }
        if (something instanceof String) {

            JSONObject jsonObject;
            try {
                jsonObject = new JSONObject(something.toString());
            } catch (JSONException e) {
                throw new IllegalArgumentException("Wrong json string " + something);
            }
            return parse(jsonObject);
        }
        throw new IllegalStateException("Can not build search criteria from class " + something.getClass().toString());
    }

    public SearchCriteria parse(JSONObject criteria) {
        SearchCriteria searchCriteria = criteriaFactory.createSearchCriteria();
        Iterator criteriaKeys = criteria.sortedKeys();
        while (criteriaKeys.hasNext()) {
            String name = (String) criteriaKeys.next();
            try {
                if (name.equals(SKIP)) {
                    searchCriteria.setSkip(criteria.getInt(name));
                } else if (name.equals(LIMIT)) {
                    searchCriteria.setLimit(criteria.getInt(name));
                } else if (name.startsWith(FIELD_KEY)) {
                    addCriteriaTriplet(searchCriteria, criteria, name);
                } else if (name.equals(SORT_BY)) {
                    addCriteriaSort(searchCriteria, criteria, name);
                } else if (name.equals("triplets")) {
                    JSONArray triplets = criteria.getJSONArray("triplets");
                    for (int i = 0; i < triplets.length(); i++) {
                        JSONObject obj = triplets.getJSONObject(i);
                        searchCriteria.addCriteriaTriplet(
                            obj.getString("field"),
                            obj.getString("predicate"),
                            obj.getString("value")
                        );
                    }
                }
            } catch (JSONException ex) {
                throw new IllegalArgumentException(ex.getMessage());
            }
        }
        return searchCriteria;
    }

    private void addCriteriaTriplet(SearchCriteria searchCriteria, JSONObject criteria, String name)
        throws JSONException {
        String field = criteria.getString(name);
        Integer criteriaIndex = Integer.valueOf(name.substring(fieldIndexPos));
        String predicate;
        String value;
        try {
            predicate = criteria.getString(PREDICATE_KEY + SEPARATOR + criteriaIndex);
            value = criteria.getString(VALUE_KEY + SEPARATOR + criteriaIndex);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Can not get predicate or value for field with index " + criteriaIndex);
        }
        searchCriteria.addCriteriaTriplet(field, predicate, value);
    }

    private void addCriteriaSort(SearchCriteria searchCriteria, JSONObject criteria, String name) throws JSONException {
        JSONArray sortParams = criteria.getJSONArray(name);
        for (int i = 0, ii = sortParams.length(); i < ii; i++) {
            JSONObject sortParam = sortParams.getJSONObject(i);
            String field = sortParam.getString(ATTRIBUTE);

            String order = null;
            if(sortParam.has(ORDER)) {
                order = sortParam.getString(ORDER);
            }

            if (order != null) {
                searchCriteria.addSort(field, order);
            } else {
                searchCriteria.addSort(field, SortOrder.ASCENDING);
            }
        }
    }

    public void setCriteriaFactory(SearchCriteriaFactory criteriaFactory) {
        this.criteriaFactory = criteriaFactory;
    }
}
