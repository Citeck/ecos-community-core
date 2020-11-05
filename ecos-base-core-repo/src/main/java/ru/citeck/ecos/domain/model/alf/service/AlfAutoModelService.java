package ru.citeck.ecos.domain.model.alf.service;

import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;

public interface AlfAutoModelService {

    QName QNAME = QName.createQName("", "alfAutoModelService");

    Map<String, String> getPropsMapping(RecordRef typeRef);

    Map<String, String> getPropsMapping(RecordRef typeRef, Collection<String> attributes, boolean isWriteMode);
}
