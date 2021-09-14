package ru.citeck.ecos.domain.cmmn;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import kotlin.Unit;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.events2.EventsService;
import ru.citeck.ecos.events2.listener.ListenerConfig;
import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.dto.CaseServiceType;
import ru.citeck.ecos.icase.activity.service.CaseActivityEventService;
import ru.citeck.ecos.icase.activity.service.eproc.importer.EProcCaseImporter;
import ru.citeck.ecos.model.ICaseEventModel;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.records3.record.request.RequestContext;
import ru.citeck.ecos.utils.TransactionUtils;

import javax.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class RemoteRecordsListener {

    private static final String EVENT_REC_SRC_MUTATION_TYPE = "recsrc-db-record-mutated";
    private static final String EVENT_RECORDS_TO_PROC_TXN_KEY = RemoteRecordsListener.class.getSimpleName() + "-recs";
    private static final String EVENT_NEW_RECORDS_TXN_KEY = RemoteRecordsListener.class.getSimpleName() + "-new-recs";

    private final EventsService eventsService;
    private final EcosTypeService ecosTypeService;
    private final EProcCaseImporter eProcCaseImporter;
    private final CaseActivityEventService caseActivityEventService;
    private final TransactionService transactionService;

    private LoadingCache<RecordRef, Boolean> isTypeCase;

    @PostConstruct
    public void init() {
        isTypeCase = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .maximumSize(100)
            .build(CacheLoader.from(this::isTypeCase));

        /*eventsService.addListener(ListenerConfig.<EventData>create()
            .withEventType(EVENT_REC_SRC_MUTATION_TYPE)
            .withDataClass(EventData.class)
            .withActionJ(this::onEvent)
            .build()
        );*/
    }

    private void onEvent(EventData event) {
        transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
            RequestContext.doWithTxnJ(() -> onEventImpl(event));
            return null;
        }, false);
    }

    private void onEventImpl(EventData event) {

        log.info(
            "Record mutated: " + event.recordRef
                + " type: " + event.typeRef
                + " new record: " + event.newRecord
        );
        if (RecordRef.isEmpty(event.recordRef) || RecordRef.isEmpty(event.typeRef)) {
            return;
        }

        if (!isTypeCase.getUnchecked(event.typeRef)) {
            log.info("Record is not a case");
            return;
        }

        ActivityRef activityRef = ActivityRef.of(CaseServiceType.EPROC, event.recordRef, ActivityRef.ROOT_ID);
        Set<RecordRef> newRecordsSet = TransactionalResourceHelper.getSet(EVENT_NEW_RECORDS_TXN_KEY);

        if (event.newRecord && newRecordsSet.add(event.recordRef)) {
            eProcCaseImporter.importCase(event.recordRef);
            caseActivityEventService.fireEvent(activityRef, ICaseEventModel.CONSTR_CASE_CREATED);
        }
        TransactionUtils.processBeforeCommit(EVENT_RECORDS_TO_PROC_TXN_KEY, event.recordRef, recordRef ->
            caseActivityEventService.fireEvent(activityRef, ICaseEventModel.CONSTR_CASE_PROPERTIES_CHANGED)
        );
    }

    private boolean isTypeCase(RecordRef typeRef) {
        MutableBoolean isCase = new MutableBoolean();
        ecosTypeService.forEachAsc(typeRef, dto -> {
            if ("case".equals(dto.getId())) {
                isCase.setTrue();
                return true;
            }
            return false;
        });
        return isCase.getValue();
    }

    @Data
    public static class EventData {
        @AttName("after?id")
        private RecordRef recordRef;
        @AttName("after._type?id")
        private RecordRef typeRef;
        private Boolean newRecord;
    }
}
