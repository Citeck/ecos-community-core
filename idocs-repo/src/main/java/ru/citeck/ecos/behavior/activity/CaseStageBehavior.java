package ru.citeck.ecos.behavior.activity;

import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.behavior.ChainingJavaBehaviour;
import ru.citeck.ecos.icase.CaseStatusAssocDao;
import ru.citeck.ecos.icase.CaseStatusService;
import ru.citeck.ecos.icase.activity.service.alfresco.CaseActivityPolicies;
import ru.citeck.ecos.model.IdocsModel;
import ru.citeck.ecos.model.StagesModel;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.service.CiteckServices;
import ru.citeck.ecos.utils.AlfActivityUtils;

import javax.annotation.PostConstruct;

/**
 * @author Pavel Simonov
 */
@Component
@DependsOn("idocs.dictionaryBootstrap")
public class CaseStageBehavior implements CaseActivityPolicies.BeforeCaseActivityStartedPolicy {

    private AlfActivityUtils alfActivityUtils;
    private CaseStatusService caseStatusService;
    private PolicyComponent policyComponent;
    private NodeService nodeService;
    private final CaseStatusAssocDao caseStatusAssocDao;

    @Autowired
    public CaseStageBehavior(ServiceRegistry serviceRegistry) {
        this.alfActivityUtils = (AlfActivityUtils) serviceRegistry.getService(CiteckServices.ALF_ACTIVITY_UTILS);
        this.caseStatusService = (CaseStatusService) serviceRegistry.getService(CiteckServices.CASE_STATUS_SERVICE);
        this.caseStatusAssocDao = (CaseStatusAssocDao) serviceRegistry.getService(CiteckServices.CASE_STATUS_ASSOC_DAO);
        this.policyComponent = serviceRegistry.getPolicyComponent();
        this.nodeService = serviceRegistry.getNodeService();
    }

    @PostConstruct
    public void init() {
        this.policyComponent.bindClassBehaviour(
                CaseActivityPolicies.BeforeCaseActivityStartedPolicy.QNAME,
                StagesModel.TYPE_STAGE,
                new ChainingJavaBehaviour(
                        this,
                        "beforeCaseActivityStarted",
                         Behaviour.NotificationFrequency.EVERY_EVENT
                )
        );
    }

    @Override
    public void beforeCaseActivityStarted(NodeRef stageRef) {
        if (!nodeService.exists(stageRef)) {
            return;
        }

        RecordRef documentId = alfActivityUtils.getDocumentId(stageRef);
        NodeRef documentNodeRef = RecordsUtils.toNodeRef(documentId);

        String documentStatus = (String) nodeService.getProperty(stageRef, StagesModel.PROP_DOCUMENT_STATUS);
        if (StringUtils.isNotEmpty(documentStatus)) {
            nodeService.setProperty(documentNodeRef, IdocsModel.PROP_DOCUMENT_STATUS, documentStatus);
        }

        NodeRef caseStatusRef = caseStatusAssocDao.getStatusByAssoc(stageRef, StagesModel.ASSOC_CASE_STATUS);
        if (caseStatusRef != null) {
            caseStatusService.setStatus(documentNodeRef, caseStatusRef);
        }
    }
}
