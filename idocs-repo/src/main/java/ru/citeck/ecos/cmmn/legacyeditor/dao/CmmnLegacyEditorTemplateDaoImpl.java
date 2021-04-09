package ru.citeck.ecos.cmmn.legacyeditor.dao;

import lombok.RequiredArgsConstructor;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.ICaseModel;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.search.ftsquery.FTSQuery;

import java.io.Serializable;
import java.sql.Date;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class CmmnLegacyEditorTemplateDaoImpl implements CmmnLegacyEditorTemplateDao {

    private static final NodeRef ROOT_REF = new NodeRef("workspace://SpacesStore/cmmn-legacy-editor-root");
    private static final int MAX_TEMP_NODES_COUNT = 10;

    private final NodeService nodeService;
    private final SearchService searchService;

    private BehaviourFilter behaviourFilter;

    @NotNull
    @Override
    public NodeRef createTempCase(@NotNull RecordRef templateRef) {

        String currentUser = AuthenticationUtil.getFullyAuthenticatedUser();

        List<NodeRef> existingTempNodes = FTSQuery.create()
            .aspect(ICaseModel.ASPECT_LEGACY_EDITOR_TEMPLATE)
            .and()
            .exact(ICaseModel.PROP_LEGACY_EDITOR_TEMPLATE_OWNER, currentUser)
            .addSort(ICaseModel.PROP_LEGACY_EDITOR_LAST_UPDATED, true)
            .transactional()
            .maxItems(MAX_TEMP_NODES_COUNT + 1)
            .query(searchService);

        if (existingTempNodes.size() >= MAX_TEMP_NODES_COUNT) {
            deleteTempCaseNode(existingTempNodes.get(0));
        }

        QName assocName = QName.createQName(NamespaceService.SYSTEM_MODEL_1_0_URI, UUID.randomUUID().toString());

        NodeRef tempNode = nodeService.createNode(
            ROOT_REF,
            ContentModel.ASSOC_CHILDREN,
            assocName,
            ContentModel.TYPE_BASE
        ).getChildRef();

        Map<QName, Serializable> props = new HashMap<>();
        props.put(ICaseModel.PROP_LEGACY_EDITOR_LAST_UPDATED, Date.from(Instant.now()));
        props.put(ICaseModel.PROP_LEGACY_EDITOR_ORIGINAL_TEMPLATE_REF, templateRef.toString());
        props.put(ICaseModel.PROP_LEGACY_EDITOR_TEMPLATE_OWNER, currentUser);

        nodeService.addAspect(tempNode, ICaseModel.ASPECT_LEGACY_EDITOR_TEMPLATE, props);

        return tempNode;
    }

    @NotNull
    @Override
    public NodeRef createTemplateNode(@NotNull NodeRef tempCaseNode) {

        QName assocName = QName.createQName(NamespaceService.SYSTEM_MODEL_1_0_URI, UUID.randomUUID().toString());

        return nodeService.createNode(
            tempCaseNode,
            ICaseModel.ASSOC_LEGACY_EDITOR_TEMPLATE_NODE_ASSOC,
            assocName,
            ICaseModel.TYPE_CASE_TEMPLATE
        ).getChildRef();
    }

    @Override
    public void updateTempCaseNode(@NotNull NodeRef tempNode) {
        nodeService.setProperty(tempNode, ICaseModel.PROP_LEGACY_EDITOR_LAST_UPDATED, Date.from(Instant.now()));
    }

    @NotNull
    @Override
    public RecordRef getOriginalTemplateRef(@NotNull NodeRef nodeRef) {

        String templateRefStr = (String) nodeService.getProperty(
            nodeRef,
            ICaseModel.PROP_LEGACY_EDITOR_ORIGINAL_TEMPLATE_REF
        );

        return RecordRef.valueOf(templateRefStr);
    }

    @Override
    public void deleteTempCaseNode(@NotNull NodeRef nodeRef) {
        behaviourFilter.disableBehaviour();
        try {
            nodeService.deleteNode(nodeRef);
        } finally {
            behaviourFilter.enableBehaviour();
        }
    }

    @Autowired
    @Qualifier("policyBehaviourFilter")
    public void setBehaviourFilter(BehaviourFilter behaviourFilter) {
        this.behaviourFilter = behaviourFilter;
    }
}
