package ru.citeck.ecos.icase.activity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import ru.citeck.ecos.records2.RecordRef;

@Data
@JsonIgnoreProperties(ignoreUnknown = true, value = {"definition"})
@ecos.com.fasterxml.jackson210.annotation.JsonIgnoreProperties(ignoreUnknown = true, value = {"definition"})
public class ProcessInstance {
    private String id;
    private RecordRef caseRef;
    private ProcessDefinition definition;
    private ActivityInstance rootActivity;
}
