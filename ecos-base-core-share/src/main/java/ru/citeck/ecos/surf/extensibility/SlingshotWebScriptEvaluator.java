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
package ru.citeck.ecos.surf.extensibility;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.springframework.extensions.surf.RequestContext;
import org.springframework.extensions.surf.ServletUtil;
import org.springframework.extensions.surf.exception.ConnectorServiceException;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.connector.Connector;
import org.springframework.extensions.webscripts.connector.Response;

/**
 * SubComponent Evaluator that does its evaluation via request to remote service and processing its response.
 *
 * Parameters, specified via Spring: 
 * - endpoint, 
 * - urlTemplate - string with @{code {placeholders}}, 
 * - accessor - dot-separated string of keys.
 * Parameters, specified via evaluator: 
 * - values (if not specified, it allowes null), 
 * - valueSeparator (default comma), 
 * - all other parameters, used to construct url from urlTemplate.
 *
 * Example:
 * Suppose there is @{code api/property} webscript, that returns value of specified property for specified node.
 *
 * <pre>
 * @{code
 * <bean id="nodeproperty.component.evaluator" class="SlingshotWebScriptEvaluator">
 *    <property name="urlTemplate" value="api/property?nodeRef={nodeRef}&amp;property={property}" />
 *    <property name="accessor" value="value" />
 *    <property name="requiredProperties">
 *       <list>
 *          <value>nodeRef</value>
 *          <value>property</value>
 *       </list>
 *    </property>
 * </bean>
 * }
 *
 * @{code
 * <evaluator type="nodeproperty.component.evaluator">
 *    <params>
 *       <nodeRef>{nodeRef}</nodeRef>
 *       <property>cm:title</property>
 *       <values>on-approval,on-signing,active</values>
 *    </params>
 * </evaluator>
 * }
 *
 * </pre>
 *
 * @author Sergey Tiunov
 *
 */
public class SlingshotWebScriptEvaluator extends AbstractUniversalEvaluator
{
    private String endpoint = "alfresco";
    private String urlTemplate;
    private String accessor;
    private static final String PARAM_VALUES = "values";
    private static final String PARAM_SEPARATOR = "valueSeparator";

    private final Logger logger = Logger.getLogger(getClass());

    @Override
    public boolean evaluateImpl(RequestContext rc, Map<String, String> params) {
        try {
            if (urlTemplate == null) {
                logger.error("mandatory parameter is not specified");
                return false;
            }

            // get connector
            Connector conn = getConnector(rc);
            if (conn == null) {
                return false;
            }

            // extract params
            Map<String, String> paramValues = new HashMap<>(params.size());
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String paramValue = substitute(entry.getValue(), rc.getParameters());
                paramValues.put(entry.getKey(), paramValue);
            }

            // get url
            String url = substitute(urlTemplate, paramValues);
            // submit request
            final Response response = conn.call(url);

            // check response
            if (response.getStatus().getCode() != Status.STATUS_OK) {
                logger.error("Response status isn't OK");
                return false;
            }

            // process response
            return evaluateImplResponse(response, params);
        } catch (Exception e) {
            logger.error("Failed to evaluate the result", e);
        }

        return false;
    }

    private boolean evaluateImplResponse(Response response, Map<String, String> params){
        Object object;
        try {
            object = JSONValue.parseWithException(response.getText());
        } catch (ParseException e) {
            logger.error("Failed to parse web script response using JSON", e);
            return false;
        }

        Object value = getJSONValue(object, accessor);
        String valuesParam = params.get(PARAM_VALUES);

        // no values is used to allow null
        if (valuesParam == null || value == null) {
            return value == null;
        }

        // default separator is comma
        String separator = params.getOrDefault(PARAM_SEPARATOR, "[,]");

        // get list of allowed values
        String[] allowedValues = valuesParam.split(separator);

        // compare
        String stringValue = value.toString();
        for (String allowedValue : allowedValues) {
            if (allowedValue.equals(stringValue)) {
                return true;
            }
        }

        return false;
    }

    private Connector getConnector(RequestContext rc){
        final String userId = rc.getUserId();

        try {
            return rc.getServiceRegistry().getConnectorService().getConnector(
                    endpoint,
                    userId,
                    ServletUtil.getSession()
            );
        } catch (ConnectorServiceException e) {
            logger.error("Can not get connector for endpoint '" + endpoint + "' and user '" + userId + "'", e);
        }

        return null;
    }

    private String substitute(String template, Map<String, String> params) {
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value != null) {
                result = result.replace("{" + key + "}", value);
            }
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    private Object getJSONValue(Object object, String accessor) {
        String[] keys = accessor.split("\\.");
        Object result = object;
        for(int i = 0; i < keys.length; i++) {
            if(result == null) {
                break;
            }
            if(keys[i].isEmpty()) {
                continue;
            }
            if(result instanceof Map) {
                result = ((Map)result).get(keys[i]);
                continue;
            }
            if(result instanceof List) {
                Integer index = null;
                try {
                    index = Integer.parseInt(keys[i]);
                } catch(Exception e) {
                    // do nothing
                }
                if(index != null) {
                    result = ((List)result).get(index);
                    continue;
                }
            }
            result = null;
            break;
        }
        return result;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setUrlTemplate(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    public void setAccessor(String accessor) {
        this.accessor = accessor;
    }

}
