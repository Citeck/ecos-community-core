/*
 * Copyright (C) 2008-2015 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.behavior;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.admin.RepositoryState;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import ru.citeck.ecos.cmmn.service.CaseXmlService;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.icase.CaseStatusService;
import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.dto.CaseServiceType;
import ru.citeck.ecos.icase.activity.service.CaseActivityEventService;
import ru.citeck.ecos.icase.activity.service.eproc.importer.EProcCaseImporter;
import ru.citeck.ecos.model.ICaseEventModel;
import ru.citeck.ecos.model.ICaseModel;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.service.AlfrescoServices;
import ru.citeck.ecos.service.CiteckServices;
import ru.citeck.ecos.state.ItemsUpdateState;
import ru.citeck.ecos.utils.TransactionUtils;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
@Component
@DependsOn("idocs.dictionaryBootstrap")
public class CaseTemplateBehavior implements NodeServicePolicies.OnCreateNodePolicy, NodeServicePolicies.OnAddAspectPolicy {

    private static final String CREATED_CASES_TXN_KEY = CaseTemplateBehavior.class.getSimpleName() + "-created-cases";

    private static final String ECOS_CASE_PROCESS_TYPE_CONFIG_KEY = "ecos-case-process-type";
    private static final String KEY_FILLED_CASE_NODES = "filled-case-nodes";
    private static final String STATUS_PROCESS_START_ERROR = "ecos-process-start-error";

    private static final ThreadLocal<Boolean> runInCurrentThread = ThreadLocal.withInitial(() -> false);

    private PolicyComponent policyComponent;
    private NodeService nodeService;
    private CaseXmlService caseXmlService;

    private RepositoryState repositoryState;
    private ItemsUpdateState itemsUpdateState;
    private CaseStatusService caseStatusService;
    private EcosConfigService ecosConfigService;
    private EProcCaseImporter eProcCaseImporter;
    private CaseActivityEventService caseActivityEventService;

    private int order;

    @Autowired
    public CaseTemplateBehavior(ServiceRegistry serviceRegistry,
                                @Qualifier("ecosConfigService") EcosConfigService ecosConfigService,
                                EProcCaseImporter eprocCaseImporter,
                                @Value("${behavior.order.case.template:40}") int order) {
        this.policyComponent = serviceRegistry.getPolicyComponent();
        this.nodeService = serviceRegistry.getNodeService();
        this.caseXmlService = (CaseXmlService) serviceRegistry.getService(CiteckServices.CASE_XML_SERVICE);
        this.repositoryState = (RepositoryState) serviceRegistry.getService(AlfrescoServices.REPOSITORY_STATE);
        this.itemsUpdateState = (ItemsUpdateState) serviceRegistry.getService(CiteckServices.ITEMS_UPDATE_STATE);
        this.caseStatusService = (CaseStatusService) serviceRegistry.getService(CiteckServices.CASE_STATUS_SERVICE);
        this.caseActivityEventService = (CaseActivityEventService) serviceRegistry
                .getService(CiteckServices.CASE_ACTIVITY_EVENT_SERVICE);
        this.ecosConfigService = ecosConfigService;
        this.eProcCaseImporter = eprocCaseImporter;
        this.order = order;
    }

    @PostConstruct
    public void init() {
        policyComponent.bindClassBehaviour(NodeServicePolicies.OnCreateNodePolicy.QNAME, ICaseModel.ASPECT_CASE,
                new OrderedBehaviour(this, "onCreateNode", NotificationFrequency.EVERY_EVENT, order));
        policyComponent.bindClassBehaviour(NodeServicePolicies.OnAddAspectPolicy.QNAME, ICaseModel.ASPECT_CASE,
                new OrderedBehaviour(this, "onAddAspect", NotificationFrequency.EVERY_EVENT, order));
    }

    @Override
    public void onAddAspect(NodeRef caseNode, QName aspectTypeQName) {
        if (ICaseModel.ASPECT_CASE.equals(aspectTypeQName)) {
            TransactionUtils.processBeforeCommit(
                CREATED_CASES_TXN_KEY,
                new CaseCreatedEvent(caseNode, runInCurrentThread.get()),
                this::copyFromTemplate
            );
        }
    }

    @Override
    public void onCreateNode(ChildAssociationRef childAssocRef) {
        TransactionUtils.processBeforeCommit(
            CREATED_CASES_TXN_KEY,
            new CaseCreatedEvent(childAssocRef.getChildRef(), runInCurrentThread.get()),
            this::copyFromTemplate
        );
    }

    public static void runInCurrentThread(Runnable action) {
        boolean valueBefore = runInCurrentThread.get();
        runInCurrentThread.set(true);
        try {
            action.run();
        } finally {
            runInCurrentThread.set(valueBefore);
        }
    }

    private void copyFromTemplate(CaseCreatedEvent caseCreatedEvent) {

        final NodeRef caseNode = caseCreatedEvent.nodeRef;

        if (repositoryState.isBootstrapping()
                || !isAllowedCaseNode(caseNode)
                || !getFilledCaseNodes().add(caseNode)) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Applying template to node. nodeRef=" + caseNode);
        }

        final CaseServiceType enabledCaseServiceType = getEnabledCaseServiceType();

        if (enabledCaseServiceType == CaseServiceType.EPROC) {
            eprocCopyFromTemplateImpl(caseNode, caseCreatedEvent.runInCurrentThread);
        } else {
            alfrescoCopyFromTemplateImpl(caseNode, caseCreatedEvent.runInCurrentThread);
        }
    }

    private boolean isAllowedCaseNode(NodeRef caseNode) {
        if (caseNode == null || !nodeService.exists(caseNode)) {
            return false;
        }
        Set<QName> aspects = nodeService.getAspects(caseNode);
        return !aspects.contains(ContentModel.ASPECT_COPIEDFROM)
                && !aspects.contains(ICaseModel.ASPECT_COPIED_FROM_TEMPLATE)
                && !aspects.contains(ICaseModel.ASPECT_CASE_TEMPLATE)
                && !aspects.contains(ICaseModel.ASPECT_LEGACY_EDITOR_TEMPLATE);
    }

    private Set<NodeRef> getFilledCaseNodes() {
        Set<NodeRef> filledCaseNodes = AlfrescoTransactionSupport.getResource(KEY_FILLED_CASE_NODES);
        if (filledCaseNodes == null) {
            AlfrescoTransactionSupport.bindResource(KEY_FILLED_CASE_NODES, filledCaseNodes = new HashSet<>());
        }
        return filledCaseNodes;
    }

    private void alfrescoCopyFromTemplateImpl(NodeRef caseNode, boolean runInCurrentThread) {
        copyFromTemplateImpl(caseNode, CaseServiceType.ALFRESCO, runInCurrentThread,
            () -> caseXmlService.fillCaseFromTemplate(caseNode),
            () -> {
                RecordRef caseRef = RecordRef.valueOf(caseNode.toString());
                ActivityRef activityRef = ActivityRef.of(CaseServiceType.ALFRESCO, caseRef, ActivityRef.ROOT_ID);
                caseActivityEventService.fireEvent(activityRef, ICaseEventModel.CONSTR_CASE_CREATED);
            }
        );
    }

    private void eprocCopyFromTemplateImpl(NodeRef caseNode, boolean runInCurrentThread) {
        RecordRef caseRef = RecordRef.valueOf(caseNode.toString());
        copyFromTemplateImpl(caseNode, CaseServiceType.EPROC, runInCurrentThread,
            () -> eProcCaseImporter.importCase(caseRef),
            () -> {
                ActivityRef activityRef = ActivityRef.of(CaseServiceType.EPROC, caseRef, ActivityRef.ROOT_ID);
                caseActivityEventService.fireEvent(activityRef, ICaseEventModel.CONSTR_CASE_CREATED);
            }
        );
    }

    private void copyFromTemplateImpl(NodeRef caseNode,
                                      CaseServiceType type,
                                      boolean runInCurrentThread,
                                      Runnable applyTemplate,
                                      Runnable fireCaseCreated) {

        final StopWatch stopWatch = new StopWatch(CaseTemplateBehavior.class.getName());
        BiConsumer<String, Runnable> runWithStopWatch = (taskName, action) -> {
            if (!stopWatch.isRunning()) {
                stopWatch.start(taskName);
            }
            try {
                action.run();
            } finally {
                stopWatch.stop();
            }
        };

        String prefix = "[" + type + "] ";
        String copyFromTemplateTaskName = prefix + "copyFromTemplate caseRef: " + caseNode;
        String fireCaseCreatedTaskName = prefix + "fire '"
            + ICaseEventModel.CONSTR_CASE_CREATED + "' event. caseRef: " + caseNode;

        if (runInCurrentThread) {

            runWithStopWatch.accept(copyFromTemplateTaskName, applyTemplate);
            runWithStopWatch.accept(fireCaseCreatedTaskName, fireCaseCreated);

            log.info(stopWatch.prettyPrint());

        } else {

            itemsUpdateState.startUpdate(CaseTemplateBehavior.class, caseNode);

            TransactionUtils.doAfterCommit(() -> {
                runWithStopWatch.accept(copyFromTemplateTaskName, applyTemplate);
                TransactionUtils.doAfterCommit(
                    () -> {
                        runWithStopWatch.accept(fireCaseCreatedTaskName, fireCaseCreated);
                        itemsUpdateState.endUpdate(
                            CaseTemplateBehavior.class,
                            caseNode,
                            true,
                            false
                        );
                        log.info(stopWatch.prettyPrint());
                    },
                    getExceptionConsumer(caseNode)
                );
            }, getExceptionConsumer(caseNode));
        }
    }

    private Consumer<Exception> getExceptionConsumer(NodeRef caseNode) {
        return e -> {
            itemsUpdateState.endUpdate(CaseTemplateBehavior.class, caseNode, true, true);
            caseStatusService.setStatus(caseNode, STATUS_PROCESS_START_ERROR);
        };
    }

    private CaseServiceType getEnabledCaseServiceType() {
        Object serviceType = ecosConfigService.getParamValue(ECOS_CASE_PROCESS_TYPE_CONFIG_KEY);
        if (serviceType == null) {
            throw new IllegalStateException("Param '" + ECOS_CASE_PROCESS_TYPE_CONFIG_KEY +
                    "' in system configuration is mandatory must be 'alf' or 'eproc'");
        }

        if (StringUtils.equalsIgnoreCase(CaseServiceType.ALFRESCO.getShortName(), serviceType.toString())) {
            return CaseServiceType.ALFRESCO;
        }
        if (StringUtils.equalsIgnoreCase(CaseServiceType.EPROC.getShortName(), serviceType.toString())) {
            return CaseServiceType.EPROC;
        }

        throw new IllegalStateException("Param '" + ECOS_CASE_PROCESS_TYPE_CONFIG_KEY +
                "' in system configuration is mandatory must be 'alf' or 'eproc'");
    }

    @Data
    @AllArgsConstructor
    private static class CaseCreatedEvent {
        private NodeRef nodeRef;
        private boolean runInCurrentThread;
    }
}
