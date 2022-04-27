package ru.citeck.ecos.workflow;

import kotlin.Unit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.workflow.WorkflowDeployment;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.alfresco.util.ParameterCheck;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.records3.record.dao.query.dto.query.Consistency;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class EcosBpmAppModelUtils {

    private ContentService contentService;
    private RecordsService recordsService;
    private WorkflowService workflowService;

    public void deployProcess(NodeRef nodeRef) {

        log.debug("Deploy workflow from nodeRef: " + nodeRef);

        ParameterCheck.mandatory("nodeRef", nodeRef);

        ProcessDto processDto = recordsService.getAtts(RecordRef.valueOf(nodeRef.toString()), ProcessDto.class);
        if (StringUtils.isBlank(processDto.processId)) {
            throw new RuntimeException("Process ID is blank. NodeRef: " + nodeRef);
        }
        RecordRef existingDeployment = recordsService.queryOne(RecordsQuery.create(b -> {
            b.withSourceId("alfresco/");
            b.withQuery(Predicates.and(
                Predicates.eq("PARENT", nodeRef.toString()),
                Predicates.eq("ecosbpm:deploymentProcDefVersion", processDto.versionLabel)
            ));
            b.withLanguage(PredicateService.LANGUAGE_PREDICATE);
            b.withConsistency(Consistency.TRANSACTIONAL);
            return Unit.INSTANCE;
        }));
        if (existingDeployment != null) {
            log.info("Workflow " + nodeRef + " already deployed. DeploymentRef: " + existingDeployment.getId());
            return;
        }

        String engineId = processDto.engineId;
        if (StringUtils.isBlank(engineId)) {
            engineId = "flowable";
        }
        ContentReader contentReader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);

        try (InputStream in = contentReader.getReader().getContentInputStream()) {

            WorkflowDeployment deployment = workflowService.deployDefinition(engineId, in, contentReader.getMimetype());

            Map<String, Object> deploymentProps = new LinkedHashMap<>();
            deploymentProps.put("type", "ecosbpm:deploymentInfo");
            deploymentProps.put("_parent", nodeRef.toString());
            deploymentProps.put("_parentAtt", "ecosbpm:deployments");
            deploymentProps.put("ecosbpm:deploymentProcDefId", processDto.processId);
            deploymentProps.put("ecosbpm:deploymentProcDefVersion", processDto.versionLabel);
            deploymentProps.put("ecosbpm:deploymentEngine", engineId);
            deploymentProps.put("ecosbpm:deploymentVersion", deployment.getDefinition().getVersion());

            RecordRef deploymentRef = recordsService.create("alfresco/", deploymentProps);
            log.info("Deployment was created: " + deploymentRef.getId()
                + " with props: " + Json.getMapper().toString(deploymentProps));

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
        @AttName("ecosbpm:engine?str")
        private String engineId;
        @AttName("ecosbpm:processId?str")
        private String processId;
        @AttName("cm:versionLabel!'1.0'")
        private String versionLabel;
    }
}
