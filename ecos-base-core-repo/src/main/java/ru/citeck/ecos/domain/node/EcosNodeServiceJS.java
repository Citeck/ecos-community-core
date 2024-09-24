package ru.citeck.ecos.domain.node;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;
import ru.citeck.ecos.utils.JavaScriptImplUtils;

import java.util.List;
import java.util.stream.Collectors;

public class EcosNodeServiceJS extends AlfrescoScopableProcessorExtension {

    private EcosNodeService ecosNodeService;

    public List<ChildAssociationRef> getChildAssocsLimited(Object nodeRef,
                                                           String typeQNamePattern,
                                                           String qNamePattern,
                                                           String childName,
                                                           int maxResults,
                                                           boolean preload) {

        NodeRef nRef = JavaScriptImplUtils.getNodeRef(nodeRef);
        QName typeQName = createQNameFromPattern(typeQNamePattern);
        QName qName = createQNameFromPattern(qNamePattern);

        return AuthenticationUtil.runAsSystem(() -> ecosNodeService.getChildAssocsLimited(nRef, typeQName, qName, childName, maxResults, preload));
    }

    public List<NodeRef> getChildAssocsNodeRefsLimited(Object nodeRef,
                                                           String typeQNamePattern,
                                                           String qNamePattern,
                                                           String childName,
                                                           int maxResults,
                                                           boolean preload) {

        List<ChildAssociationRef> assocRefs = getChildAssocsLimited(nodeRef, typeQNamePattern, qNamePattern, childName, maxResults, preload);
        List<NodeRef> nodeRefs = assocRefs.stream().map(ChildAssociationRef::getChildRef).collect(Collectors.toList());

        return nodeRefs;
    }
    public List<AssociationRef> getSourceAssocsLimited(Object nodeRef,
                                                String qName,
                                                int skipCount,
                                                int maxItems) {

        NodeRef nRef = JavaScriptImplUtils.getNodeRef(nodeRef);
        QName typeQName = QName.createQName(qName);

        return AuthenticationUtil.runAsSystem(() -> ecosNodeService.getSourceAssocsByType(nRef, typeQName, skipCount, maxItems));
    }

    public List<NodeRef> getSourceAssocsNodeRefsLimited(Object nodeRef,
                                                 String qName,
                                                 int skipCount,
                                                 int maxItems) {

        List<AssociationRef> assocRefs = getSourceAssocsLimited(nodeRef, qName, skipCount, maxItems);
        List<NodeRef> nodeRefs = assocRefs.stream().map(AssociationRef::getSourceRef).collect(Collectors.toList());

        return nodeRefs;
    }

    private QName createQNameFromPattern(String pattern) {
        return (pattern != null && !pattern.isEmpty()) ? QName.createQName(pattern) : null;
    }

    @Autowired
    public void setEcosNodeService(EcosNodeService ecosNodeService) {
        this.ecosNodeService = ecosNodeService;
    }
}
