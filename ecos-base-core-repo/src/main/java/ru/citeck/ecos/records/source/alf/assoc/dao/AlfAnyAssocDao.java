package ru.citeck.ecos.records.source.alf.assoc.dao;

import lombok.RequiredArgsConstructor;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.EcosModel;
import ru.citeck.ecos.utils.NodeUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AlfAnyAssocDao implements AlfAssocDao {

    public static final QName QNAME = QName.createQName(EcosModel.ECOS_NAMESPACE, "any");

    private static final Set<QName> Q_NAMES = Collections.singleton(QNAME);

    private final NodeUtils nodeUtils;
    private final NodeService nodeService;

    @Override
    public void create(NodeRef sourceRef, NodeRef targetRef, AssociationDefinition association) {

        QName assocName = association.getName();

        if (association.isChild()) {
            if (getChildAssoc(sourceRef, targetRef, assocName) == null) {
                QName qName = QName.createQName(assocName.getNamespaceURI(), UUID.randomUUID().toString());
                nodeService.addChild(sourceRef, targetRef, assocName, qName);
            }
        } else {
            nodeUtils.createAssoc(sourceRef, targetRef, assocName);
        }
    }

    @Override
    public void remove(NodeRef sourceRef, NodeRef targetRef, AssociationDefinition association) {

        QName assocName = association.getName();

        if (association.isChild()) {
            ChildAssociationRef childAssoc = getChildAssoc(sourceRef, targetRef, assocName);
            if (childAssoc != null) {
                nodeService.removeSecondaryChildAssociation(childAssoc);
            }
        } else {
            nodeUtils.removeAssoc(sourceRef, targetRef, assocName);
        }
    }

    @Nullable
    private ChildAssociationRef getChildAssoc(NodeRef sourceRef, NodeRef targetRef, QName assocName) {

        // We should get child assocs using getParentAssocs because parent node
        // may contain a lot of children and nodeService.getChildAssocs will be very slow
        List<ChildAssociationRef> currentAssocs =
            nodeService.getParentAssocs(targetRef, assocName, RegexQNamePattern.MATCH_ALL)
                .stream()
                .filter(assoc -> sourceRef.equals(assoc.getParentRef()))
                .collect(Collectors.toList());

        return currentAssocs.stream().findFirst().orElse(null);
    }

    @Override
    public Set<QName> getQNames() {
        return Q_NAMES;
    }

    @Override
    public float getOrder() {
        return 100f;
    }
}
