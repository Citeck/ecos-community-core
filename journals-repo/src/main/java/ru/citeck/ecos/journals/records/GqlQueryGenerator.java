package ru.citeck.ecos.journals.records;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.journals.JournalType;
import ru.citeck.ecos.model.AttributeModel;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records2.utils.RecordsUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GqlQueryGenerator {

    private static final Pattern FORMATTER_ATTRIBUTES_PATTERN = Pattern.compile(
            "['\"]\\s*?(\\S+?:\\S+?\\s*?(,\\s*?\\S+?:\\S+?\\s*?)*?)['\"]"
    );

    private NamespaceService namespaceService;
    private RecordsService recordsService;

    private ConcurrentHashMap<String, String> gqlQueryWithDataByJournalId = new ConcurrentHashMap<>();

    public String generate(JournalType journalType) {
        return gqlQueryWithDataByJournalId.computeIfAbsent(journalType.getId(),
                id -> generateGqlQueryWithData(journalType));
    }

    private String generateGqlQueryWithData(JournalType journalType) {

        StringBuilder schemaBuilder = new StringBuilder();

        schemaBuilder.append("id\n");

        int attrCounter = 0;

        List<String> attributes = new ArrayList<>(journalType.getAttributes());
        if (StringUtils.isEmpty(journalType.getDataSource())) {
            attributes.add(AttributeModel.ATTR_ASPECTS.toPrefixString(namespaceService));
            attributes.add(AttributeModel.ATTR_IS_CONTAINER.toPrefixString(namespaceService));
            attributes.add(AttributeModel.ATTR_IS_DOCUMENT.toPrefixString(namespaceService));
        }

        Set<String> strAtts = new HashSet<>(attributes);

        Map<String, Class<?>> attJavaClasses = RecordsUtils.getAttributesClasses(journalType.getDataSource(),
                                                                                 strAtts,
                                                                                 Object.class,
                                                                                 recordsService);

        for (String attribute : attributes) {

            Map<String, String> attributeOptions = journalType.getAttributeOptions(attribute);

            schemaBuilder.append("a")
                    .append(attrCounter++)
                    .append(":edge(n:\"")
                    .append(attribute)
                    .append("\"){");

            schemaBuilder.append(getAttributeSchema(attributeOptions, attJavaClasses.get(attribute)));

            schemaBuilder.append("}");
        }

        return schemaBuilder.toString();
    }

    private String getAttributeSchema(Map<String, String> attributeOptions, Class<?> javaClass) {

        String schema = attributeOptions.get("attributeSchema");
        if (StringUtils.isNotBlank(schema)) {
            return "name,val:vals{" + schema + "}";
        }

        String formatter = attributeOptions.get("formatter");
        formatter = formatter != null ? formatter : "";

        StringBuilder schemaBuilder = new StringBuilder("name,val:vals{");

        // attributes
        Set<String> attributesToLoad = new HashSet<>();
        if (javaClass != null && QName.class.isAssignableFrom(javaClass)) {
            attributesToLoad.add("shortName");
        }

        Matcher attrMatcher = FORMATTER_ATTRIBUTES_PATTERN.matcher(formatter);
        if (attrMatcher.find()) {
            do {
                String attributes = attrMatcher.group(1);
                for (String attr : attributes.split(",")) {
                    attributesToLoad.add(attr.trim());
                }
            } while (attrMatcher.find());
        }

        if (formatter.contains("typeName")) {
            attributesToLoad.add("classTitle");
        }

        int attrCounter = 0;
        for (String attrName : attributesToLoad) {
            schemaBuilder.append("a")
                    .append(attrCounter++)
                    .append(":edge(n:\"")
                    .append(attrName).append("\")")
                    .append("{name val:vals{str:disp}}")
                    .append(",");
        }

        // inner fields
        List<String> innerFields = new ArrayList<>();

        Class dataType = javaClass != null ? javaClass : Object.class;
        boolean isNode = NodeRef.class.isAssignableFrom(dataType);
        boolean isQName = QName.class.isAssignableFrom(dataType);

        if (formatter.contains("Link") || formatter.contains("nodeRef")) {
            innerFields.add("id");
            innerFields.add("str:disp");
        } else if (attributesToLoad.isEmpty() || (!isNode && !isQName)) {
            innerFields.add("str:disp");
        }

        for (String field : innerFields) {
            schemaBuilder.append(field).append(",");
        }

        schemaBuilder.append("}");

        return schemaBuilder.toString();
    }

    public void clearCache() {
        gqlQueryWithDataByJournalId.clear();
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.namespaceService = serviceRegistry.getNamespaceService();
    }

    @Autowired
    public void setRecordsService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }
}
