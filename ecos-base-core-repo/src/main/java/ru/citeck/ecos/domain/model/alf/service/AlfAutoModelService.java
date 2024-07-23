package ru.citeck.ecos.domain.model.alf.service;

import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.*;

public interface AlfAutoModelService {

    QName QNAME = QName.createQName("", "alfAutoModelService");

    Map<String, String> getPropsMapping(EntityRef typeRef);

    Map<String, String> getPropsMapping(EntityRef typeRef, Collection<String> attributes, boolean isWriteMode);
}
