package ru.citeck.ecos.icase.evaluators;

import lombok.Data;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.icase.completeness.CaseCompletenessService;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.evaluator.RecordEvaluatorService;
import ru.citeck.ecos.records2.evaluator.details.*;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CompletenessLevelsEvaluator implements
        RecordEvaluatorWithDetails<Object, RecordMeta, CompletenessLevelsEvaluator.Config> {

    public static final String TYPE = "completeness-levels";

    private RecordEvaluatorService recordEvaluatorService;
    private CaseCompletenessService caseCompletenessService;
    private NodeService nodeService;

    @Autowired
    public CompletenessLevelsEvaluator(RecordEvaluatorService recordEvaluatorService,
                                       CaseCompletenessService caseCompletenessService,
                                       NodeService nodeService) {
        this.recordEvaluatorService = recordEvaluatorService;
        this.caseCompletenessService = caseCompletenessService;
        this.nodeService = nodeService;
    }

    @PostConstruct
    public void init() {
        recordEvaluatorService.register(this);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Object getMetaToRequest(Config config) {
        return null;
    }

    @Override
    public EvalDetails evalWithDetails(RecordMeta recordMeta, Config config) {
        Set<NodeRef> alfCompletenessLevels = getAlfCompletenessLevels(config);
        EntityRef caseRef = recordMeta.getId();
        return evalWithDetailsImpl(caseRef, alfCompletenessLevels);
    }

    private Set<NodeRef> getAlfCompletenessLevels(Config config) {
        Set<String> completenessLevelIdentifiers = config.getCompletenessLevelIdentifiers();
        if (CollectionUtils.isEmpty(completenessLevelIdentifiers)) {
            return Collections.emptySet();
        }

        return completenessLevelIdentifiers.stream()
                .map(NodeRef::new)
                .collect(Collectors.toSet());
    }

    private EvalDetails evalWithDetailsImpl(EntityRef caseRef, Set<NodeRef> alfCompletenessLevels) {
        List<EvalResultCause> causes = evalCauses(caseRef, alfCompletenessLevels);
        boolean success = CollectionUtils.isEmpty(causes);
        return new EvalDetailsImpl(success, causes);
    }

    private List<EvalResultCause> evalCauses(EntityRef caseRef, Set<NodeRef> alfCompletenessLevels) {
        NodeRef caseNodeRef = RecordsUtils.toNodeRef(caseRef);
        List<EvalResultCause> resultCauses = new ArrayList<>();
        for (NodeRef alfCompletenessLevel : alfCompletenessLevels) {
            if (!caseCompletenessService.isLevelCompleted(caseNodeRef, alfCompletenessLevel)) {
                String levelTitle = (String) nodeService.getProperty(alfCompletenessLevel, ContentModel.PROP_TITLE);
                resultCauses.add(new EvalResultCauseImpl(levelTitle));
            }
        }
        return resultCauses;
    }

    @Data
    public static class Config {
        private Set<String> completenessLevelIdentifiers;
    }
}
