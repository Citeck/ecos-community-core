package ru.citeck.ecos.records.language.predicate.converters;

import lombok.Data;
import org.alfresco.service.namespace.QName;

import java.util.List;

@Data
public class AssocToCustomSearchFieldsRegistrar {
    private QName assocQName;
    private List<QName> customSearchFields;
}
