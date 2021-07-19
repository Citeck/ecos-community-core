package ru.citeck.ecos.workflow;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.alfresco.util.ParameterCheck;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
public class EcosBpmAppModelUtils {

    private ContentService contentService;
    private RecordsService recordsService;
    private WorkflowService workflowService;

    public void deployProcess(NodeRef nodeRef) {

        log.debug("Deploy workflow from nodeRef: " + nodeRef);

        ParameterCheck.mandatory("nodeRef", nodeRef);

        ProcessDto processDto = recordsService.getMeta(RecordRef.valueOf(nodeRef.toString()), ProcessDto.class);
        String engineId = processDto.engineId;
        if (StringUtils.isBlank(engineId)) {
            engineId = "flowable";
        }
        ContentReader contentReader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);

        try (InputStream in = contentReader.getReader().getContentInputStream()) {
            workflowService.deployDefinition(engineId, in, contentReader.getMimetype());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.debug("Success deploy of " + nodeRef);
    }

    @Autowired
    public void serServiceRegistry(ServiceRegistry serviceRegistry) {
        contentService = serviceRegistry.getContentService();
        workflowService = serviceRegistry.getWorkflowService();
    }

    @Autowired
    public void setRecordsService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    @Data
    public static class ProcessDto {
        @MetaAtt("ecosbpm:engine?str")
        private String engineId;
    }
}
