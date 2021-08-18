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
package ru.citeck.ecos.config;

import kotlin.Unit;
import lombok.Data;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.behavior.JavaBehaviour;
import ru.citeck.ecos.events2.EventService;
import ru.citeck.ecos.events2.emitter.EmitterConfig;
import ru.citeck.ecos.events2.emitter.EventEmitter;
import ru.citeck.ecos.records2.RecordRef;

import javax.annotation.PostConstruct;

@Component
public class NewEventsTest implements NodeServicePolicies.OnCreateNodePolicy {

    @Autowired
    private PolicyComponent policyComponent;

    @Autowired
    private EventService eventService;

    private EventEmitter<EventData> emitter;

    @PostConstruct
    public void init() {

        QName type = QName.createQName("http://www.alfresco.org/model/content/1.0", "content");
        this.policyComponent.bindClassBehaviour(
                NodeServicePolicies.OnCreateNodePolicy.QNAME,
                type,
                new JavaBehaviour(this, "onCreateNode", NotificationFrequency.EVERY_EVENT)
        );

        emitter = eventService.getEmitter(EmitterConfig.Companion.create(b -> {
            b.setEventClass(EventData.class);
            b.setEventType("record-created");
            b.setFamily("alfresco-family");
            b.setSource("alfresco");
            return Unit.INSTANCE;
        }));
    }

    @Override
    public void onCreateNode(ChildAssociationRef childAssocRef) {
        AuthenticationUtil.runAsSystem(() -> {
            emitter.emit(new EventData(childAssocRef.getChildRef()));
            return null;
        });
    }

    @Data
    public static class EventData {

        private RecordRef record;
        private String customField = "custom field";

        public EventData(NodeRef nodeRef) {
            this.record = RecordRef.valueOf(nodeRef.toString());
        }
    }
}
