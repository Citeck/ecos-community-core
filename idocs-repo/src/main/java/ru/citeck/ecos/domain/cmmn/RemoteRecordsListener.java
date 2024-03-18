package ru.citeck.ecos.domain.cmmn;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
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
import ru.citeck.ecos.events2.type.RecordChangedEvent;
import ru.citeck.ecos.events2.type.RecordCreatedEvent;
import ru.citeck.ecos.events2.type.RecordDraftStatusChangedEvent;
import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.dto.CaseServiceType;
import ru.citeck.ecos.icase.activity.service.CaseActivityEventService;
import ru.citeck.ecos.icase.activity.service.eproc.importer.EProcCaseImporter;
import ru.citeck.ecos.model.ICaseEventModel;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.records3.record.request.RequestContext;
import ru.citeck.ecos.utils.TransactionUtils;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import javax.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class RemoteRecordsListener {

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

        eventsService.addListener(ListenerConfig.<EventData>create()
            .withEventType(RecordCreatedEvent.TYPE)
            .withDataClass(EventData.class)
            .withActionJ(event -> onEvent(event, true))
            .build()
        );
        eventsService.addListener(ListenerConfig.<EventData>create()
            .withEventType(RecordChangedEvent.TYPE)
            .withDataClass(EventData.class)
            .withActionJ(event -> onEvent(event, false))
            .build()
        );
        eventsService.addListener(ListenerConfig.<EventData>create()
            .withEventType(RecordDraftStatusChangedEvent.TYPE)
            .withDataClass(EventData.class)
            .withActionJ(event -> onEvent(event, false))
            .withFilter(Predicates.eq("after?bool", false))
            .build()
        );
    }

    private void onEvent(EventData event, boolean isNewRec) {
        RequestContext.doWithTxnJ(() ->
            transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
                onEventImpl(event, isNewRec);
                return null;
            }, false)
        );
    }

    private void onEventImpl(EventData event, boolean isNewRec) {

        log.debug(
            "Record mutated: " + event.recordRef
                + " type: " + event.typeRef
                + " new record: " + isNewRec
        );
        if (EntityRef.isEmpty(event.recordRef) || EntityRef.isEmpty(event.typeRef)) {
            return;
        }

        if (!isTypeCase.getUnchecked(event.typeRef)) {
            log.debug("Record is not a case");
            return;
        }

        ActivityRef activityRef = ActivityRef.of(CaseServiceType.EPROC, event.recordRef, ActivityRef.ROOT_ID);
        Set<RecordRef> newRecordsSet = TransactionalResourceHelper.getSet(EVENT_NEW_RECORDS_TXN_KEY);

        if (isNewRec && newRecordsSet.add(event.recordRef)) {
            if (eProcCaseImporter.importCase(event.recordRef)) {
                caseActivityEventService.fireEvent(
                    activityRef,
                    ICaseEventModel.CONSTR_CASE_CREATED,
                    false
                );
            }
        }
        TransactionUtils.processBeforeCommit(EVENT_RECORDS_TO_PROC_TXN_KEY, event.recordRef, recordRef ->
            caseActivityEventService.fireEvent(
                activityRef,
                ICaseEventModel.CONSTR_CASE_PROPERTIES_CHANGED,
                false
            )
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
        @AttName("record?id")
        private RecordRef recordRef;
        @AttName("record._type?id")
        private RecordRef typeRef;
    }
}
