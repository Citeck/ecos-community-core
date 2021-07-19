package ru.citeck.ecos.action;

import lombok.Data;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records2.evaluator.RecordEvaluatorDto;

import java.util.Map;

@Data
public class ActionModule {
    private String id;
    private String name;
    private String type;
    private String key;
    private String icon;
    private ObjectData config = ObjectData.create();
    private Map<String, Boolean> features;
    private RecordEvaluatorDto evaluator;
    private ObjectData attributes;
}
