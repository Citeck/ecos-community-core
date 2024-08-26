package ru.citeck.ecos.icase.activity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString(exclude = {"parentActivityDefinition"})
@EqualsAndHashCode(exclude = {"parentActivityDefinition"})
@JsonIgnoreProperties(ignoreUnknown = true, value = {"parentActivityDefinition"})
@ecos.com.fasterxml.jackson210.annotation.JsonIgnoreProperties(ignoreUnknown = true, value = {"parentActivityDefinition"})
public class ActivityTransitionDefinition {
    private ActivityState fromState;
    private ActivityState toState;
    private TriggerDefinition trigger;
    private EvaluatorDefinition evaluator;
    private boolean restartRequired;

    private ActivityDefinition parentActivityDefinition;
}
