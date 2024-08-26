package ru.citeck.ecos.icase.activity.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.StringUtils;

public enum ActivityType {

    ROOT("root"),
    STAGE("stage"),
    USER_TASK("userTask"),
    PROCESS_TASK("processTask"),
    ACTION("action"),
    USER_EVENT_LISTENER("userEventListener"),
    TIMER("timer");


    private final String value;

    ActivityType(String value) {
        this.value = value;
    }

    @JsonValue
    @ecos.com.fasterxml.jackson210.annotation.JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    @ecos.com.fasterxml.jackson210.annotation.JsonCreator
    public ActivityType getByValue(String value) {
        for (ActivityType activityType : values()) {
            if (StringUtils.equals(activityType.value, value)) {
                return activityType;
            }
        }
        throw new IllegalArgumentException("Enum element with value='" + value + "' not exist");
    }
}
