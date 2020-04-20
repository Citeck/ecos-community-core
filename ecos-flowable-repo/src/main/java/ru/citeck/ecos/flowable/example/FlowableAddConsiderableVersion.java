package ru.citeck.ecos.flowable.example;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.service.ServiceDescriptorRegistry;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.json.JSONException;
import ru.citeck.ecos.confirm.ConfirmService;
import ru.citeck.ecos.providers.ApplicationContextProvider;
import ru.citeck.ecos.service.CiteckServices;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Add version task listener
 */
public class FlowableAddConsiderableVersion implements TaskListener {

    private ConfirmService confirmService;
    private NamespaceService namespaceService;
    private NodeService nodeService;

    private void initServices() {
        ServiceRegistry services = ApplicationContextProvider.getBean(ServiceDescriptorRegistry.class);
        confirmService = (ConfirmService) services.getService(CiteckServices.CONFIRM_SERVICE);
        namespaceService = services.getNamespaceService();
        nodeService = services.getNodeService();

    }

    @Override
    public void notify(DelegateTask delegateTask) {
        if (delegateTask.getAssignee() == null) {
            return;
        }

        initServices();
        WorkflowQNameConverter qNameConverter = new WorkflowQNameConverter(namespaceService);

        NodeRef packageRef;
        Object varObj = delegateTask.getVariable(qNameConverter.mapQNameToName(WorkflowModel.ASSOC_PACKAGE));
        if (varObj instanceof NodeRef) {
            packageRef = (NodeRef) varObj;
        } else if (varObj instanceof ScriptNode) {
            packageRef = ((ScriptNode) varObj).getNodeRef();
        } else {
            throw new RuntimeException("Variable type not supported: " + varObj.getClass() + ". var: " + varObj);
        }

        Set<QName> includeQNames = new HashSet<>();
        includeQNames.add(WorkflowModel.ASSOC_PACKAGE_CONTAINS);
        includeQNames.add(ContentModel.ASSOC_CONTAINS);
        List<ChildAssociationRef> documentRefs = nodeService.getChildAssocs(packageRef);
        for (ChildAssociationRef documentRef : documentRefs) {
            if (!includeQNames.contains(documentRef.getTypeQName()) || documentRef.getChildRef() == null) {
                continue;
            }
            try {
                confirmService.addCurrentVersionToConsiderable(delegateTask.getAssignee(), documentRef.getChildRef());
            } catch (JSONException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }
}
