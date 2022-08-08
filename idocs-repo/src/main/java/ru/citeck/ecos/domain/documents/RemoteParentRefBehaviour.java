package ru.citeck.ecos.domain.documents;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.behavior.base.AbstractBehaviour;
import ru.citeck.ecos.behavior.base.PolicyMethod;
import ru.citeck.ecos.model.EcosModel;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.request.RequestContext;
import ru.citeck.ecos.utils.TransactionUtils;
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProperties;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class RemoteParentRefBehaviour extends AbstractBehaviour implements NodeServicePolicies.OnCreateNodePolicy  {

    private static final String TXN_KEY = RemoteParentRefBehaviour.class.getName() + ".txn-key";

    private final RecordsService recordsService;
    private final EcosWebAppProperties webAppProperties;

    @Override
    protected void beforeInit() {
        setClassName(EcosModel.ASPECT_HAS_REMOTE_PARENT_REF);
    }

    @Override
    @PolicyMethod(policy = NodeServicePolicies.OnCreateNodePolicy.class,
        frequency = Behaviour.NotificationFrequency.EVERY_EVENT, runAsSystem = true)
    public void onCreateNode(ChildAssociationRef childAssocRef) {

        RequestContext ctx = RequestContext.getCurrent();
        if (ctx == null || !ctx.ctxData.getTxnOwner()) {
            return;
        }

        NodeRef nodeRef = childAssocRef.getChildRef();
        String remoteParentRefStr = (String) nodeService.getProperty(nodeRef, EcosModel.PROP_REMOTE_PARENT_REF);

        RecordRef remoteParentRef = RecordRef.valueOf(remoteParentRefStr);

        if (RecordRef.isEmpty(remoteParentRef)) {
            return;
        }

        RecordRef docRef = RecordRef.create(webAppProperties.getAppName(), "", nodeRef.toString());

        Map<RecordRef, Set<RecordRef>> docsByRemoteRef = TransactionalResourceHelper.getMap(TXN_KEY);
        boolean isFirstRef = docsByRemoteRef.isEmpty();
        docsByRemoteRef.computeIfAbsent(remoteParentRef, key -> new HashSet<>()).add(docRef);

        if (isFirstRef) {
            TransactionUtils.doAfterCommit(() ->
                docsByRemoteRef.forEach((remoteRef, docs) -> {
                    try {
                        recordsService.mutateAtt(remoteRef, "att_add_documents", docs);
                    } catch (Exception e) {
                        log.error("Documents can't be updated for remote ref " + remoteRef + " docs: " + docs);
                    }
                })
            );
        }
    }
}
