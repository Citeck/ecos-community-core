package ru.citeck.ecos.icase.activity.service.eproc.listeners.activity;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.icase.CaseStatusService;
import ru.citeck.ecos.icase.activity.dto.ActivityDefinition;
import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.service.eproc.EProcActivityService;
import ru.citeck.ecos.icase.activity.service.eproc.EProcCaseActivityListenerManager;
import ru.citeck.ecos.icase.activity.service.eproc.EProcUtils;
import ru.citeck.ecos.icase.activity.service.eproc.importer.parser.CmmnDefinitionConstants;
import ru.citeck.ecos.icase.activity.service.eproc.listeners.BeforeStartedActivityListener;
import ru.citeck.ecos.model.IdocsModel;
import ru.citeck.ecos.records.RecordsUtils;

import javax.annotation.PostConstruct;

@Slf4j
@Component
public class CaseSetStageStatusListener implements BeforeStartedActivityListener {

    private EProcCaseActivityListenerManager manager;
    private EProcActivityService eprocActivityService;
    private CaseStatusService caseStatusService;
    private NodeService nodeService;

    @Autowired
    public CaseSetStageStatusListener(EProcCaseActivityListenerManager manager,
                                      EProcActivityService eProcActivityService,
                                      CaseStatusService caseStatusService,
                                      NodeService nodeService) {
        this.manager = manager;
        this.eprocActivityService = eProcActivityService;
        this.caseStatusService = caseStatusService;
        this.nodeService = nodeService;
    }

    @PostConstruct
    public void init() {
        manager.subscribeBeforeStarted(this);
    }

    @Override
    public void beforeStartedActivity(ActivityRef activityRef) {
        ActivityDefinition definition = eprocActivityService.getActivityDefinition(activityRef);
        if (!EProcUtils.isStage(definition)) {
            return;
        }

        NodeRef documentNodeRef = RecordsUtils.toNodeRef(activityRef.getProcessId());

        String documentStatus = EProcUtils.getDefAttribute(definition, CmmnDefinitionConstants.DOCUMENT_STATUS);
        if (StringUtils.isNotEmpty(documentStatus)) {
            nodeService.setProperty(documentNodeRef, IdocsModel.PROP_DOCUMENT_STATUS, documentStatus);
        }

        String statusName = EProcUtils.getDefAttribute(definition, CmmnDefinitionConstants.CASE_STATUS);
        if (StringUtils.isNotEmpty(statusName)) {
            NodeRef caseStatusRef = caseStatusService.getStatusByName(statusName, documentNodeRef);
            if (caseStatusRef != null) {
                caseStatusService.setStatus(documentNodeRef, caseStatusRef);
            } else {
                log.error("Can not find status by name '" + statusName + "'. ActivityRef='" + activityRef + "'");
            }
        }
    }
}
