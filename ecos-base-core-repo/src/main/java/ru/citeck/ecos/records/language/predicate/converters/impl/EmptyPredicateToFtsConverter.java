package ru.citeck.ecos.records.language.predicate.converters.impl;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.ClassAttributeDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.language.predicate.converters.PredicateToFtsConverter;
import ru.citeck.ecos.records2.predicate.model.EmptyPredicate;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.search.AssociationIndexPropertyRegistry;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.DictUtils;

import java.util.function.Consumer;

@Component
@Slf4j
public class EmptyPredicateToFtsConverter implements PredicateToFtsConverter {

    private DictUtils dictUtils;
    private AssociationIndexPropertyRegistry associationIndexPropertyRegistry;

    @Override
    public void convert(Predicate predicate, FTSQuery query) {
        String attribute = ((EmptyPredicate) predicate).getAttribute();
        QName attQName = getQueryField(dictUtils.getAttDefinition(attribute));
        DataTypeDefinition attDataType = dictUtils.getPropertyDataType(attQName);

        if (attDataType != null &&
            ("java.lang.String".equalsIgnoreCase(attDataType.getJavaClassName())
            || "org.alfresco.service.cmr.repository.MLText".equalsIgnoreCase(attDataType.getJavaClassName()))) {
            consumeQueryField(attribute, query::emptyString);
        } else {
            consumeQueryField(attribute, query::empty);
        }
    }

    private void consumeQueryField(String field, Consumer<QName> consumer) {
        QName attQName = getQueryField(dictUtils.getAttDefinition(field));
        if (attQName != null) {
            consumer.accept(attQName);
        }
    }

    private QName getQueryField(ClassAttributeDefinition def) {
        if (def == null) {
            return null;
        }

        if (def instanceof AssociationDefinition) {
            return associationIndexPropertyRegistry.getAssociationIndexProperty(def.getName());
        }
        return def.getName();
    }

    @Autowired
    public void setDictUtils(DictUtils dictUtils) {
        this.dictUtils = dictUtils;
    }

    @Autowired
    public void setAssociationIndexPropertyRegistry(AssociationIndexPropertyRegistry associationIndexPropertyRegistry) {
        this.associationIndexPropertyRegistry = associationIndexPropertyRegistry;
    }
}
