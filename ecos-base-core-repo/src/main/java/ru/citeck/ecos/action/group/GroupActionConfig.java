package ru.citeck.ecos.action.group;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import ru.citeck.ecos.utils.JSONUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class GroupActionConfig {

    private ObjectNode params;
    private int batchSize = 1;
    private boolean async = false;

    private int maxResults = 100;
    private int errorsLimit = 1_000;
    private int elementsLimit = 10_000;

    private long timeout = TimeUnit.HOURS.toMillis(5);
    private String actionId;
    private boolean readOnly = false;

    public GroupActionConfig() {
    }

    public GroupActionConfig(GroupActionConfig other) {
        this.params = other.getParams().deepCopy();
        this.batchSize = other.batchSize;
        this.actionId = other.actionId;
    }

    public ObjectNode getParams() {
        if (params == null) {
            params = JsonNodeFactory.instance.objectNode();
        }
        return params;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public int getErrorsLimit() {
        return errorsLimit;
    }

    public void setErrorsLimit(int errorsLimit) {
        this.errorsLimit = errorsLimit;
    }

    public void setParams(ObjectNode params) {
        this.params = params;
    }

    public void setParam(String key, String value) {
        getParams().set(key, TextNode.valueOf(value));
    }

    public void setParam(String key, Boolean value) {
        getParams().set(key, BooleanNode.valueOf(value));
    }

    public void setPojoParam(String key, Object pojo) {
        getParams().set(key, JsonNodeFactory.instance.pojoNode(pojo));
    }

    @JsonIgnore
    public String getStrParam(String key) {
        JsonNode jsonNode = getParams().get(key);
        if (jsonNode == null || jsonNode instanceof NullNode) {
            return null;
        }
        return jsonNode.asText();
    }

    @JsonIgnore
    public <T> T getPojoParam(String key, Class<T> clazz, ObjectMapper mapper) {
        return JSONUtils.getPojo(getParams().get(key), clazz, mapper);
    }

    public boolean getBoolParam(String key) {
        return getBoolParam(key, false);
    }

    public boolean getBoolParam(String key, boolean def) {
        JsonNode jsonNode = getParams().get(key);
        if (jsonNode == null || jsonNode instanceof NullNode) {
            return def;
        }
        return jsonNode.asBoolean(def);
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public int getElementsLimit() {
        return elementsLimit;
    }

    public void setElementsLimit(int elementsLimit) {
        this.elementsLimit = elementsLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GroupActionConfig that = (GroupActionConfig) o;

        return Objects.equals(actionId, that.actionId) &&
            Objects.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(params);
        result = 31 * result + Objects.hashCode(actionId);
        return result;
    }

    @Override
    public String toString() {
        return "GroupActionConfig{" +
            "params=" + params +
            ", actionId=" + actionId +
            ", batchSize=" + batchSize +
            ", async=" + async +
            ", maxResults=" + maxResults +
            ", errorsLimit=" + errorsLimit +
            ", elementsLimit=" + elementsLimit +
            ", timeout=" + timeout +
            ", readOnly=" + readOnly +
            '}';
    }
}
