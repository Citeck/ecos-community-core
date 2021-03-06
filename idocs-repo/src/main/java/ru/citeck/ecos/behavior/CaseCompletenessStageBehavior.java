package ru.citeck.ecos.behavior;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.icase.activity.service.alfresco.CaseActivityPolicies;
import ru.citeck.ecos.icase.completeness.CaseCompletenessServiceImpl;
import ru.citeck.ecos.model.StagesModel;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.service.CiteckServices;
import ru.citeck.ecos.utils.AlfActivityUtils;
import ru.citeck.ecos.utils.RepoUtils;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@DependsOn("idocs.dictionaryBootstrap")
public class CaseCompletenessStageBehavior implements CaseActivityPolicies.BeforeCaseActivityStartedPolicy,
                                                      CaseActivityPolicies.BeforeCaseActivityStoppedPolicy {

    private static final String REQUIREMENTS_ERROR_MESSAGE = "requirement.message.business-requirements-not-completed";

    private static final Log logger = LogFactory.getLog(CaseCompletenessStageBehavior.class);

    private NodeService nodeService;
    private PolicyComponent policyComponent;
    private AlfActivityUtils alfActivityUtils;
    private CaseCompletenessServiceImpl caseCompletenessService;

    @Autowired
    public CaseCompletenessStageBehavior(ServiceRegistry serviceRegistry) {
        this.nodeService = serviceRegistry.getNodeService();
        this.policyComponent = serviceRegistry.getPolicyComponent();
        this.alfActivityUtils = (AlfActivityUtils) serviceRegistry.getService(CiteckServices.ALF_ACTIVITY_UTILS);
        this.caseCompletenessService = (CaseCompletenessServiceImpl) serviceRegistry.getService(CiteckServices.CASE_COMPLETENESS_SERVICE);
    }

    @PostConstruct
    public void init() {
        policyComponent.bindClassBehaviour(
                CaseActivityPolicies.BeforeCaseActivityStartedPolicy.QNAME,
                StagesModel.ASPECT_HAS_START_COMPLETENESS_LEVELS_RESTRICTION,
                new JavaBehaviour(this,
                        "beforeCaseActivityStarted",
                        Behaviour.NotificationFrequency.EVERY_EVENT)
        );
        policyComponent.bindClassBehaviour(
                CaseActivityPolicies.BeforeCaseActivityStoppedPolicy.QNAME,
                StagesModel.ASPECT_HAS_END_COMPLETENESS_LEVELS_RESTRICTION,
                new JavaBehaviour(this,
                        "beforeCaseActivityStopped",
                        Behaviour.NotificationFrequency.EVERY_EVENT)
        );
    }

    @Override
    public void beforeCaseActivityStarted(NodeRef stageRef) {
        if (!nodeService.exists(stageRef)) {
            logger.warn("Some nodes doesn't exists");
            return;
        }
        checkCompletenessLevel(stageRef, StagesModel.ASSOC_START_COMPLETENESS_LEVELS_RESTRICTION);
    }

    @Override
    public void beforeCaseActivityStopped(NodeRef stageRef) {
        if (!nodeService.exists(stageRef)) {
            logger.warn("Some nodes doesn't exists");
            return;
        }
        checkCompletenessLevel(stageRef, StagesModel.ASSOC_STOP_COMPLETENESS_LEVELS_RESTRICTION);
    }

    private void checkCompletenessLevel(NodeRef stageRef, QName assocTypeQName) {
        RecordRef documentId = alfActivityUtils.getDocumentId(stageRef);
        if (documentId == null) {
            return;
        }
        NodeRef caseRef = RecordsUtils.toNodeRef(documentId);
        List<NodeRef> completenessLevels = RepoUtils.getTargetAssoc(stageRef, assocTypeQName, nodeService);

        StringBuilder incompleteLevels = null;
        for (NodeRef completenessLevel : completenessLevels) {
            if (!caseCompletenessService.isLevelCompleted(caseRef, completenessLevel)) {
                if (incompleteLevels == null) {
                    incompleteLevels = new StringBuilder();
                } else {
                    incompleteLevels.append(", ");
                }
                incompleteLevels.append(nodeService.getProperty(completenessLevel, ContentModel.PROP_TITLE));
            }
        }
        if (incompleteLevels != null) {
            throw new AlfrescoRuntimeException(
                    String.format(I18NUtil.getMessage(REQUIREMENTS_ERROR_MESSAGE), incompleteLevels.toString())
            );
        }
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setCaseCompletenessService(CaseCompletenessServiceImpl caseCompletenessService) {
        this.caseCompletenessService = caseCompletenessService;
    }
}
