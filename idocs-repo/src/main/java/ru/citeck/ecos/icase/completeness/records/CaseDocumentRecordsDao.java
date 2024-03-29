package ru.citeck.ecos.icase.completeness.records;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.dictionary.ClassDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.QName;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.icase.completeness.CaseCompletenessService;
import ru.citeck.ecos.model.EcosModel;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.result.RecordsResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.records3.RecordsProperties;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.utils.DictUtils;
import ru.citeck.ecos.utils.NodeUtils;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CaseDocumentRecordsDao extends LocalRecordsDao implements LocalRecordsQueryWithMetaDao {

    public final static String ID = "documents";
    private static final String DOCUMENT_TYPES_QUERY_LANGUAGE = "document-types";
    private static final String TYPES_DOCUMENTS_QUERY_LANGUAGE = "types-documents";
    private static final String DOCUMENTS_QUERY_LANGUAGE = "documents";
    private static final String BASE_ECOS_TYPE_ID = "base";

    private final NodeService nodeService;
    private final NodeUtils nodeUtils;
    private final DictUtils dictUtils;
    private final CaseCompletenessService caseCompletenessService;
    private final SearchService searchService;
    private final EcosTypeService ecosTypeService;
    private final AuthorityUtils authorityUtils;
    private final RecordsProperties recordsProperties;
    private final EcosWebAppProps ecosWebAppProperties;

    private final Map<QName, Map<RecordRef, QName>> assocTypesRegistry = new ConcurrentHashMap<>();
    private final LoadingCache<QName, Map<RecordRef, QName>> assocTypesByCaseAlfTypeCache;

    @Autowired
    public CaseDocumentRecordsDao(@Qualifier("caseCompletenessService")
                                      CaseCompletenessService caseCompletenessService,
                                  EcosTypeService ecosTypeService,
                                  AuthorityUtils authorityUtils,
                                  SearchService searchService,
                                  NodeService nodeService,
                                  NodeUtils nodeUtils,
                                  DictUtils dictUtils,
                                  RecordsProperties recordsProperties,
                                  EcosWebAppProps ecosWebAppProperties) {
        setId(ID);
        this.caseCompletenessService = caseCompletenessService;
        this.ecosTypeService = ecosTypeService;
        this.authorityUtils = authorityUtils;
        this.searchService = searchService;
        this.nodeService = nodeService;
        this.nodeUtils = nodeUtils;
        this.dictUtils = dictUtils;
        this.recordsProperties = recordsProperties;
        this.ecosWebAppProperties = ecosWebAppProperties;

        assocTypesByCaseAlfTypeCache = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.SECONDS)
            .maximumSize(200)
            .build(CacheLoader.from(this::getAssocTypesForType));
    }

    @Override
    public RecordsQueryResult<?> queryLocalRecords(RecordsQuery recordsQuery, MetaField field) {

        switch (recordsQuery.getLanguage()) {
            case DOCUMENT_TYPES_QUERY_LANGUAGE:
                return getDocumentTypes(recordsQuery);
            case TYPES_DOCUMENTS_QUERY_LANGUAGE:
                return getTypesDocuments(recordsQuery);
            case DOCUMENTS_QUERY_LANGUAGE:
                return getDocumentsOfAllTypes(recordsQuery);
            default:
                log.error("Language doesn't supported: " + recordsQuery.getLanguage());
        }

        return new RecordsQueryResult<>();
    }

    private RecordsQueryResult<TypeDocumentsRecord> getTypesDocuments(RecordsQuery recordsQuery) {

        TypesDocumentsQuery query = recordsQuery.getQuery(TypesDocumentsQuery.class);
        List<RecordRef> typesRefs = query.getTypes();

        if (typesRefs == null || typesRefs.isEmpty()) {
            return new RecordsQueryResult<>();
        }

        Map<RecordRef, List<DocInfo>> docsByType = getAllDocsForCase(query.getRecordRef());

        List<TypeDocumentsRecord> typeDocumentsList = typesRefs.stream()
            .map(typeRef -> new TypeDocumentsRecord(typeRef, docsByType.getOrDefault(typeRef, Collections.emptyList())
                .stream()
                .map(DocInfo::getRef)
                .collect(Collectors.toList())))
            .collect(Collectors.toList());

        RecordsQueryResult<TypeDocumentsRecord> typeDocumentsRecords = new RecordsQueryResult<>();
        typeDocumentsRecords.setRecords(typeDocumentsList);
        return typeDocumentsRecords;
    }

    private RecordsQueryResult<TypeDocumentsRecord> getDocumentsOfAllTypes(RecordsQuery recordsQuery) {

        TypesDocumentsQuery query = recordsQuery.getQuery(TypesDocumentsQuery.class);

        Map<RecordRef, List<DocInfo>> docsByType = getAllDocsForCase(query.getRecordRef());

        List<TypeDocumentsRecord> documentsByTypes = docsByType.entrySet().stream()
            .map(e -> new TypeDocumentsRecord(e.getKey(), e.getValue().stream()
                .map(DocInfo::getRef)
                .collect(Collectors.toList())))
            .collect(Collectors.toList());

        RecordsQueryResult<TypeDocumentsRecord> documentsByTypesRecords = new RecordsQueryResult<>();
        documentsByTypesRecords.setRecords(documentsByTypes);
        return documentsByTypesRecords;
    }

    /**
     * Get hash value to compare documents state before and after some changes
     * If after some changes getAllDocsHash not equal to getAllDocsHash before then documents was changed.
     * Changes which will affect to this hash: documents modification or deletion or creation
     */
    public String getAllDocsHash(RecordRef caseRef) {
        Map<RecordRef, List<DocInfo>> allDocs = getAllDocsForCase(caseRef);
        List<RecordRef> types = new ArrayList<>(allDocs.keySet());
        types.sort(Comparator.comparing(RecordRef::getId));
        long hash = 1;
        long docsCount = 0;
        for (RecordRef typeRef : types) {
            List<DocInfo> docsInfo = allDocs.get(typeRef);
            if (docsInfo != null) {
                for (DocInfo info : docsInfo) {
                    hash = 31 * hash + info.modifiedMs;
                }
                docsCount += docsInfo.size();
            }
        }
        return hash + "-" + docsCount;
    }

    private Map<RecordRef, List<DocInfo>> getAllDocsForCase(RecordRef caseRef) {

        NodeRef nodeRef = convertRecordRefToNodeRef(caseRef);

        FTSQuery ftsQuery = FTSQuery.createRaw()
            .transactional()
            .maxItems(1000);

        if (nodeRef != null) {
            ftsQuery = ftsQuery.parent(nodeRef);
        } else {
            ftsQuery = ftsQuery.exact(EcosModel.PROP_REMOTE_PARENT_REF, caseRef.toString());
        }

        List<RecordRef> documentRefs = ftsQuery.query(searchService)
            .stream()
            .map(ref -> RecordRef.valueOf(ref.toString()))
            .collect(Collectors.toList());

        RecordsResult<DocumentTypeMeta> meta = recordsService.getMeta(documentRefs, DocumentTypeMeta.class);

        Map<RecordRef, Set<DocInfo>> docsByType = new HashMap<>();
        Set<DocInfo> allDocuments = new HashSet<>();

        for (int i = 0; i < meta.getRecords().size(); i++) {

            DocumentTypeMeta docMeta = meta.getRecords().get(i);

            if (docMeta.type != null) {

                long order = docMeta.getCreated() != null ? docMeta.getCreated().getTime() : 0L;
                long modifiedMs = docMeta.getModified() != null ? docMeta.getModified().getTime() : 0L;

                DocInfo docInfo = new DocInfo(documentRefs.get(i), order, modifiedMs);

                if (!BASE_ECOS_TYPE_ID.equals(docMeta.getType().getId())) {
                    allDocuments.add(docInfo);
                    docsByType.computeIfAbsent(docMeta.getType(), t -> new HashSet<>()).add(docInfo);
                }
            }
        }

        if (nodeRef != null) {
            getAllDocsByAssocsRegistry(nodeRef).forEach((type, docs) -> {
                Set<DocInfo> docsSet = docsByType.computeIfAbsent(type, t -> new HashSet<>());
                docs.stream().filter(d -> !allDocuments.contains(d)).forEach(docsSet::add);
            });
        }

        Set<RecordRef> typeRefs = new HashSet<>(docsByType.keySet());
        for (RecordRef typeRef : typeRefs) {
            Set<DocInfo> documents = docsByType.get(typeRef);
            ecosTypeService.forEachAscRef(typeRef, ref -> {
                if (typeRef == null || typeRef.getId().isEmpty() || typeRef.equals(ref)) {
                    return false;
                }
                docsByType.computeIfAbsent(ref, t -> new HashSet<>()).addAll(documents);
                return false;
            });
        }

        Map<RecordRef, List<DocInfo>> orderedDocsByType = new HashMap<>();
        docsByType.forEach((type, docs) -> {
            List<DocInfo> docsList = orderedDocsByType.computeIfAbsent(type, t -> new ArrayList<>());
            docsList.addAll(docs);
            docsList.sort(Comparator.comparingLong(DocInfo::getOrder).reversed());
        });

        return orderedDocsByType;
    }

    @Nullable
    private NodeRef convertRecordRefToNodeRef(RecordRef recordRef) {
        if (EntityRef.isEmpty(recordRef)) {
            return null;
        }
        if (authorityUtils.isAuthorityRef(recordRef)) {
            return authorityUtils.getNodeRefNotNull(recordRef);
        }
        if (nodeUtils.isNodeRef(recordRef.getId())) {
            return new NodeRef(recordRef.getId());
        }
        return null;
    }

    private Map<RecordRef, List<DocInfo>> getAllDocsByAssocsRegistry(NodeRef caseRef) {

        QName caseDocumentType = nodeService.getType(caseRef);

        Map<RecordRef, QName> assocsByEcosType = assocTypesByCaseAlfTypeCache.getUnchecked(caseDocumentType);
        Map<RecordRef, List<DocInfo>> documents = new HashMap<>();

        assocsByEcosType.forEach((ecosTypeRef, assocName) -> {

            List<NodeRef> assocsRecordRefs = nodeUtils.getAssocTargets(caseRef, assocName);
            if (!assocsRecordRefs.isEmpty()) {
                documents.put(ecosTypeRef, assocsRecordRefs.stream()
                    .map(this::nodeRefToDocInfo)
                    .collect(Collectors.toList())
                );
            }
        });

        return documents;
    }

    private DocInfo nodeRefToDocInfo(NodeRef nodeRef) {

        Map<QName, Serializable> properties = nodeService.getProperties(nodeRef);

        Date created = (Date) properties.get(ContentModel.PROP_CREATED);
        long createdMs = created != null ? created.getTime() : 0L;
        Date modified = (Date) properties.get(ContentModel.PROP_MODIFIED);
        long modifiedMs = modified != null ? modified.getTime() : 0L;

        return new DocInfo(RecordRef.create("", nodeRef.toString()), createdMs, modifiedMs);
    }

    private Map<RecordRef, QName> getAssocTypesForType(QName typeName) {

        if (typeName == null) {
            return Collections.emptyMap();
        }

        ClassDefinition typeDef = dictUtils.getTypeDefinition(typeName);

        List<QName> types = new ArrayList<>();

        while (typeDef != null) {
            types.add(typeDef.getName());
            typeDef = typeDef.getParentClassDefinition();
        }

        Map<RecordRef, QName> assocsByEcosType = new HashMap<>();

        for (int i = types.size() - 1; i >= 0; i--) {
            Map<RecordRef, QName> forType = assocTypesRegistry.get(types.get(i));
            if (forType != null) {
                assocsByEcosType.putAll(forType);
            }
        }
        return assocsByEcosType;
    }

    private RecordsQueryResult<CaseDocumentRecord> getDocumentTypes(RecordsQuery recordsQuery) {

        RecordsQueryResult<CaseDocumentRecord> result = new RecordsQueryResult<>();

        DocumentTypesQuery queryData = recordsQuery.getQuery(DocumentTypesQuery.class);
        String recordRefStr = queryData.recordRef;

        NodeRef nodeRef = convertRecordRefToNodeRef(RecordRef.valueOf(recordRefStr));
        if (nodeRef == null) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        List<CaseDocumentRecord> documentRecords = caseCompletenessService.getCaseDocuments(nodeRef).stream()
            .map(CaseDocumentRecord::new)
            .collect(Collectors.toList());

        result.setRecords(documentRecords);

        return result;
    }

    public void register(CaseAssocToEcosType caseAssocToEcosType) {

        RecordRef typeRef = RecordRef.valueOf(caseAssocToEcosType.getEcosTypeRef());

        assocTypesRegistry.computeIfAbsent(caseAssocToEcosType.getAlfType(), t -> new ConcurrentHashMap<>())
                          .put(typeRef, caseAssocToEcosType.getAssocName());
    }

    @Data
    @RequiredArgsConstructor
    class TypeDocumentsRecord implements MetaValue {

        private final String id = UUID.randomUUID().toString();

        private final RecordRef typeRef;
        private final List<RecordRef> documents;

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Object getAttribute(String name, MetaField field) {

            switch (name) {
                case "type":
                    return typeRef.toString();
                case "documents":
                    String appName = ecosWebAppProperties.getAppName();
                    return documents.stream()
                        .map(doc -> RecordRef.create(appName, "", doc.getId()))
                        .collect(Collectors.toList());
                case "docsCount":
                    return documents.size();
            }
            return null;
        }
    }

    @Data
    @AllArgsConstructor
    private static class DocInfo {

        private RecordRef ref;
        private long order;
        private long modifiedMs;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DocInfo docInfo = (DocInfo) o;
            return Objects.equals(ref, docInfo.ref);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ref);
        }
    }

    @Data
    public static class DocumentTypeMeta {
        @AttName("_etype?id")
        private RecordRef type;
        @AttName("cm:created?str")
        private Date created;
        @AttName("cm:modified?str")
        private Date modified;
    }

    @Data
    private static class TypesDocumentsQuery {

        private RecordRef recordRef;
        private List<RecordRef> types;
    }

    @Data
    private static class DocumentTypesQuery {

        private String recordRef;
    }
}
