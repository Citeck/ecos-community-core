package ru.citeck.ecos.workflow.listeners;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.MLPropertyInterceptor;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.icase.CaseStatusService;
import ru.citeck.ecos.model.HistoryModel;
import ru.citeck.ecos.model.lib.status.dto.StatusDef;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class TaskDataListenerUtils {

    private static final String TITLE_DELIMITER = "|";
    private static final Locale LOCAL_RU = Locale.forLanguageTag("ru");

    private final RecordsService recordsService;
    private final NodeService nodeService;
    private final CaseStatusService caseStatusService;

    @Autowired
    public TaskDataListenerUtils(RecordsService recordsService, NodeService nodeService,
                                 CaseStatusService caseStatusService) {
        this.recordsService = recordsService;
        this.nodeService = nodeService;
        this.caseStatusService = caseStatusService;
    }

    public void fillDocumentData(NodeRef documentRef, Map<QName, Serializable> eventProperties) {
        if (documentRef == null || !nodeService.exists(documentRef)) {
            return;
        }

        String type = recordsService.getAtt(RecordRef.valueOf(documentRef.toString()), "TYPE").asText();
        eventProperties.put(HistoryModel.PROP_DOC_TYPE, type);

        fillStatusData(documentRef, eventProperties);
    }

    private void fillStatusData(NodeRef documentRef, Map<QName, Serializable> eventProperties) {
        NodeRef statusRef = caseStatusService.getStatusRef(documentRef);
        if (caseStatusService.isAlfRef(statusRef)) {
            fillAlfrescoStatusData(eventProperties, statusRef);
        } else {
            StatusDef statusDef = caseStatusService.getStatusDef(documentRef, statusRef.getId());
            fillEcosStatusData(eventProperties, statusDef);
        }
    }

    private void fillEcosStatusData(Map<QName, Serializable> eventProperties, StatusDef statusDef) {
        eventProperties.put(HistoryModel.PROP_DOC_STATUS_NAME, statusDef.getId());
        ru.citeck.ecos.commons.data.MLText name = statusDef.getName();
        String titleStr = mlTextToLine(name.get(Locale.ENGLISH), name.get(LOCAL_RU));
        eventProperties.put(HistoryModel.PROP_DOC_STATUS_TITLE, titleStr);
    }

    private void fillAlfrescoStatusData(Map<QName, Serializable> eventProperties, NodeRef statusRef) {
        MLText title = getMlTitle(statusRef);
        String name = (String) nodeService.getProperty(statusRef, ContentModel.PROP_NAME);
        eventProperties.put(HistoryModel.PROP_DOC_STATUS_NAME, name);

        String titleStr = null;
        if (title != null) {
            titleStr = mlTextToLine(title.get(Locale.ENGLISH), title.get(LOCAL_RU));
        }
        eventProperties.put(HistoryModel.PROP_DOC_STATUS_TITLE, titleStr);
    }

    private MLText getMlTitle(NodeRef statusRef) {
        MLPropertyInterceptor.setMLAware(true);
        try {
            return (MLText) nodeService.getProperty(statusRef, ContentModel.PROP_TITLE);
        } finally {
            MLPropertyInterceptor.setMLAware(false);
        }
    }

    private String mlTextToLine(String... text) {
        return Stream.of(text)
            .filter(StringUtils::isNoneBlank)
            .collect(Collectors.joining(TITLE_DELIMITER));
    }
}
