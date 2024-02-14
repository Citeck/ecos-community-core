package ru.citeck.ecos.behavior.event;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.behavior.base.AbstractBehaviour;
import ru.citeck.ecos.behavior.base.PolicyMethod;
import ru.citeck.ecos.events2.type.RecordDeletedEvent;
import ru.citeck.ecos.events2.type.RecordEventsService;
import ru.citeck.ecos.model.lib.type.dto.TypeInfo;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;

@Slf4j
@Component
@DependsOn("idocs.dictionaryBootstrap")
public class AddDeletedEventBehaviour extends AbstractBehaviour
    implements NodeServicePolicies.BeforeDeleteNodePolicy {

    private final RecordEventsService recordEventsService;
    private final EcosTypeService ecosTypeService;

    @Autowired
    public AddDeletedEventBehaviour(RecordEventsService recordEventsService, EcosTypeService ecosTypeService) {
        this.recordEventsService = recordEventsService;
        this.ecosTypeService = ecosTypeService;
    }

    @Override
    @PolicyMethod(policy = NodeServicePolicies.BeforeDeleteNodePolicy.class,
        frequency = Behaviour.NotificationFrequency.EVERY_EVENT, runAsSystem = true)
    public void beforeDeleteNode(NodeRef nodeRef) {

        EntityRef entityRef = EntityRef.create("", nodeRef.toString());

        RecordRef typeRef = ecosTypeService.getEcosType(nodeRef);
        if (typeRef.isEmpty()) {
            log.warn("Deleted node = " + nodeRef + ", but ecosType is null.");
            return;
        }

        TypeDef typeDef = ecosTypeService.getTypeDef(typeRef);
        if (typeDef == null) {
            log.warn("Deleted node = " + nodeRef + ", but typeDef is null.");
            return;
        }
        TypeInfo typeInfo = typeDef.getTypeInfo();

        RecordDeletedEvent recordDeletedEvent = new RecordDeletedEvent(entityRef, typeInfo);
        recordEventsService.emitRecDeleted(recordDeletedEvent);
    }
}
