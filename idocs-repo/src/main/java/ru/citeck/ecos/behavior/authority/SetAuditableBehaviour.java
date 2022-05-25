package ru.citeck.ecos.behavior.authority;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.behavior.base.AbstractBehaviour;
import ru.citeck.ecos.behavior.base.PolicyMethod;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@DependsOn("idocs.dictionaryBootstrap")
public class SetAuditableBehaviour extends AbstractBehaviour
    implements NodeServicePolicies.OnCreateNodePolicy,
               NodeServicePolicies.OnUpdatePropertiesPolicy,
               NodeServicePolicies.OnCreateAssociationPolicy,
               NodeServicePolicies.OnDeleteAssociationPolicy,
               NodeServicePolicies.OnCreateChildAssociationPolicy,
               NodeServicePolicies.OnDeleteChildAssociationPolicy {

    private NodeService nodeService;

    private LoadingCache<NodeRef, Boolean> cache;

    @Override
    protected void beforeInit() {

        cache = CacheBuilder.newBuilder()
            .maximumSize(200)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build(CacheLoader.from(this::addAuditAspectIfRequired));

        setClassName(ContentModel.TYPE_AUTHORITY);
        this.dictionaryService = serviceRegistry.getDictionaryService();
        this.nodeService = serviceRegistry.getNodeService();
    }

    @PolicyMethod(policy = NodeServicePolicies.OnCreateNodePolicy.class,
                  frequency = Behaviour.NotificationFrequency.EVERY_EVENT)
    public void onCreateNode(ChildAssociationRef childAssociationRef) {
        cache.getUnchecked(childAssociationRef.getChildRef());
    }

    @PolicyMethod(policy = NodeServicePolicies.OnUpdatePropertiesPolicy.class,
                  frequency = Behaviour.NotificationFrequency.EVERY_EVENT)
    public void onUpdateProperties(NodeRef nodeRef,
                                   Map<QName, Serializable> before,
                                   Map<QName, Serializable> after) {

        cache.getUnchecked(nodeRef);
    }

    @PolicyMethod(policy = NodeServicePolicies.OnCreateAssociationPolicy.class,
                  frequency = Behaviour.NotificationFrequency.EVERY_EVENT)
    public void onCreateAssociation(AssociationRef nodeAssocRef) {

        cache.getUnchecked(nodeAssocRef.getSourceRef());
    }

    @PolicyMethod(policy = NodeServicePolicies.OnDeleteAssociationPolicy.class,
        frequency = Behaviour.NotificationFrequency.EVERY_EVENT)
    public void onDeleteAssociation(AssociationRef nodeAssocRef) {

        cache.getUnchecked(nodeAssocRef.getSourceRef());
    }

    @PolicyMethod(policy = NodeServicePolicies.OnCreateChildAssociationPolicy.class,
                  frequency = Behaviour.NotificationFrequency.EVERY_EVENT)
    public void onCreateChildAssociation(ChildAssociationRef childAssocRef, boolean isNewNode) {
        cache.getUnchecked(childAssocRef.getParentRef());
    }

    @PolicyMethod(policy = NodeServicePolicies.OnDeleteChildAssociationPolicy.class,
        frequency = Behaviour.NotificationFrequency.EVERY_EVENT)
    public void onDeleteChildAssociation(ChildAssociationRef childAssocRef) {
        cache.getUnchecked(childAssocRef.getParentRef());
    }

    private boolean addAuditAspectIfRequired(NodeRef nodeRef) {
        return AuthenticationUtil.runAsSystem(() -> {
            if (nodeService.exists(nodeRef) && !nodeService.hasAspect(nodeRef, ContentModel.ASPECT_AUDITABLE)) {
                nodeService.addAspect(nodeRef, ContentModel.ASPECT_AUDITABLE, Collections.emptyMap());
            }
            return true;
        });
    }
}
