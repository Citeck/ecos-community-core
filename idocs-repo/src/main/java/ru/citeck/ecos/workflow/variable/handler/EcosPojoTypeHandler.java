package ru.citeck.ecos.workflow.variable.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.activiti.engine.ActivitiException;
import org.activiti.engine.impl.variable.ValueFields;
import org.activiti.engine.impl.variable.VariableType;
import org.alfresco.error.AlfrescoRuntimeException;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.workflow.variable.json.JsonObjectMapper;
import ru.citeck.ecos.workflow.variable.type.EcosPojoType;
import ru.citeck.ecos.workflow.variable.type.PojoVariableWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Slf4j
public class EcosPojoTypeHandler<T> implements VariableType {

    private static final int MAX_TEXT_LENGTH = 4000;
    private static final String TYPE_NAME = "ecos-pojo";
    private static final Charset STRING_CHARSET = StandardCharsets.UTF_8;
    private static final byte[] STRING_BYTES_MARKER = {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF};

    private VariableTypeUtils utils;
    private JsonObjectMapper objectMapper;

    @Override
    public void setValue(Object value, ValueFields valueFields) {
        valueFields.setCachedValue(value);
        String textValue = null;
        if (value != null) {
            textValue = toText((T) value);
        }
        if (textValue == null) {
            valueFields.setTextValue(null);
            valueFields.setBytes(null);
        } else if (textValue.length() < MAX_TEXT_LENGTH) {
            valueFields.setTextValue(textValue);
            valueFields.setBytes(null);
        } else {
            int length = STRING_BYTES_MARKER.length + textValue.length();
            ByteArrayOutputStream s = new ByteArrayOutputStream(length);
            try {
                s.write(STRING_BYTES_MARKER);
                s.write(textValue.getBytes(STRING_CHARSET));
                valueFields.setBytes(s.toByteArray());
            } catch (IOException e) {
                throw new AlfrescoRuntimeException("Error!", e);
            }
        }
    }

    @Override
    public Object getValue(ValueFields valueFields) {
        Object cached = valueFields.getCachedValue();
        if (cached != null) {
            return cached;
        }
        T result = null;
        String text = valueFields.getTextValue();
        if (StringUtils.isNotBlank(text)) {
            result = toObject(text);
        } else if (ArrayUtils.isNotEmpty(valueFields.getBytes())) {
            result = deserialize(valueFields.getBytes(), valueFields.getName());
        }
        valueFields.setCachedValue(result);
        return result;
    }

    private boolean isStringBytes(byte[] bytes) {
        if (bytes.length < STRING_BYTES_MARKER.length) {
            return false;
        }
        for (int i = 0; i < STRING_BYTES_MARKER.length; i++) {
            if (bytes[i] != STRING_BYTES_MARKER[i]) {
                return false;
            }
        }
        return true;
    }

    private T deserialize(byte[] bytes, String variableName) {
        if (isStringBytes(bytes)) {
            try {
                int offset = STRING_BYTES_MARKER.length;
                int length = bytes.length - offset;
                String textValue = new String(bytes, offset, length, STRING_CHARSET);
                return toObject(textValue);
            } catch (Exception e) {
                log.debug("Error while parsing string bytes value. Let's try to deserialize", e);
            }
        }

        try {
            return utils.deserialize(bytes);
        } catch (IOException | ClassNotFoundException e) {
            throw new ActivitiException("Couldn't deserialize object in variable '" + variableName + "'", e);
        }
    }

    private T toObject(String text) {
        T result;
        try {
            @SuppressWarnings("unchecked")
            PojoVariableWrapper<T> wrapper = objectMapper.readValue(text, PojoVariableWrapper.class);
            result = wrapper.variable;
        } catch(IOException e) {
            throw new AlfrescoRuntimeException("Error!", e);
        }
        return result;
    }

    private String toText(T object) {
        PojoVariableWrapper wrapper = new PojoVariableWrapper<>(object);
        try {
            return objectMapper.writeValueAsString(wrapper);
        } catch (JsonProcessingException e) {
            throw new AlfrescoRuntimeException("Error!", e);
        }
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public boolean isCachable() {
        return true;
    }

    @Override
    public boolean isAbleToStore(Object value) {
        return value != null && (EcosPojoType.class.isAssignableFrom(value.getClass())
                                 || objectMapper.hasMixIn(value.getClass()));
    }

    @Autowired
    public void setUtils(VariableTypeUtils utils) {
        this.utils = utils;
    }

    public void setObjectMapper(JsonObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
}
