package ru.citeck.ecos.template;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.ClassificationModel;
import ru.citeck.ecos.model.DmsModel;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.RecordElement;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service("ecosSelectTemplateService")
public class EcosSelectTemplateService {

    @Autowired
    private NodeService nodeService;

    @Autowired
    private EcosTypeService ecosTypeService;

    @Autowired
    private RecordsService recordsService;

    @Autowired
    private PredicateService predicateService;

    @Autowired
    private SearchService searchService;

    public List<NodeRef> getNodeOrderedTemplates(NodeRef nodeRef) {
        EntityRef ecosType = ecosTypeService.getEcosType(nodeRef);
        if (ecosType == null) {
            return Collections.emptyList();
        }

        List<NodeRef> templateBasedOnType = getTemplateBasedOnType(ecosType);
        if (templateBasedOnType.isEmpty()) {
            return Collections.emptyList();
        }

        RecordElement element = new RecordElement(recordsService, EntityRef.valueOf(nodeRef.toString()));
        return templateBasedOnType.stream()
            .filter(tNodeRef -> filter(element, tNodeRef))
            .sorted(Comparator.comparingInt(item -> {
                Integer priority = (Integer) nodeService.getProperty(item, DmsModel.PROP_PRIORITY);
                return (priority == null) ? 0 : -priority;
            }))
            .collect(Collectors.toList());
    }

    private boolean filter(RecordElement element, NodeRef tNodeRef) {
        if (nodeService.hasAspect(tNodeRef, DmsModel.ASPECT_HAS_PREDICATE)) {
            String predicateStr = (String) nodeService.getProperty(tNodeRef, DmsModel.PROP_PREDICATE);
            if (predicateStr != null && !predicateStr.isEmpty()) {
                Predicate predicate = Json.getMapper().read(predicateStr, Predicate.class);
                if (predicate != null) {
                    return predicateService.isMatch(element, predicate);
                }
            }
        } else {
            return true;
        }
        return false;
    }

    private List<NodeRef> getTemplateBasedOnType(EntityRef ecosType) {
        String categoryId = ecosType.getLocalId();
        int index = categoryId.indexOf("/");
        if (index != -1) {
            categoryId = categoryId.substring(0, index);
        }
        NodeRef category = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, categoryId);
        return FTSQuery.create()
            .type(DmsModel.TYPE_TEMPLATE).and()
            .value(ClassificationModel.PROP_DOCUMENT_APPLIES_TO_TYPE, category)
            .and()
            .empty(ClassificationModel.PROP_DOCUMENT_APPLIES_TO_KIND)
            .query(searchService);
    }
}
