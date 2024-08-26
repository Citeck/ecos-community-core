package ru.citeck.ecos.icase.activity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import ru.citeck.ecos.commons.data.ObjectData;

import java.util.Date;
import java.util.List;

@Data
@ToString(exclude = {"parentInstance"})
@EqualsAndHashCode(exclude = {"parentInstance"})
@JsonIgnoreProperties(ignoreUnknown = true, value = {"definition", "parentInstance"})
@ecos.com.fasterxml.jackson210.annotation.JsonIgnoreProperties(ignoreUnknown = true, value = {"definition", "parentInstance"})
public class ActivityInstance {
    private String id;
    private ActivityDefinition definition;
    private ActivityState state;
    private Date activated;
    private Date terminated;
    private List<ActivityInstance> activities;
    private ObjectData variables;

    private ActivityInstance parentInstance;
}

