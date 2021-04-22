package ru.citeck.ecos.doclib.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
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
import ru.citeck.ecos.doclib.api.records.DocLibRecords;
import ru.citeck.ecos.model.lib.type.dto.DocLibDef;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.node.etype.EcosTypeRootService;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeRecord;
import ru.citeck.ecos.records.type.TypeDto;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.OrPredicate;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records2.predicate.model.VoidPredicate;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.records3.record.dao.query.dto.query.QueryPage;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class DocLibService {

    public static final String TYPE_DELIM = "$";

    private static final DocLibNodeInfo EMPTY_NODE = new DocLibNodeInfo(
        RecordRef.EMPTY,
        DocLibNodeType.FILE,
        "",
        RecordRef.EMPTY,
        RecordRef.EMPTY,
        null,
        null,
        null,
        null,
        () -> null
    );

    private final EcosTypeRootService ecosTypeRootService;
    private final EcosTypeService ecosTypeService;
    private final RecordsService recordsService;
    private final NamespaceService namespaceService;
    private final AlfNodesRecordsDAO alfNodesRecordsDao;
    private final NodeService nodeService;

    @Autowired
    public DocLibService(EcosTypeRootService ecosTypeRootService,
                         EcosTypeService ecosTypeService,
                         RecordsService recordsService,
                         NamespaceService namespaceService,
                         AlfNodesRecordsDAO alfNodesRecordsDao,
                         NodeService nodeService) {

        this.nodeService = nodeService;
        this.ecosTypeService = ecosTypeService;
        this.recordsService = recordsService;
        this.namespaceService = namespaceService;
        this.alfNodesRecordsDao = alfNodesRecordsDao;
        this.ecosTypeRootService = ecosTypeRootService;
    }

    public RecordRef createEntity(ObjectData attributes) {

        attributes = attributes.deepCopy();

        String parent = attributes.get(RecordConstants.ATT_PARENT).asText();
        EntityId parentEntityId = getEntityId(RecordRef.valueOf(parent));

        RecordRef docLibTypeRef = parentEntityId.getTypeRef();
        if (RecordRef.isEmpty(docLibTypeRef)) {
            throw new IllegalStateException("Incorrect parent entity id: '" + parent + "'. Type info is missing.");
        }

        String ecosType = attributes.get(RecordConstants.ATT_TYPE).asText();
        ParameterCheck.mandatoryString(RecordConstants.ATT_TYPE, ecosType);
        RecordRef typeRef = RecordRef.valueOf(ecosType);

        DocLibDef docLib = ecosTypeService.getDocLib(docLibTypeRef);
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

        String parentNodeRefStr = parentEntityId.getLocalId();
        NodeRef parentNodeRef = null;
        if (parentNodeRefStr.isEmpty()) {
            parentNodeRef = ecosTypeRootService.getRootForType(docLibTypeRef, true);
        } else if (NodeRef.isNodeRef(parentNodeRefStr)) {
            parentNodeRef = new NodeRef(parentNodeRefStr);
        }
        if (parentNodeRef == null || !nodeService.exists(parentNodeRef)) {
            throw new IllegalArgumentException("Incorrect parent: '" + parentNodeRefStr + "'");
        }
        attributes.set(RecordConstants.ATT_PARENT, parentNodeRef.toString());

        RecordsMutation mutation = new RecordsMutation();
        mutation.addRecord(new RecordMeta(RecordRef.EMPTY, attributes));

        RecordsMutResult result = alfNodesRecordsDao.mutate(mutation);
        List<RecordMeta> resultRecords = result.getRecords();

        if (resultRecords.isEmpty()) {
            throw new IllegalStateException("Mutation return nothing. Attributes: " + attributes);
        }
        return RecordRef.create(DocLibRecords.SOURCE_ID,
            docLibTypeRef.getId() + TYPE_DELIM + result.getRecords().get(0).getId());
    }

    public List<DocLibNodeInfo> getPath(RecordRef docLibRef) {

        List<DocLibNodeInfo> resultPath = new ArrayList<>();

        EntityId entityId = getEntityId(docLibRef);
        if (RecordRef.isEmpty(entityId.getTypeRef()) || StringUtils.isBlank(entityId.getLocalId())) {
            return resultPath;
        }

        resultPath.add(getDocLibNodeInfo(getEntityRef(entityId.getTypeRef(), "")));

        if (entityId.getLocalId().isEmpty() || !NodeRef.isNodeRef(entityId.getLocalId())) {
            return resultPath;
        }

        NodeRef rootNodeRef = ecosTypeRootService.getRootForType(entityId.getTypeRef(), false);
        if (rootNodeRef == null) {
            return resultPath;
        }
        String rootNodeRefStr = rootNodeRef.toString();

        String entityNodeRefStr = entityId.getLocalId();
        if (rootNodeRefStr.equals(entityNodeRefStr)) {
            return resultPath;
        }

        List<DocLibNodeInfo> invPath = new ArrayList<>();

        String parentRefStr = recordsService.getAtt(RecordRef.valueOf(entityId.getLocalId()), "_parent?id").asText();
        while (NodeRef.isNodeRef(parentRefStr) && !rootNodeRefStr.equals(parentRefStr)) {
            DocLibNodeInfo nodeInfo = getDocLibNodeInfo(getEntityRef(entityId.getTypeRef(), parentRefStr));
            if (RecordRef.isEmpty(nodeInfo.getTypeRef())) {
                break;
            }
            invPath.add(nodeInfo);
            parentRefStr = recordsService.getAtt(RecordRef.valueOf(parentRefStr), "_parent?id").asText();
        }

        for (int i = invPath.size() - 1; i >= 0; i--) {
            resultPath.add(invPath.get(i));
        }
        return resultPath;
    }

    public boolean hasChildrenDirs(RecordRef docLibRef) {

        DocLibChildrenQuery query = new DocLibChildrenQuery();
        query.setParentRef(docLibRef);
        query.setRecursive(false);
        query.setNodeType(DocLibNodeType.DIR);

        RecsQueryRes<RecordRef> queryRes = getChildren(query, new QueryPage(1, 0, RecordRef.EMPTY));
        return !queryRes.getRecords().isEmpty();
    }

    @NotNull
    public DocLibNodeInfo getDocLibNodeInfo(@Nullable RecordRef docLibRef) {

        EntityId entityId = getEntityId(docLibRef);

        if (RecordRef.isEmpty(entityId.typeRef)) {
            return EMPTY_NODE;
        }

        if (entityId.getLocalId().isEmpty()) {

            TypeDto typeDef = ecosTypeService.getTypeDef(entityId.getTypeRef());
            if (typeDef == null) {
                return EMPTY_NODE;
            }

            String name = MLText.getClosestValue(typeDef.getName(), I18NUtil.getLocale());

            return new DocLibNodeInfo(
                docLibRef,
                DocLibNodeType.DIR,
                name,
                RecordRef.EMPTY,
                entityId.getTypeRef(),
                null,
                null,
                null,
                null,
                () -> null
            );
        }

        String nodeRef = entityId.getLocalId();
        if (StringUtils.isBlank(nodeRef) || !NodeRef.isNodeRef(nodeRef)) {
            return EMPTY_NODE;
        }

        DocLibEntityInfo info = recordsService.getAtts(RecordRef.create("", nodeRef), DocLibEntityInfo.class);

        if (RecordRef.isEmpty(info.typeRef)) {
            return EMPTY_NODE;
        }

        if (StringUtils.isBlank(info.getDisplayName())) {
            info.setDisplayName(entityId.getLocalId());
        }

        DocLibNodeType nodeType;
        DocLibDef docLib = ecosTypeService.getDocLib(entityId.getTypeRef());
        if (info.getTypeRef().equals(docLib.getDirTypeRef())) {
            nodeType = DocLibNodeType.DIR;
        } else if (docLib.getFileTypeRefs().contains(info.getTypeRef())) {
            nodeType = DocLibNodeType.FILE;
        } else {
            return EMPTY_NODE;
        }

        Supplier<ContentData> content;
        if (DocLibNodeType.DIR.equals(nodeType)) {
            content = () -> null;
        } else {
            content = () -> (ContentData) nodeService.getProperty(new NodeRef(nodeRef), ContentModel.PROP_CONTENT);
        }

        return new DocLibNodeInfo(
            docLibRef,
            nodeType,
            info.getDisplayName(),
            info.getTypeRef(),
            entityId.getTypeRef(),
            info.getModified(),
            info.getCreated(),
            info.getModifier(),
            info.getCreator(),
            content
        );
    }

    public RecsQueryRes<RecordRef> getChildren(DocLibChildrenQuery query, QueryPage page) {

        EntityId entityId = getEntityId(query.getParentRef());

        if (RecordRef.isEmpty(entityId.typeRef)) {
            return new RecsQueryRes<>();
        }
        if (page == null) {
            page = new QueryPage(1000, 0, RecordRef.EMPTY);
        }

        NodeRef parentNodeRef = null;
        if (entityId.localId.isEmpty()) {
            parentNodeRef = ecosTypeRootService.getRootForType(entityId.getTypeRef(), false);
        } else if (NodeRef.isNodeRef(entityId.localId)) {
            parentNodeRef = new NodeRef(entityId.localId);
        }
        if (parentNodeRef == null || !nodeService.exists(parentNodeRef)) {
            return new RecsQueryRes<>();
        }

        DocLibDef docLibDef = ecosTypeService.getDocLib(entityId.getTypeRef());

        boolean includeDirs = query.getNodeType() == null || query.getNodeType().equals(DocLibNodeType.DIR);
        boolean includeFiles = query.getNodeType() == null || query.getNodeType().equals(DocLibNodeType.FILE);

        OrPredicate typesPredicate = new OrPredicate();
        if (includeDirs && RecordRef.isNotEmpty(docLibDef.getDirTypeRef())) {
            ecosTypeService.expandTypeWithChildren(docLibDef.getDirTypeRef()).forEach(
                ref -> typesPredicate.addPredicate(Predicates.eq(RecordConstants.ATT_TYPE, ref))
            );
        }
        if (includeFiles) {
            for (RecordRef fileType : docLibDef.getFileTypeRefs()) {
                ecosTypeService.expandTypeWithChildren(fileType).forEach(
                    ref -> typesPredicate.addPredicate(Predicates.eq(RecordConstants.ATT_TYPE, ref))
                );
            }
        }

        Predicate parentPred;
        if (query.isRecursive()) {
            String path = nodeService.getPath(parentNodeRef).toPrefixString(namespaceService) + "//*";
            parentPred = Predicates.eq("PATH", path);
        } else {
            parentPred = Predicates.eq("PARENT", parentNodeRef.toString());
        }

        Predicate filterPred = VoidPredicate.INSTANCE;
        if (query.getFilter() != null && query.getFilter() != VoidPredicate.INSTANCE) {
            filterPred = PredicateUtils.mapValuePredicates(query.getFilter(), pred -> {
                if (pred.getAttribute().equals("ALL")) {
                    pred = pred.copy();
                    pred.setAttribute("cm:title");
                }
                return pred;
            });
        }

        RecordsQuery recordsQuery = RecordsQuery.create()
            .withLanguage(PredicateService.LANGUAGE_PREDICATE)
            .withQuery(Predicates.and(parentPred, typesPredicate, filterPred))
            .withPage(page)
            .build();

        RecsQueryRes<RecordRef> childrenQueryRes = recordsService.query(recordsQuery);

        List<RecordRef> children = childrenQueryRes.getRecords()
            .stream()
            .map(r -> getEntityRef(entityId.getTypeRef(), r.getId()))
            .collect(Collectors.toList());

        RecsQueryRes<RecordRef> res = new RecsQueryRes<>();
        res.setRecords(children);
        res.setHasMore(childrenQueryRes.getHasMore());
        res.setTotalCount(childrenQueryRes.getTotalCount());

        return res;
    }

    private RecordRef getEntityRef(RecordRef typeRef, String localId) {
        return RecordRef.create(DocLibRecords.SOURCE_ID, typeRef.getId() + TYPE_DELIM + localId);
    }

    @NotNull
    private EntityId getEntityId(RecordRef ref) {
        return getEntityId(ref != null ? ref.getId() : null);
    }

    @NotNull
    private EntityId getEntityId(@Nullable String refId) {

        if (StringUtils.isBlank(refId)) {
            return new EntityId(RecordRef.EMPTY, "");
        }

        if (!refId.contains(TYPE_DELIM)) {
            return new EntityId(RecordRef.EMPTY, "");
        }

        int delimIdx = refId.indexOf('$');
        if (delimIdx == 0) {
            return new EntityId(RecordRef.EMPTY, "");
        }

        String typeId = refId.substring(0, delimIdx);
        String localId = "";
        if (delimIdx < refId.length() - 1) {
            localId = refId.substring(delimIdx + 1);
        }

        return new EntityId(TypeUtils.getTypeRef(typeId), localId);
    }

    @Data
    public static class DocLibEntityInfo {
        @AttName("_type?id")
        private RecordRef typeRef;
        @AttName("?disp")
        private String displayName;
        @AttName("cm:modified")
        private Date modified;
        @AttName("cm:modifier")
        private String modifier;
        @AttName("cm:created")
        private Date created;
        @AttName("cm:creator")
        private String creator;
    }

    @Data
    @AllArgsConstructor
    private static class EntityId {
        private final RecordRef typeRef;
        private final String localId;
    }
}
