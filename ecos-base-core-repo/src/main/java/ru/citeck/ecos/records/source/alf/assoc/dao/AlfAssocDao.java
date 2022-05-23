package ru.citeck.ecos.records.source.alf.assoc.dao;

import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;

import java.util.Set;

public interface AlfAssocDao {

    void create(NodeRef sourceRef, NodeRef targetRef, AssociationDefinition assoc);

    void remove(NodeRef sourceRef, NodeRef targetRef, AssociationDefinition assoc);

    Set<QName> getQNames();

    /**
     * The actual order can be interpreted as prioritization,
     * with the first object (with the lowest order value) having the highest priority.
     */
    default float getOrder() {
        return 0f;
    }
}
