package ru.citeck.ecos.icase;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import ru.citeck.ecos.model.lib.status.dto.StatusDef;
import ru.citeck.ecos.records2.RecordRef;

/**
 * @author Roman Makarskiy
 * @author Pavel Simonov
 */
public interface CaseStatusService {

    /**
     * Set case status to document.
     *
     * @param document - document nodeRef
     * @param status   - case status nodeRef
     */
    void setStatus(NodeRef document, NodeRef status);

    /**
     * Set case status to document
     *
     * @throws IllegalArgumentException if status not found in system
     */
    void setStatus(NodeRef document, String status);

    /**
     * Get case status by name.
     *
     * @param statusName - case status name
     * @return case status nodeRef
     */
    @Deprecated
    NodeRef getStatusByName(String statusName);

    /**
     * Get case status by name and document.
     *
     * @param statusName - case status name
     * @param document - document NodeRef
     * @return case status nodeRef
     */
    NodeRef getStatusByName(NodeRef document, String statusName);

    /**
     * Get case status
     *
     * @return case status name or null if status doesn't exists in this case
     */
    String getStatus(NodeRef caseRef);

    /**
     * Get case status and document
     *
     * @return case status name or null if status doesn't exists in this case
     */
    String getStatusName(NodeRef caseRef, NodeRef statusRef);

    /**
     * Get case status and ECOS type
     *
     * @return case status name or null if status doesn't exists in this ecos type
     */
    NodeRef getStatusByNameAndType(String statusName, RecordRef etype);

    /**
     * Get ECOS type and ECOS case status name
     *
     * @return ECOS virtual case status name or null if case status name is null
     */
    NodeRef getEcosStatus(String etype, String statusName);

    /**
     * Get case status before
     *
     * @return case status before name or null if status doesn't exists in this case
     */
    String getStatusBefore(NodeRef caseRef);

    /**
     * Get case status reference
     *
     * @return case status nodeRef or null if status doesn't exists in this case
     */
    NodeRef getStatusRef(NodeRef caseRef);

    /**
     * Get ECOS case status definition
     *
     * @return case status StatusDef or null if status doesn't exists in this case
     */
    StatusDef getStatusDef(NodeRef caseRef, String statusId);

    /**
     * Get case status before reference
     *
     * @return case status before nodeRef or null if status doesn't exists in this case
     */
    NodeRef getStatusBeforeRef(NodeRef caseRef);

    /**
     * Get case status reference from primary parent
     *
     * @param childRef - nodeRef of child
     * @return case status nodeRef or null if status doesn't exists in the primary parent
     */
    NodeRef getStatusRefFromPrimaryParent(NodeRef childRef);


    default boolean isAlfRef(NodeRef nodeRef) {
        return nodeRef == null || StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.equals(nodeRef.getStoreRef());
    }
}
