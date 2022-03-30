package ru.citeck.ecos.domain.admin.nodebrowser;

import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.jetbrains.annotations.NotNull;

public interface NodeBrowserNodeDao {

    @NotNull
    NodeElementsResult<ChildAssociationRef> getChildAssocs(@NotNull NodeRef nodeRef, int skipCount, int maxItems);

    @NotNull
    NodeElementsResult<AssociationRef> getSourceAssocs(@NotNull NodeRef nodeRef, int skipCount, int maxItems);
}
