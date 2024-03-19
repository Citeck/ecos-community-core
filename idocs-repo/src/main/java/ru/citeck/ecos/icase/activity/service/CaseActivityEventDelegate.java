package ru.citeck.ecos.icase.activity.service;

import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.dto.EventRef;

public interface CaseActivityEventDelegate {

    default void fireEvent(ActivityRef activityRef, String eventType) {
        fireEvent(activityRef, eventType, true);
    }

    void fireEvent(ActivityRef activityRef, String eventType, boolean procDefRequired);

    void fireConcreteEvent(EventRef eventRef);

    boolean checkConditions(EventRef eventRef);

}
