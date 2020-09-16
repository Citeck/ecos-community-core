package ru.citeck.ecos.behavior;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeServicePolicies.OnUpdatePropertiesPolicy;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.NoSuchPersonException;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.behavior.base.AbstractBehaviour;
import ru.citeck.ecos.behavior.base.PolicyMethod;
import ru.citeck.ecos.journals.JournalService;
import ru.citeck.ecos.model.EcosModel;
import ru.citeck.ecos.utils.NewUIUtils;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class InvalidateCacheBehaviour extends AbstractBehaviour implements OnUpdatePropertiesPolicy {
    private static final Log logger = LogFactory.getLog(InvalidateCacheBehaviour.class);

    @Autowired
    private PersonService personService;

    @Autowired
    private NewUIUtils newUIUtils;

    @Autowired
    private JournalService journalService;

    @Override
    protected void beforeInit() {
        setClassName(ContentModel.TYPE_PERSON);
    }

    @Override
    @PolicyMethod(policy = OnUpdatePropertiesPolicy.class,
        frequency = Behaviour.NotificationFrequency.TRANSACTION_COMMIT)
    public void onUpdateProperties(NodeRef nodeRef, Map<QName, Serializable> before, Map<QName, Serializable> after) {
        try {
            Object propBefore = before.get(EcosModel.PROP_NEW_JOURNALS_ENABLED);
            Object propAfter = after.get(EcosModel.PROP_NEW_JOURNALS_ENABLED);
            String userName = personService.getPerson(nodeRef).getUserName();
            if (!Objects.equals(propBefore, propAfter)) {
                newUIUtils.invalidateCacheForUser(userName);
                journalService.clearCacheForUser(userName);
            }
        } catch (NoSuchPersonException e) {
            logger.error("Cannot find person for node " + nodeRef);
        }
    }
}
