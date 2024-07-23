package ru.citeck.ecos.currency;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.node.DoubleNode;
import lombok.Data;
import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.IdocsModel;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.field.EmptyMetaField;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.AndPredicate;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class CurrencyRateRecordsDao extends LocalRecordsDao implements
        LocalRecordsQueryWithMetaDao<CurrencyRateRecordsDao.CurrencyRateRecord>,
        LocalRecordsMetaDao<CurrencyRateRecordsDao.CurrencyRateRecord>,
        MutableRecordsLocalDao<CurrencyRateRecordsDao.CurrencyRateRecord> {

    private static final String ID = "currency-rate";

    private final AlfNodesRecordsDAO alfNodesRecordsDao;
    private final CurrencyService currencyService;

    @Autowired
    public CurrencyRateRecordsDao(AlfNodesRecordsDAO alfNodesRecordsDao,
                                  CurrencyService currencyService) {
        this.alfNodesRecordsDao = alfNodesRecordsDao;
        this.currencyService = currencyService;

        setId(ID);
    }

    @Override
    public List<CurrencyRateRecord> getLocalRecordsMeta(List<EntityRef> list, MetaField metaField) {
        return list.stream()
                .map(recordRef -> RecordRef.create(AlfNodesRecordsDAO.ID, recordRef.getLocalId()))
                .map(recordRef -> recordsService.getMeta(recordRef, CurrencyRateRecord.class))
                .collect(Collectors.toList());
    }

    @Override
    public RecordsQueryResult<CurrencyRateRecord> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {
        RecordsQueryResult<EntityRef> records = queryRecords(recordsQuery);

        RecordsQueryResult<CurrencyRateRecord> result = new RecordsQueryResult<>();
        result.merge(records);
        result.setHasMore(records.getHasMore());
        result.setTotalCount(records.getTotalCount());
        result.setRecords(getLocalRecordsMeta(records.getRecords(), metaField));

        if (recordsQuery.isDebug()) {
            result.setDebugInfo(getClass(), "query", recordsQuery.getQuery());
            result.setDebugInfo(getClass(), "language", recordsQuery.getLanguage());
        }

        return result;
    }

    @Override
    public List<CurrencyRateRecord> getValuesToMutate(List<EntityRef> list) {
        return getLocalRecordsMeta(list, EmptyMetaField.INSTANCE);
    }

    @Override
    public RecordsMutResult save(List<CurrencyRateRecord> list) {
        RecordsMutation recordsMutation = new RecordsMutation();

        list.forEach(currencyRate -> {
            AndPredicate predicate = Predicates.and(
                    Predicates.eq("TYPE", IdocsModel.TYPE_CURRENCY_RATE_RECORD.toString()),
                    Predicates.eq("idocs:crrSyncDate", currencyRate.syncDate),
                    Predicates.eq("idocs:crrBaseCurrency", getRefByCode(currencyRate.baseCurrencyCode).toString()),
                    Predicates.eq("idocs:crrTargetCurrency", getRefByCode(currencyRate.targetCurrencyCode).toString())
            );

            RecordsQuery query = new RecordsQuery();
            query.setLanguage(PredicateService.LANGUAGE_PREDICATE);
            query.setQuery(predicate);

            Optional<EntityRef> recordRef = recordsService.queryRecord(query);
            if (recordRef.isPresent()) {
                recordsMutation.getRecords().add(composeCurrencyRecordMeta(recordRef.get().getLocalId(), currencyRate));
            } else {
                recordsMutation.getRecords().add(composeCurrencyRecordMeta(null, currencyRate));
            }
        });

        return alfNodesRecordsDao.mutate(recordsMutation);
    }

    private RecordMeta composeCurrencyRecordMeta(String id, CurrencyRateRecord currencyRate) {
        RecordMeta recordMeta = new RecordMeta(id);

        recordMeta.set("type", "idocs:currencyRateRecord");
        recordMeta.set("_parent", "/app:company_home/app:dictionary/cm:dataLists/cm:currency-rates");
        recordMeta.set("_parentAtt", "cm:contains");

        recordMeta.set("idocs:crrValue", new DoubleNode(currencyRate.rate));
        recordMeta.set("idocs:crrDate", currencyRate.date);
        recordMeta.set("idocs:crrSyncDate", currencyRate.syncDate);
        recordMeta.set("idocs:crrBaseCurrency", getRefByCode(currencyRate.baseCurrencyCode).toString());
        recordMeta.set("idocs:crrTargetCurrency", getRefByCode(currencyRate.targetCurrencyCode).toString());

        return recordMeta;
    }

    private NodeRef getRefByCode(String currencyCode) {
        Currency currency = currencyService.getCurrencyByCode(currencyCode);
        if (currency == null) {
            throw new IllegalArgumentException("Currency not found for code " + currencyCode);
        }
        return currency.getNodeRef();
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {
        return alfNodesRecordsDao.delete(recordsDeletion);
    }

    @Data
    public static class CurrencyRateRecord {
        private double rate;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd.MM.yyyy", timezone = "GMT")
        @ecos.com.fasterxml.jackson210.annotation.JsonFormat(
            shape = ecos.com.fasterxml.jackson210.annotation.JsonFormat.Shape.STRING,
            pattern = "dd.MM.yyyy",
            timezone = "GMT"
        )
        private Date date;
        private String syncDate;
        private String baseCurrencyCode;
        private String targetCurrencyCode;
    }

}
