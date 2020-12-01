package ru.citeck.ecos.doclib.service;

import lombok.Data;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.extensions.surf.util.ParameterCheck;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.utils.MandatoryParam;
import ru.citeck.ecos.doclib.api.records.DocLibRecords;
import ru.citeck.ecos.model.lib.type.dto.DocLibDef;
import ru.citeck.ecos.model.lib.type.dto.TypeDef;
import ru.citeck.ecos.model.lib.type.service.TypeDefService;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeRecord;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.OrPredicate;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.op.atts.service.schema.annotation.AttName;
import ru.citeck.ecos.records3.record.op.query.dto.RecsQueryRes;
import ru.citeck.ecos.records3.record.op.query.dto.query.QueryPage;
import ru.citeck.ecos.records3.record.op.query.dto.query.RecordsQuery;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DocLibService {

    private static final String DOCLIB_TYPE_REF_ATT = "docLibTypeRef";

    private static final DocLibNodeInfo EMPTY_NODE = new DocLibNodeInfo(
        RecordRef.EMPTY,
        DocLibNodeType.FILE,
        "",
        RecordRef.EMPTY
    );

    private final EcosTypeService ecosTypeService;
    private final TypeDefService typeDefService;
    private final RecordsService recordsService;
    private final NamespaceService namespaceService;
    private final AlfNodesRecordsDAO alfNodesRecordsDao;

    @Autowired
    public DocLibService(EcosTypeService ecosTypeService,
                         TypeDefService typeDefService,
                         RecordsService recordsService,
                         NamespaceService namespaceService,
                         AlfNodesRecordsDAO alfNodesRecordsDao) {

        this.ecosTypeService = ecosTypeService;
        this.typeDefService = typeDefService;
        this.recordsService = recordsService;
        this.namespaceService = namespaceService;
        this.alfNodesRecordsDao = alfNodesRecordsDao;
    }

    public RecordRef createEntity(ObjectData attributes) {

        attributes = attributes.deepCopy();

        String docLibType = attributes.get(DOCLIB_TYPE_REF_ATT).asText();
        MandatoryParam.checkString(DOCLIB_TYPE_REF_ATT, docLibType);
        RecordRef docLibTypeRef = RecordRef.valueOf(docLibType);
        attributes.remove(DOCLIB_TYPE_REF_ATT);

        String ecosType = attributes.get(RecordConstants.ATT_TYPE).asText();
        ParameterCheck.mandatoryString(RecordConstants.ATT_TYPE, ecosType);
        RecordRef typeRef = RecordRef.valueOf(ecosType);

        DocLibDef docLib = typeDefService.getDocLib(docLibTypeRef);
        Set<RecordRef> allowedTypes = new HashSet<>(docLib.getFileTypeRefs());
        allowedTypes.add(docLib.getDirTypeRef());

        if (!allowedTypes.contains(typeRef)) {
            throw new IllegalArgumentException("Incorrect typeRef: '"
                + typeRef + "' for documents library: " + docLib);
        }

        QName nodeType;
        String currentAlfType = attributes.get(AlfNodeRecord.ATTR_TYPE).asText();
        if (StringUtils.isNotBlank(currentAlfType)) {
            nodeType = QName.resolveToQName(namespaceService, currentAlfType);
        } else {
            if (docLib.getDirTypeRef().equals(typeRef)) {
                nodeType = ContentModel.TYPE_FOLDER;
            } else {
                nodeType = ContentModel.TYPE_CONTENT;
            }
        }

        attributes.set(AlfNodeRecord.ATTR_TYPE, nodeType.toPrefixString(namespaceService));

        String parent = attributes.get(RecordConstants.ATT_PARENT).asText();
        if (StringUtils.isBlank(parent)) {
            DocLibNodeInfo rootForType = getRootForType(docLibTypeRef, true);
            parent = rootForType.getRecordRef().getId();
        } else {
            RecordRef parentRef = RecordRef.valueOf(parent);
            if (!parentRef.getSourceId().equals(DocLibRecords.SOURCE_ID)) {
                throw new IllegalArgumentException("Incorrect parent: '" + parentRef
                    + "'. Expected sourceId: '" + DocLibRecords.SOURCE_ID + "'");
            }
            parent = parentRef.getId();
        }
        attributes.set(RecordConstants.ATT_PARENT, parent);

        RecordsMutation mutation = new RecordsMutation();
        mutation.addRecord(new RecordMeta(RecordRef.EMPTY, attributes));

        RecordsMutResult result = alfNodesRecordsDao.mutate(mutation);
        List<RecordMeta> resultRecords = result.getRecords();

        if (resultRecords.isEmpty()) {
            throw new IllegalStateException("Mutation return nothing. Attributes: " + attributes);
        }
        return RecordRef.create(DocLibRecords.SOURCE_ID, result.getRecords().get(0).getId());
    }

    @NotNull
    public DocLibNodeInfo getRootForType(RecordRef typeRef, boolean createIfNotExists) {

        if (RecordRef.isEmpty(typeRef)) {
            return EMPTY_NODE;
        }

        TypeDef typeDef = typeDefService.getTypeDef(typeRef);
        if (typeDef == null) {
            return EMPTY_NODE;
        }
        String name = MLText.getClosestValue(typeDef.getName(), I18NUtil.getLocale());

        NodeRef rootNodeRef = ecosTypeService.getRootForType(typeRef, createIfNotExists);
        RecordRef rootRef;
        if (rootNodeRef == null) {
            rootRef = RecordRef.create(DocLibRecords.SOURCE_ID, "");
        } else {
            rootRef = RecordRef.create(DocLibRecords.SOURCE_ID, rootNodeRef.toString());
        }

        return new DocLibNodeInfo(rootRef, DocLibNodeType.DIR, name, RecordRef.EMPTY);
    }

    @NotNull
    public DocLibNodeInfo getDocLibNodeInfo(@Nullable RecordRef docLibRef, @Nullable RecordRef typeRef) {

        if (docLibRef == null || typeRef == null) {
            return EMPTY_NODE;
        }

        if (RecordRef.isEmpty(docLibRef) && RecordRef.isEmpty(typeRef)) {
            return EMPTY_NODE;
        }

        String nodeRef = docLibRef.getId();
        if (StringUtils.isBlank(nodeRef) || !NodeRef.isNodeRef(nodeRef)) {
            return EMPTY_NODE;
        }
        DocLibEntityInfo info = recordsService.getAtts(RecordRef.create("", nodeRef), DocLibEntityInfo.class);

        if (RecordRef.isEmpty(info.typeRef)) {
            return EMPTY_NODE;
        }

        if (StringUtils.isBlank(info.getDisplayName())) {
            info.setDisplayName(docLibRef.toString());
        }

        DocLibNodeType nodeType;
        DocLibDef docLib = typeDefService.getDocLib(typeRef);
        if (info.getTypeRef().equals(docLib.getDirTypeRef())) {
            nodeType = DocLibNodeType.DIR;
        } else if (docLib.getFileTypeRefs().contains(info.getTypeRef())) {
            nodeType = DocLibNodeType.FILE;
        } else {
            return EMPTY_NODE;
        }

        return new DocLibNodeInfo(docLibRef, nodeType, info.getDisplayName(), typeRef);
    }

    public RecsQueryRes<RecordRef> getChildren(DocLibChildrenQuery query, QueryPage page) {

        if (RecordRef.isEmpty(query.getTypeRef())) {
            return new RecsQueryRes<>();
        }
        if (page == null) {
            page = new QueryPage(1000, 0, RecordRef.EMPTY);
        }

        RecordRef parentRef = query.getParentRef();
        if (RecordRef.isEmpty(parentRef)) {
            parentRef = getRootForType(query.getTypeRef(), false).getRecordRef();
        }
        if (parentRef == null || parentRef.getId().isEmpty()) {
            return new RecsQueryRes<>();
        }

        DocLibDef docLibDef = typeDefService.getDocLib(query.getTypeRef());

        boolean includeDirs = query.getNodeType() == null || query.getNodeType().equals(DocLibNodeType.DIR);
        boolean includeFiles = query.getNodeType() == null || query.getNodeType().equals(DocLibNodeType.FILE);

        OrPredicate typesPredicate = new OrPredicate();
        if (includeDirs && RecordRef.isNotEmpty(docLibDef.getDirTypeRef())) {
            typeDefService.expandTypeWithChildren(docLibDef.getDirTypeRef()).forEach(
                ref -> typesPredicate.addPredicate(Predicates.eq(RecordConstants.ATT_TYPE, ref))
            );
        }
        if (includeFiles) {
            for (RecordRef fileType : docLibDef.getFileTypeRefs()) {
                typeDefService.expandTypeWithChildren(fileType).forEach(
                    ref -> typesPredicate.addPredicate(Predicates.eq(RecordConstants.ATT_TYPE, ref))
                );
            }
        }

        RecordsQuery recordsQuery = RecordsQuery.create()
            .withLanguage(PredicateService.LANGUAGE_PREDICATE)
            .withQuery(Predicates.and(
                Predicates.eq("PARENT", parentRef.getId()),
                typesPredicate
            ))
            .withPage(page)
            .build();

        RecsQueryRes<RecordRef> childrenQueryRes = recordsService.query(recordsQuery);

        List<RecordRef> children = childrenQueryRes.getRecords()
            .stream()
            .map(r -> RecordRef.create(DocLibRecords.SOURCE_ID, r.getId()))
            .collect(Collectors.toList());

        RecsQueryRes<RecordRef> res = new RecsQueryRes<>();
        res.setRecords(children);
        res.setHasMore(childrenQueryRes.getHasMore());
        res.setTotalCount(childrenQueryRes.getTotalCount());

        return res;
    }

    @Data
    public static class DocLibEntityInfo {
        @AttName("type?id")
        private RecordRef typeRef;
        @AttName("?disp")
        private String displayName;
    }
}
