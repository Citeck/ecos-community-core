package ru.citeck.ecos.icase.completeness.records;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.search.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.icase.completeness.CaseCompletenessService;
import ru.citeck.ecos.model.ClassificationModel;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.result.RecordsResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDAO;
import ru.citeck.ecos.search.ftsquery.FTSQuery;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CaseDocumentRecordsDAO extends LocalRecordsDAO
    implements LocalRecordsQueryWithMetaDAO {

    public final static String ID = "documents";
    private static final String DOCUMENT_TYPES_QUERY_LANGUAGE = "document-types";
    private static final String TYPES_DOCUMENTS_QUERY_LANGUAGE = "types-documents";

    private final CaseCompletenessService caseCompletenessService;
    private final SearchService searchService;

    @Autowired
    public CaseDocumentRecordsDAO(@Qualifier("caseCompletenessService")
                                      CaseCompletenessService caseCompletenessService,
                                  SearchService searchService) {
        setId(ID);
        this.caseCompletenessService = caseCompletenessService;
        this.searchService = searchService;
    }

    @Override
    public RecordsQueryResult<?> queryLocalRecords(RecordsQuery recordsQuery, MetaField field) {

        switch (recordsQuery.getLanguage()) {
            case DOCUMENT_TYPES_QUERY_LANGUAGE:
                return getDocumentTypes(recordsQuery);
            case TYPES_DOCUMENTS_QUERY_LANGUAGE:
                return getTypesDocuments(recordsQuery);
            default:
                log.error("Language doesn't supported: " + recordsQuery.getLanguage());
        }

        return new RecordsQueryResult<>();
    }

    private RecordsQueryResult<TypeDocumentsRecord> getTypesDocuments(RecordsQuery recordsQuery) {

        TypesDocumentsQuery query = recordsQuery.getQuery(TypesDocumentsQuery.class);

        RecordRef recordRef = query.getRecordRef();
        List<RecordRef> typesRefs = query.getTypes();

        if (recordRef == null || !NodeRef.isNodeRef(recordRef.getId()) || typesRefs == null || typesRefs.isEmpty()) {
            return new RecordsQueryResult<>();
        }

        FTSQuery ftsQuery = FTSQuery.createRaw()
            .parent(new NodeRef(recordRef.getId()))
            .and()
            .open();

        for (RecordRef typeRef : typesRefs) {

            String[] typeParts = typeRef.getId().split("/");
            String tkType = "workspace://SpacesStore/" + typeParts[0];

            ftsQuery.value(ClassificationModel.PROP_DOCUMENT_TYPE, tkType).or();
        }

        ftsQuery.close()
            .transactional()
            .maxItems(5000);

        List<RecordRef> documentRefs = ftsQuery.query(searchService)
            .stream()
            .map(ref -> RecordRef.valueOf(ref.toString()))
            .collect(Collectors.toList());

        RecordsResult<DocumentTypeMeta> meta = recordsService.getMeta(documentRefs, DocumentTypeMeta.class);

        Map<RecordRef, List<RecordRef>> docsByType = new HashMap<>();

        for (int i = 0; i < meta.getRecords().size(); i++) {

            DocumentTypeMeta docMeta = meta.getRecords().get(i);

            if (docMeta.type != null) {
                docsByType.computeIfAbsent(docMeta.getType(), t -> new ArrayList<>()).add(documentRefs.get(i));
            }
        }

        List<TypeDocumentsRecord> typeDocumentsList = new ArrayList<>();

        for (RecordRef typeRef : typesRefs) {
            typeDocumentsList.add(new TypeDocumentsRecord(typeRef,
                docsByType.getOrDefault(typeRef, Collections.emptyList())));
        }

        RecordsQueryResult<TypeDocumentsRecord> typeDocumentsRecords = new RecordsQueryResult<>();
        typeDocumentsRecords.setRecords(typeDocumentsList);

        return typeDocumentsRecords;
    }

    private RecordsQueryResult<CaseDocumentRecord> getDocumentTypes(RecordsQuery recordsQuery) {

        RecordsQueryResult<CaseDocumentRecord> result = new RecordsQueryResult<>();

        DocumentTypesQuery queryData = recordsQuery.getQuery(DocumentTypesQuery.class);
        String recordRefStr = queryData.recordRef;

        RecordRef recordRef = RecordRef.valueOf(recordRefStr);

        if (!NodeRef.isNodeRef(recordRef.getId())) {
            log.warn("RecordRef id is not nodeRef");
            result.setRecords(Collections.emptyList());
            return result;
        }

        NodeRef nodeRef = new NodeRef(recordRef.getId());

        List<CaseDocumentRecord> documentRecords = caseCompletenessService.getCaseDocuments(nodeRef).stream()
            .map(CaseDocumentRecord::new)
            .collect(Collectors.toList());

        result.setRecords(documentRecords);

        return result;
    }

    @Data
    @RequiredArgsConstructor
    static class TypeDocumentsRecord implements MetaValue {

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
                    return documents;
                case "docsCount":
                    return documents.size();
            }
            return null;
        }
    }

    @Data
    private static class DocumentTypeMeta {

        @MetaAtt("_etype?id")
        private RecordRef type;
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
