package ru.citeck.ecos.action.group.output;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.alfresco.service.cmr.repository.NodeRef;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.utils.ReflectUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExportOutputActionsRegistry {

    private final Map<String, ExportOutputAction<Object>> actions = new ConcurrentHashMap<>();

    public void validate(String type, ObjectData config) {
       ExportOutputAction<Object> action = needAction(type);
       action.validate(prepareConfig(action, config));
    }

    public void execute(String type, ObjectData config, NodeRef outputFile) {
        ExportOutputAction<Object> action = needAction(type);
        action.execute(outputFile, prepareConfig(action, config));
    }

    private Object prepareConfig(ExportOutputAction<Object> action, ObjectData config) {
        Class<?> configType = ReflectUtils.getGenericArg(action.getClass(), ExportOutputAction.class);
        if (configType == null) {
            throw new RuntimeException("ConfigType is null for " + action);
        }
        return Json.getMapper().convert(config, configType);
    }

    @NotNull
    private ExportOutputAction<Object> needAction(String type) {
        ExportOutputAction<Object> result = actions.get(type);
        if (result == null) {
            throw new RuntimeException("Action with type " + type + " not found");
        }
        return result;
    }

    @Autowired
    public void setActions(List<ExportOutputAction<?>> actions) {
        for (ExportOutputAction<?> action : actions) {
            @SuppressWarnings("unchecked")
            ExportOutputAction<Object> objAction = (ExportOutputAction<Object>) action;
            this.actions.put(action.getType(), objAction);
        }
    }
}
