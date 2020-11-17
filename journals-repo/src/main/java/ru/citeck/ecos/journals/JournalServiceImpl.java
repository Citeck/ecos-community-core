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
package ru.citeck.ecos.journals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ecos.com.fasterxml.jackson210.databind.JsonNode;
import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.namespace.NamespacePrefixResolver;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.utils.MandatoryParam;
import ru.citeck.ecos.graphql.journal.JGqlPageInfoInput;
import ru.citeck.ecos.invariants.Feature;
import ru.citeck.ecos.invariants.InvariantDefinition;
import ru.citeck.ecos.journals.invariants.CriterionInvariantsProvider;
import ru.citeck.ecos.journals.records.JournalRecordsDao;
import ru.citeck.ecos.journals.xml.Journal;
import ru.citeck.ecos.journals.xml.Journals;
import ru.citeck.ecos.journals.xml.Journals.Imports.Import;
import ru.citeck.ecos.model.ClassificationModel;
import ru.citeck.ecos.model.JournalsModel;
import ru.citeck.ecos.processor.TemplateExpressionEvaluator;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.search.SearchCriteria;
import ru.citeck.ecos.search.SearchCriteriaSettingsRegistry;
import ru.citeck.ecos.utils.*;

import javax.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

class JournalServiceImpl implements JournalService {

    protected static final String JOURNALS_SCHEMA_LOCATION = "alfresco/module/journals-repo/schema/journals.xsd";
    protected static final String INVARIANTS_SCHEMA_LOCATION = "alfresco/module/ecos-forms-repo/schema/invariants.xsd";

    @Autowired
    private RecordsService recordsService;

    private NodeService nodeService;
    private ServiceRegistry serviceRegistry;
    private SearchCriteriaSettingsRegistry searchCriteriaSettingsRegistry;
    private JournalRecordsDao recordsDao;
    private NamespaceService namespaceService;
    private NodeUtils nodeUtils;
    private NewUIUtils newUIUtils;
    private DictUtils dictUtils;

    private LazyNodeRef journalsRoot;
    private final Map<String, JournalType> journalTypes = new ConcurrentHashMap<>();

    @Autowired
    private TemplateExpressionEvaluator expressionEvaluator;
    @Autowired
    private AuthenticationService authenticationService;

    private final List<CriterionInvariantsProvider> criterionInvariantsProviders;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final LoadingCache<String, Optional<JournalType>> journalTypeByJournalIdOrRef;
    private final LoadingCache<JournalUserKey, String> uiTypeByJournal;

    public JournalServiceImpl() {
        criterionInvariantsProviders = Collections.synchronizedList(new ArrayList<>());

        journalTypeByJournalIdOrRef = CacheBuilder.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build(CacheLoader.from(this::getJournalTypeByIdOrNodeRefImpl));

        uiTypeByJournal = CacheBuilder.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build(CacheLoader.from(this::getUITypeImpl));
    }

    private static Map<String, String> getPrefixToUriMap(List<Import> namespaces) {
        Map<String, String> prefixToUriMap = new HashMap<>(namespaces.size());
        for (Import namespace : namespaces) {
            prefixToUriMap.put(namespace.getPrefix(), namespace.getUri());
        }
        return prefixToUriMap;
    }

    @Override
    public Long getRecordsCount(String journal) {

        NodeRef journalRef;
        String journalId;

        if (NodeRef.isNodeRef(journal)) {
            journalRef = new NodeRef(journal);
            if (!nodeService.exists(journalRef)) {
                return 0L;
            }
            journalId = (String) nodeService.getProperty(journalRef, JournalsModel.PROP_JOURNAL_TYPE);
        } else {
            journalId = journal;
            journalRef = getJournalRef(journal);
            if (journalRef == null) {
                return 0L;
            }
        }

        JGqlPageInfoInput page = new JGqlPageInfoInput(null, 1, Collections.emptyList(), 0);

        String query = buildJournalQuery(journalRef);
        RecordsQueryResult<RecordRef> result = getRecords(journalId, query, null, page);
        return result.getTotalCount();
    }

    @Override
    public void deployJournalTypes(InputStream inputStream) {
        Journals data = parseXML(inputStream);

        NamespacePrefixResolver prefixResolver =
            new NamespacePrefixResolverMapImpl(
                getPrefixToUriMap(data.getImports().getImport()));

        for (Journal journal : data.getJournal()) {
            searchCriteriaSettingsRegistry.cleanFieldNameCache(journal.getId());
            this.journalTypes.put(journal.getId(), new JournalTypeImpl(journal, prefixResolver, serviceRegistry,
                searchCriteriaSettingsRegistry));
            Map<String, String> options = journalTypes.get(journal.getId()).getOptions();
            String nodeType = options.get("type");
            if (nodeType != null) {
                searchCriteriaSettingsRegistry.registerJournalNodeType(journal.getId(), nodeType);
            }
            String staticQuery = options.get("staticQuery");
            if (staticQuery != null) {
                searchCriteriaSettingsRegistry.registerJournalStaticQuery(journal.getId(), staticQuery);
            }
        }
    }

    @Override
    public Optional<JournalType> getJournalForType(QName typeName) {

        String typeShortName = typeName.toPrefixString(namespaceService);

        Collection<JournalType> types = getAllJournalTypes();
        for (JournalType type : types) {
            if (typeShortName.equals(type.getOptions().get("type"))) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<InvariantDefinition> getCriterionInvariants(String journalId, String attribute) {

        JournalType journalType = journalTypes.get(journalId);

        Set<Feature> features = new HashSet<>();
        List<InvariantDefinition> invariants = new ArrayList<>();

        for (CriterionInvariantsProvider provider : criterionInvariantsProviders) {
            List<InvariantDefinition> provInvariants = provider.getInvariants(journalType, attribute);
            for (InvariantDefinition inv : provInvariants) {
                if (features.add(inv.getFeature())) {
                    invariants.add(inv);
                }
            }
        }

        return invariants;
    }

    private String buildJournalQuery(NodeRef journalRef) {
        List<NodeRef> criteriaRefs = RepoUtils.getChildrenByAssoc(
            journalRef, JournalsModel.ASSOC_SEARCH_CRITERIA, nodeService);
        if (CollectionUtils.isEmpty(criteriaRefs)) {
            return "{}";
        }
        SearchCriteria searchCriteria = new SearchCriteria(namespaceService);
        criteriaRefs.forEach(nodeRef -> {
            Map<QName, Serializable> props = nodeService.getProperties(nodeRef);
            QName fieldQName = (QName) props.get(JournalsModel.PROP_FIELD_QNAME);
            String predicate = (String) props.get(JournalsModel.PROP_PREDICATE);
            String criterionValue = (String) props.get(JournalsModel.PROP_CRITERION_VALUE);
            if (fieldQName == null || predicate == null || criterionValue == null) {
                return;
            }
            String field = fieldQName.toPrefixString(namespaceService);
            String criterion = "";
            if (StringUtils.isNotEmpty(criterionValue)) {
                Map<String, Object> model = new HashMap<>();
                /* this replacement is used to fix template strings like this one:
                 * <#list (people.getContainerGroups(person)![]) as group>#{group.nodeRef},</#list>#{person.nodeRef} */
                criterionValue = criterionValue.replace("#{", "${");
                criterion = (String) expressionEvaluator.evaluate(criterionValue, model);
            }
            searchCriteria.addCriteriaTriplet(field, predicate, criterion);
        });
        String criteria;
        try {
            criteria = objectMapper.writeValueAsString(searchCriteria);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Json processing error.", e);
        }
        return criteria;
    }

    protected Journals parseXML(InputStream inputStream) {
        try {
            Unmarshaller jaxbUnmarshaller = XMLUtils.createUnmarshaller(Journals.class,
                INVARIANTS_SCHEMA_LOCATION,
                JOURNALS_SCHEMA_LOCATION);
            return (Journals) jaxbUnmarshaller.unmarshal(inputStream);
        } catch (Exception e) {
            throw new IllegalArgumentException("Can not parse journals file", e);
        }
    }

    @Override
    public void clearCache() {
        for (CriterionInvariantsProvider provider : criterionInvariantsProviders) {
            provider.clearCache();
        }
        recordsDao.clearCache();
        uiTypeByJournal.invalidateAll();
        journalTypeByJournalIdOrRef.invalidateAll();
    }

    @Override
    public void clearCacheForUser(String username) {
        List<JournalUserKey> journalUserKeys = new ArrayList<>();
        uiTypeByJournal.asMap().forEach((k, v) -> {
            if (k.getUserName().equals(username)) {
                journalUserKeys.add(k);
            }
        });
        uiTypeByJournal.invalidateAll(journalUserKeys);
    }

    @Override
    public JournalType getJournalType(String id) {
        if (id == null) {
            return null;
        }
        Optional<JournalType> typeOpt = journalTypeByJournalIdOrRef.getUnchecked(id);
        return typeOpt.orElse(null);
    }

    @Override
    public JournalType getJournalType(NodeRef nodeRef) {
        return getJournalType(nodeRef.toString());
    }

    @Override
    public Collection<JournalType> getAllJournalTypes() {
        return new ArrayList<>(journalTypes.values());
    }

    @Override
    public NodeRef getJournalRef(String id) {
        List<ChildAssociationRef> associationRefs = nodeService.getChildAssocs(journalsRoot.getNodeRef(),
            ContentModel.ASSOC_CONTAINS,
            RegexQNamePattern.MATCH_ALL);
        for (ChildAssociationRef associationRef : associationRefs) {
            NodeRef journalRef = associationRef.getChildRef();
            String journalID = (String) nodeService.getProperty(journalRef, JournalsModel.PROP_JOURNAL_TYPE);
            if (Objects.equals(journalID, id)) {
                return journalRef;
            }
        }
        return null;
    }

    @Override
    public void registerCriterionInvariantsProvider(CriterionInvariantsProvider provider) {
        criterionInvariantsProviders.add(provider);
        criterionInvariantsProviders.sort(null);
    }

    @Override
    public RecordsQueryResult<RecordRef> getRecords(String journalId,
                                                    String query,
                                                    String language,
                                                    JGqlPageInfoInput pageInfo,
                                                    boolean debug) {
        if (pageInfo == null) {
            pageInfo = JGqlPageInfoInput.DEFAULT;
        }
        JournalType journalType = needJournalType(journalId);
        return recordsDao.getRecords(journalType, query, language, pageInfo, debug);
    }

    @Override
    public RecordsQueryResult<ObjectData> getRecordsWithData(String journalId,
                                                             String query,
                                                             String language,
                                                             JGqlPageInfoInput pageInfo,
                                                             boolean debug) {
        if (pageInfo == null) {
            pageInfo = JGqlPageInfoInput.DEFAULT;
        }
        JournalType journalType = needJournalType(journalId);
        return new RecordsQueryResult<>(
            recordsDao.getRecordsWithData(journalType, query, language, pageInfo, debug),
            meta -> {
                ObjectData attributes = meta.getAttributes();
                attributes.set("id", meta.getId().toString());
                return attributes;
            }
        );
    }

    @Override
    public String getJournalGqlSchema(String journalId) {
        return recordsDao.getJournalGqlSchema(needJournalType(journalId));
    }

    @Override
    public JournalType needJournalType(String journalIdOrRef) {
        MandatoryParam.check("journalIdOrRef", journalIdOrRef);
        Optional<JournalType> typeOpt = journalTypeByJournalIdOrRef.getUnchecked(journalIdOrRef);
        if (!typeOpt.isPresent()) {
            throw new IllegalArgumentException("Journal with id " + journalIdOrRef + " is not found");
        }
        return typeOpt.orElse(null);
    }

    private Optional<JournalType> getJournalTypeByIdOrNodeRefImpl(String journalIdOrRef) {

        MandatoryParam.check("journalIdOrRef", journalIdOrRef);

        if (journalIdOrRef.startsWith("alf_")) {

            QName typeQName = QName.resolveToQName(namespaceService, journalIdOrRef.substring(4));
            return typeQName != null ? getJournalForType(typeQName) : Optional.empty();

        } else {

            String journalId;

            if (NodeRef.isNodeRef(journalIdOrRef)) {

                journalId = AuthenticationUtil.runAsSystem(() ->
                    nodeUtils.getProperty(new NodeRef(journalIdOrRef), JournalsModel.PROP_JOURNAL_TYPE)
                );
            } else {
                journalId = journalIdOrRef;
            }

            return Optional.ofNullable(journalTypes.get(journalId));
        }
    }

    @Override
    public String getUIType(String journalId) {
        return uiTypeByJournal.getUnchecked(new JournalUserKey(journalId, authenticationService.getCurrentUserName()));
    }

    public String getUITypeImpl(JournalUserKey journalUserKey) {

        String journalId = journalUserKey.getJournalId();

        if (StringUtils.isBlank(journalId)) {
            return "";
        }

        if (NodeRef.isNodeRef(journalId)) {
            NodeRef journalRef = new NodeRef(journalId);
            NodeRef ecosType = (NodeRef) nodeService.getProperty(journalRef, ClassificationModel.PROP_RELATES_TO_TYPE);
            if (ecosType != null) {
                RecordRef typeRef = RecordRef.create("emodel", "type", ecosType.getId());
                return getUITypeForRecordAndUser(typeRef, journalUserKey.getUserName());
            }
        }

        Optional<JournalType> optJournal = getJournalTypeByIdOrNodeRefImpl(journalId);
        if (!optJournal.isPresent()) {
            RecordRef journalRef = RecordRef.create("uiserv", "journal", journalId);
            JsonNode type = recordsService.getAttribute(journalRef, "typeRef?id").getValue();

            if (type != null) {
                RecordRef typeRef = RecordRef.valueOf(type.asText());
                return getUITypeForRecordAndUser(typeRef, journalUserKey.getUserName());
            }

            return "";
        }

        JournalType journal = optJournal.get();

        String typeRefOption = journal.getOptions().get("typeRef");
        if (StringUtils.isNotBlank(typeRefOption)) {
            return getUITypeForRecordAndUser(RecordRef.valueOf(typeRefOption), journalUserKey.getUserName());
        } else {

            String type = journal.getOptions().get("type");

            if (StringUtils.isNotBlank(type)) {
                PropertyDefinition propDef = dictUtils.getPropDef(type, ClassificationModel.PROP_DOCUMENT_TYPE);
                if (propDef != null) {
                    String value = propDef.getDefaultValue();
                    if (StringUtils.isNotBlank(value) && NodeRef.isNodeRef(value)) {
                        NodeRef typeNodeRef = new NodeRef(value);
                        RecordRef typeRef = RecordRef.create("emodel", "type", typeNodeRef.getId());
                        return getUITypeForRecordAndUser(typeRef, journalUserKey.getUserName());
                    }
                }
            }
        }

        return "";
    }

    private String getUITypeForRecordAndUser(RecordRef typeRef, String userName) {
        String uiType = newUIUtils.getUITypeForRecord(typeRef);

        if (StringUtils.isBlank(uiType)) {
            uiType = newUIUtils.isNewUIEnabledForUser(userName) ? NewUIUtils.UI_TYPE_REACT : "";
        }

        return uiType;
    }

    @Autowired
    public void setNewUIUtils(NewUIUtils newUIUtils) {
        this.newUIUtils = newUIUtils;
    }

    public void setJournalsRoot(LazyNodeRef journalsRoot) {
        this.journalsRoot = journalsRoot;
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        this.nodeService = serviceRegistry.getNodeService();
    }

    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    public void setRecordsDao(JournalRecordsDao recordsDao) {
        this.recordsDao = recordsDao;
    }

    public void setSearchCriteriaSettingsRegistry(SearchCriteriaSettingsRegistry searchCriteriaSettingsRegistry) {
        this.searchCriteriaSettingsRegistry = searchCriteriaSettingsRegistry;
    }

    public void setNamespaceService(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    public SearchCriteriaSettingsRegistry getSearchCriteriaSettingsRegistry() {
        return searchCriteriaSettingsRegistry;
    }

    @Autowired
    public void setNodeUtils(NodeUtils nodeUtils) {
        this.nodeUtils = nodeUtils;
    }

    @Autowired
    public void setDictUtils(DictUtils dictUtils) {
        this.dictUtils = dictUtils;
    }

    @Data
    @AllArgsConstructor
    private static class JournalUserKey {
        private String journalId;
        private String userName;
    }
}
