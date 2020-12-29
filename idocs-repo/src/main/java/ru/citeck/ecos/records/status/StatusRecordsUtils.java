package ru.citeck.ecos.records.status;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.dictionary.Constraint;
import org.alfresco.service.cmr.dictionary.ConstraintDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.icase.CaseStatusAssocDao;
import ru.citeck.ecos.icase.CaseStatusService;
import ru.citeck.ecos.model.IdocsModel;
import ru.citeck.ecos.model.lib.role.service.StatusService;
import ru.citeck.ecos.model.lib.status.dto.StatusDef;
import ru.citeck.ecos.model.lib.type.service.TypeDefService;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.op.query.dto.RecsQueryRes;
import ru.citeck.ecos.records3.record.op.query.dto.query.RecordsQuery;
import ru.citeck.ecos.spring.registry.MappingRegistry;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Component
class StatusRecordsUtils {

    private static final String CONSTRAINT_STATUS_KEY = "listconstraint.idocs_constraint_documentStatus.";
    private static final String CONSTRAINT_ALLOWED_VALUES = "allowedValues";

    private static final String ATT_TYPE = "type";
    private static final String ATT_FIELD = "_status";

    private final NodeService nodeService;
    private final RecordsService recordsService;
    private final CaseStatusService caseStatusService;
    private final CaseStatusAssocDao caseStatusAssocDao;
    private final MappingRegistry<String, String> typeToConstraintMapping;
    private final DictionaryService dictionaryService;
    private final NamespaceService namespaceService;
    private final EcosTypeService ecosTypeService;
    private final StatusService statusService;
    private final TypeDefService typeDefService;

    @Autowired
    public StatusRecordsUtils(NodeService nodeService, RecordsService recordsService,
                              CaseStatusService caseStatusService,
                              CaseStatusAssocDao caseStatusAssocDao,
                              @Qualifier("records.document-status.type-to-constraint.mappingRegistry")
                                  MappingRegistry<String, String> typeToConstraintMapping,
                              DictionaryService dictionaryService, NamespaceService namespaceService,
                              EcosTypeService ecosTypeService, StatusService statusService, TypeDefService typeDefService) {
        this.nodeService = nodeService;
        this.recordsService = recordsService;
        this.caseStatusService = caseStatusService;
        this.caseStatusAssocDao = caseStatusAssocDao;
        this.typeToConstraintMapping = typeToConstraintMapping;
        this.dictionaryService = dictionaryService;
        this.namespaceService = namespaceService;
        this.ecosTypeService = ecosTypeService;
        this.statusService = statusService;
        this.typeDefService = typeDefService;
    }

    RecsQueryRes<StatusRecord> getAllExistingStatuses(String type) {
        RecsQueryRes<StatusRecord> existingCaseStatuses = getAllExistingCaseStatuses(type);
        if (existingCaseStatuses.getTotalCount() > 0) {
            return existingCaseStatuses;
        } else {
            return getAllExistingDocumentStatuses(type);
        }
    }

    private RecsQueryRes<StatusRecord> getAllExistingCaseStatuses(String type) {
        RecordRef ecosType = ecosTypeService.getEcosType(type);
        Map<String, StatusDef> statuses = ecosType != null ? statusService.getStatusesByType(ecosType) : null;

        RecordsQuery query = RecordsQuery.create()
            .withLanguage(PredicateService.LANGUAGE_PREDICATE)
            .withQuery(Predicates.eq(ATT_TYPE, type))
            .withGroupBy(Collections.singletonList(ATT_FIELD))
            .build();

        List<StatusRecord> statusRecords = recordsService.query(query, Collections.singletonMap("id", ATT_FIELD + "?id"))
            .getRecords().stream().map(value -> {
                String ref = value.getAtt("id").asText();
                return getByStatusRef(new NodeRef(ref), statuses, ecosType);
            })
            .map(StatusRecord::new)
            .collect(Collectors.toList());

        RecsQueryRes<StatusRecord> result = new RecsQueryRes<>(statusRecords);
        result.setRecords(statusRecords);
        result.setTotalCount(statusRecords.size());
        return result;
    }

    private RecsQueryRes<StatusRecord> getAllExistingDocumentStatuses(String type) {
        RecsQueryRes<StatusRecord> result = new RecsQueryRes<>();

        String constraintKey = typeToConstraintMapping.getMapping().get(type);
        if (StringUtils.isBlank(constraintKey)) {
            return result;
        }

        ConstraintDefinition statusConstraint = dictionaryService.getConstraint(QName.resolveToQName(namespaceService,
            constraintKey));
        Constraint constraint = statusConstraint.getConstraint();

        Map<String, Object> parameters = constraint.getParameters();

        // This cast is correct, because we know this is a ArrayList<String>
        @SuppressWarnings("unchecked")
        List<String> allowedValues = (List<String>) parameters.get(CONSTRAINT_ALLOWED_VALUES);

        List<StatusRecord> statuses = allowedValues.
            stream()
            .map(this::getByNameDocumentStatus)
            .map(StatusRecord::new)
            .collect(Collectors.toList());

        result.setRecords(statuses);

        return result;
    }

    RecsQueryRes<StatusRecord> getAllAvailableToChangeStatuses(RecordRef recordRef) {
        if (recordRef == null || StringUtils.isBlank(recordRef.getId())) {
            throw new IllegalArgumentException("You mus specify a record to find comments");
        }

        String id = recordRef.getId();
        if (!NodeRef.isNodeRef(id)) {
            throw new IllegalArgumentException("Record id should be NodeRef format");
        }

        //TODO: implement
        return new RecsQueryRes<>();
    }

    RecsQueryRes<StatusRecord> getStatusByRecord(RecordRef recordRef) {
        if (recordRef == null || StringUtils.isBlank(recordRef.getId())) {
            throw new IllegalArgumentException("You mus specify a record to find comments");
        }

        String id = recordRef.getId();
        if (!NodeRef.isNodeRef(id)) {
            throw new IllegalArgumentException("Record id should be NodeRef format");
        }

        StatusDto dto = getDocumentStatus(new NodeRef(id));
        if (dto == null) {
            return new RecsQueryRes<>();
        }

        StatusRecord statusRecord = new StatusRecord(dto);

        RecsQueryRes<StatusRecord> result = new RecsQueryRes<>();
        result.setRecords(Collections.singletonList(statusRecord));
        result.setTotalCount(1);
        return result;
    }

    public StatusDto getStatusById(String recordId) {
        int slashIndex = recordId.lastIndexOf('/');
        String status = slashIndex >= 0 ? recordId.substring(slashIndex + 1) : recordId;
        String etype = slashIndex >= 0 ? recordId.substring(0, slashIndex) : null;

        if (StringUtils.isBlank(etype)) {
            return getByNameCaseOrDocumentStatus(status);
        }

        return getEcosStatusById(status, etype);
    }

    public StatusDto getEcosStatusById(String statusId, String etype) {
        RecordRef typeRef = RecordRef.create("emodel", "type", etype);
        Map<String, StatusDef> statuses = statusService.getStatusesByType(typeRef);
        NodeRef statusNode = caseStatusAssocDao.statusToNode(statusId);
        return getEcosStatusDto(statusNode, statuses, typeRef);
    }

    StatusDto getByNameCaseOrDocumentStatus(String name) {
        NodeRef statusRef = caseStatusService.getStatusByName(name);
        if (statusRef == null) {
            return getByNameDocumentStatus(name);
        }

        return getByStatusRef(statusRef);
    }

    private StatusDto getDocumentStatus(NodeRef document) {
        NodeRef statusRef = caseStatusService.getStatusRef(document);

        if (statusRef != null) {
            RecordRef documentRef = RecordRef.valueOf(document.toString());
            Map<String, StatusDef> statuses = statusService.getStatusesByDocument(documentRef);
            return getByStatusRef(statusRef, statuses, typeDefService.getTypeRef(documentRef));
        } else {
            Serializable documentStatus = nodeService.getProperty(document, IdocsModel.PROP_DOCUMENT_STATUS);
            if (documentStatus != null) {
                return getByNameCaseOrDocumentStatus((String) documentStatus);
            }
        }

        return null;
    }

    private StatusDto getByNameDocumentStatus(String name) {
        StatusDto dto = new StatusDto();
        dto.setType(StatusType.DOCUMENT_STATUS.toString());
        dto.setId(name);

        String titleKey = CONSTRAINT_STATUS_KEY + name;
        dto.setName(I18NUtil.getMessage(titleKey));

        return dto;
    }

    private StatusDto getByStatusRef(NodeRef statusRef) {
        return getByStatusRef(statusRef, null, null);
    }

    private StatusDto getByStatusRef(NodeRef statusRef, Map<String, StatusDef> statuses, RecordRef etype) {
        if (caseStatusService.isAlfRef(statusRef)) {
            return getAlfrescoStatusDto(statusRef);
        } else {
            return getEcosStatusDto(statusRef, statuses, etype);
        }
    }

    @NotNull
    private StatusDto getAlfrescoStatusDto(NodeRef statusRef) {
        Map<QName, Serializable> properties = nodeService.getProperties(statusRef);
        String name = (String) properties.get(ContentModel.PROP_NAME);

        Serializable title = properties.get(ContentModel.PROP_TITLE);
        if (title == null) {
            title = name;
        }

        StatusDto dto = new StatusDto();
        dto.setName((String) title);
        dto.setType(StatusType.CASE_STATUS.toString());
        dto.setId(name);
        return dto;
    }

    private StatusDto getEcosStatusDto(NodeRef statusRef, Map<String, StatusDef> statuses, RecordRef etype) {
        if (statuses == null) {
            return null;
        }

        StatusDef statusDef = statuses.get(statusRef.getId());
        if (statusDef == null) {
            return null;
        }

        StatusDto dto = new StatusDto();
        dto.setName(statusDef.getName().getClosestValue(I18NUtil.getLocale()));
        dto.setType(StatusType.ECOS_CASE_STATUS.toString());

        String id = StringUtils.isNoneBlank(etype.getId())
            ? etype.getId() + "/" + statusDef.getId()
            : statusDef.getId();
        dto.setId(id);
        return dto;
    }
}
