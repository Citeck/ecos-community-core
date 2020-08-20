package ru.citeck.ecos.records;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.service.namespace.NamespaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.WorkflowMirrorModel;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeRecord;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.query.lang.DistinctQuery;
import ru.citeck.ecos.records2.source.common.group.DistinctValue;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.search.SearchPredicate;

/**
 * @author Roman Makarskiy
 */
@Component
public class CounterpartyRecords extends LocalRecordsDao implements LocalRecordsQueryWithMetaDao<AlfNodeRecord> {

    private static final String ID = "counterparty";

    private static final String CRITERIA_LANGUAGE = "criteria";
    private static final String ATT_TYPE = "type";
    private static final String ATT_FIELD = "field_1";
    private static final String ATT_PREDICATE = "predicate_1";
    private static final String ATT_VALUE = "value_1";

    private final String COUNTERPARTY_ATTR;

    private final RecordsService recordsService;

    @Autowired
    public CounterpartyRecords(RecordsService recordsService, NamespaceService namespaceService) {
        setId(ID);
        this.recordsService = recordsService;

        COUNTERPARTY_ATTR = WorkflowMirrorModel.PROP_COUNTERPARTY.toPrefixString(namespaceService);
    }

    @Override
    public RecordsQueryResult<AlfNodeRecord> queryLocalRecords(RecordsQuery query, MetaField field) {
        CounterpartyQuery counterpartyQuery = query.getQuery(CounterpartyQuery.class);
        if (counterpartyQuery.allAvailableCounterparties) {
            return getDistinctCounterpartiesByTasks();
        }

        return new RecordsQueryResult<>();
    }

    private RecordsQueryResult<AlfNodeRecord> getDistinctCounterpartiesByTasks() {
        RecordsQuery findAllAvailableQuery = new RecordsQuery();
        findAllAvailableQuery.setLanguage(DistinctQuery.LANGUAGE);

        DistinctQuery distinctQuery = new DistinctQuery();
        distinctQuery.setLanguage(CRITERIA_LANGUAGE);

        ObjectNode attributes = JsonNodeFactory.instance.objectNode();
        attributes.put(ATT_FIELD, ATT_TYPE);
        attributes.put(ATT_PREDICATE, SearchPredicate.TYPE_EQUALS.getValue());
        attributes.put(ATT_VALUE, WorkflowModel.TYPE_TASK.toString());

        distinctQuery.setQuery(attributes);
        distinctQuery.setAttribute(COUNTERPARTY_ATTR);

        findAllAvailableQuery.setQuery(distinctQuery);

        RecordsQueryResult<DistinctValue> values = recordsService.queryRecords(findAllAvailableQuery,
            DistinctValue.class);

        return new RecordsQueryResult<>(values, value -> new AlfNodeRecord(RecordRef.valueOf(value.getValue())));
    }

    @Data
    static class CounterpartyQuery {
        public boolean allAvailableCounterparties;
    }
}
