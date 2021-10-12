package ru.citeck.ecos.icase;

import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.model.lib.status.constants.StatusConstants;
import ru.citeck.ecos.model.lib.status.dto.StatusDef;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;
import ru.citeck.ecos.utils.JavaScriptImplUtils;
import ru.citeck.ecos.utils.NodeUtils;

/**
 * @author Roman Makarskiy
 */
public class CaseStatusServiceJS extends AlfrescoScopableProcessorExtension {

    private CaseStatusService caseStatusService;
    private RecordsService recordsService;

    public void setStatus(Object caseRef, Object caseStatus) {

        if (caseRef instanceof String && caseStatus instanceof String) {
            String caseRefStr = (String) caseRef;
            if (!caseRefStr.contains(NodeUtils.WORKSPACE_PREFIX)) {
                recordsService.mutateAtt(RecordRef.valueOf(caseRefStr), StatusConstants.ATT_STATUS, caseStatus);
                return;
            }
        }

        NodeRef caseStatusRef;
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(caseRef);

        if (caseStatus instanceof String && !NodeRef.isNodeRef(caseStatus.toString())) {
            caseStatusRef = caseStatusService.getStatusByName(docRef, caseStatus.toString());
        } else {
            caseStatusRef = JavaScriptImplUtils.getNodeRef(caseStatus);
        }

        caseStatusService.setStatus(docRef, caseStatusRef);
    }

    @Deprecated
    public ScriptNode getStatusByName(String statusName) {
        NodeRef caseStatusRef = caseStatusService.getStatusByName(statusName);
        return JavaScriptImplUtils.wrapNode(caseStatusRef, this);
    }

    public ScriptNode getStatusByName(String statusName, Object document) {
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        NodeRef caseStatusRef = caseStatusService.getStatusByName(docRef, statusName);
        return JavaScriptImplUtils.wrapNode(caseStatusRef, this);
    }

    public String getStatus(Object document) {
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        return caseStatusService.getStatus(docRef);
    }

    public ScriptNode getStatusNode(Object document) {
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        NodeRef statusRef = caseStatusService.getStatusRef(docRef);
        return JavaScriptImplUtils.wrapNode(statusRef, this);
    }

    public String getStatusName(Object document, Object statusNode) {
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        NodeRef statusRef = JavaScriptImplUtils.getNodeRef(statusNode);
        return caseStatusService.getStatusName(docRef, statusRef);
    }

    public boolean isAlfRef(Object document) {
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        return caseStatusService.isAlfRef(docRef);
    }

    public String getEcosStatusName(Object document, Object statusNode) {
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        NodeRef statusRef = JavaScriptImplUtils.getNodeRef(statusNode);
        StatusDef statusDef = caseStatusService.getStatusDef(docRef, statusRef.getId());

        if (statusDef == null) {
            return null;
        }

        return statusDef.getName().getClosestValue(I18NUtil.getLocale());
    }

    public ScriptNode getStatusNodeFromPrimaryParent(Object document) {
        NodeRef childRef = JavaScriptImplUtils.getNodeRef(document);
        NodeRef statusRef = caseStatusService.getStatusRefFromPrimaryParent(childRef);
        return JavaScriptImplUtils.wrapNode(statusRef, this);
    }

    public boolean isDocumentInStatus(String[] statuses, Object document) {
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        String status = caseStatusService.getStatus(docRef);

        if (StringUtils.isNotBlank(status)) {
            for (String item : statuses) {
                if (item.equals(status)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void setCaseStatusService(CaseStatusService caseStatusService) {
        this.caseStatusService = caseStatusService;
    }

    @Autowired
    public void setRecordsService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }
}
