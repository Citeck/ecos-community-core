package ru.citeck.ecos.icase.activity.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.dto.CaseServiceType;
import ru.citeck.ecos.icase.activity.dto.EventRef;
import ru.citeck.ecos.icase.activity.service.alfresco.AlfrescoCaseActivityEventDelegate;
import ru.citeck.ecos.icase.activity.service.eproc.EProcCaseActivityEventDelegate;

@Service("caseActivityEventService")
public class CaseActivityEventServiceImpl implements CaseActivityEventService {

    private AlfrescoCaseActivityEventDelegate alfDelegate;
    private EProcCaseActivityEventDelegate eprocDelegate;

    @Override
    public void fireEvent(ActivityRef activityRef, String eventType, boolean procDefRequired) {
        if (isAlfrescoCase(activityRef)) {
            alfDelegate.fireEvent(activityRef, eventType, procDefRequired);
        } else {
            eprocDelegate.fireEvent(activityRef, eventType, procDefRequired);
        }
    }

    @Override
    public void fireConcreteEvent(EventRef eventRef) {
        if (isAlfrescoCase(eventRef)) {
            alfDelegate.fireConcreteEvent(eventRef);
        } else {
            eprocDelegate.fireConcreteEvent(eventRef);
        }
    }

    @Override
    public boolean checkConditions(EventRef eventRef) {
        if (isAlfrescoCase(eventRef)) {
            return alfDelegate.checkConditions(eventRef);
        } else {
            return eprocDelegate.checkConditions(eventRef);
        }
    }

    private boolean isAlfrescoCase(ActivityRef activityRef) {
        return activityRef.getCaseServiceType() == CaseServiceType.ALFRESCO;
    }

    private boolean isAlfrescoCase(EventRef eventRef) {
        return eventRef.getCaseServiceType() == CaseServiceType.ALFRESCO;
    }

    @Autowired
    public void setAlfDelegate(AlfrescoCaseActivityEventDelegate alfDelegate) {
        this.alfDelegate = alfDelegate;
    }

    @Autowired
    public void setEprocDelegate(EProcCaseActivityEventDelegate eprocDelegate) {
        this.eprocDelegate = eprocDelegate;
    }
}
