package ru.citeck.ecos.eapps;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler;
import ru.citeck.ecos.model.EcosBpmModel;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.workflow.EcosBpmAppModelUtils;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class WorkflowArtifactHandler implements EcosArtifactHandler<WorkflowArtifact> {

    private static final NodeRef ROOT = new NodeRef("workspace://SpacesStore/ecos-bpm-process-root");
    private static final NodeRef CATEGORY = new NodeRef("workspace://SpacesStore/cat-doc-kind-ecos-bpm-default");

    private final NodeService nodeService;
    private final SearchService searchService;
    private final ContentService contentService;
    private EcosBpmAppModelUtils bpmAppUtils;
    private Consumer<WorkflowArtifact> listener;

    @Autowired
    public WorkflowArtifactHandler(ServiceRegistry serviceRegistry) {
        this.nodeService = serviceRegistry.getNodeService();
        this.searchService = serviceRegistry.getSearchService();
        this.contentService = serviceRegistry.getContentService();
    }

    @Override
    public void deployArtifact(@NotNull WorkflowArtifact module) {

        String[] engineAndId = module.getId().split("\\$");
        String processId = engineAndId[1];
        String engineId = engineAndId[0];

        String localName = module.getId().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        QName assocQName = QName.createQNameWithValidLocalName(NamespaceService.SYSTEM_MODEL_1_0_URI, localName);

        log.info("Workflow publishing: " + module.getId());

        Map<QName, Serializable> props = new HashMap<>();

        props.put(ContentModel.PROP_NAME, localName);
        props.put(EcosBpmModel.PROP_ENGINE, engineId);
        props.put(EcosBpmModel.PROP_CATEGORY, CATEGORY);
        props.put(EcosBpmModel.PROP_PROCESS_ID, processId);

        NodeRef processNode = FTSQuery.create()
                .type(EcosBpmModel.TYPE_PROCESS_MODEL).and()
                .exact(EcosBpmModel.PROP_PROCESS_ID, processId).and()
                .exact(EcosBpmModel.PROP_ENGINE, engineId)
                .transactional()
                .queryOne(searchService)
                .orElse(null);

        if (processNode == null) {

            processNode = nodeService.createNode(
                    ROOT,
                    ContentModel.ASSOC_CONTAINS,
                    assocQName,
                    EcosBpmModel.TYPE_PROCESS_MODEL,
                    props
            ).getChildRef();

        } else {

            nodeService.addProperties(processNode, props);
        }

        QName prop = ContentModel.PROP_CONTENT;

        ContentWriter writer = contentService.getWriter(processNode, prop, true);
        writer.putContent(new ByteArrayInputStream(module.getXmlData()));

        NodeRef finalNode = processNode;
        bpmAppUtils.deployProcess(finalNode);
    }

    @Override
    public void listenChanges(@NotNull Consumer<WorkflowArtifact> consumer) {
        this.listener = consumer;
    }

    public void fireWorkflowChanged(WorkflowArtifact artifact) {
        listener.accept(artifact);
    }

    @Override
    public void deleteArtifact(@NotNull String s) {
    }

    @Autowired
    public void setBpmAppUtils(EcosBpmAppModelUtils bpmAppUtils) {
        this.bpmAppUtils = bpmAppUtils;
    }

    @NotNull
    @Override
    public String getArtifactType() {
        return "workflow";
    }
}
