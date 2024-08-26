package ru.citeck.ecos.icase.activity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString(exclude = {"parentTriggerDefinition"})
@EqualsAndHashCode(exclude = {"parentTriggerDefinition"})
@JsonIgnoreProperties(ignoreUnknown = true, value = {"parentTriggerDefinition"})
@ecos.com.fasterxml.jackson210.annotation.JsonIgnoreProperties(ignoreUnknown = true, value = {"parentTriggerDefinition"})
public class SentryDefinition {
    private String id;
    private String event;
    private SourceRef sourceRef;
    private EvaluatorDefinition evaluator;

    private TriggerDefinition parentTriggerDefinition;
}
