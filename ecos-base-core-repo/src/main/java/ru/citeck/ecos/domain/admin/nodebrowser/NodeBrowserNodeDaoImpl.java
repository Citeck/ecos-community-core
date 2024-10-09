package ru.citeck.ecos.domain.admin.nodebrowser;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.domain.node.EcosNodeService;

import java.util.List;

/**
 * NodeBrowser component to add pagination support for associations.
 * This bean solves problem with loading a huge number of associations when node is opened in NodeBrowser.
 *
 * @see org.alfresco.repo.web.scripts.admin.NodeBrowserPost
 */
@Component
public class NodeBrowserNodeDaoImpl implements NodeBrowserNodeDao {

    private EcosNodeService ecosNodeService;

    @NotNull
    @Override
    public NodeElementsResult<ChildAssociationRef> getChildAssocs(@NotNull NodeRef nodeRef, int skipCount, int maxItems) {
        List<ChildAssociationRef> entities = AuthenticationUtil.runAsSystem(() -> ecosNodeService.getChildAssocsLimited(nodeRef, null,
            null, maxItems + 1, false));
        if (entities.size() > maxItems) {
            //because trying to get (maxItems + 1) of entities
            return new NodeElementsResult<>(entities.subList(0, maxItems), true);
        }
        return new NodeElementsResult<>(entities, false);
    }

    @NotNull
    @Override
    public NodeElementsResult<AssociationRef> getSourceAssocs(@NotNull NodeRef nodeRef, int skipCount, int maxItems) {
        List<AssociationRef> associationRefs = AuthenticationUtil.runAsSystem(
            () -> ecosNodeService.getSourceAssocsByType(nodeRef, null, maxItems + 1));
        if (associationRefs.size() > maxItems) {
            //because trying to get (maxItems + 1) of entities
            return new NodeElementsResult<>(associationRefs.subList(0, maxItems), true);
        }
        return new NodeElementsResult<>(associationRefs, false);
    }

    @Autowired
    public void setEcosNodeService(EcosNodeService ecosNodeService) {
        this.ecosNodeService = ecosNodeService;
    }
}
