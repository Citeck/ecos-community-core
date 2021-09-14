package ru.citeck.ecos.domain.cmmn;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import javax.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class RemoteRecordsListener {

    private static final String EVENT_REC_SRC_MUTATION_TYPE = "recsrc-db-record-mutated";

    private final EventsService eventsService;
    private final EcosTypeService ecosTypeService;
    private final EProcCaseImporter eProcCaseImporter;
    private final CaseActivityEventService caseActivityEventService;
    private final TransactionService transactionService;

    @PostConstruct
    public void init() {
        eventsService.addListener(ListenerConfig.<EventData>create()
            .withEventType(EVENT_REC_SRC_MUTATION_TYPE)
            .withDataClass(EventData.class)
            .withActionJ(this::onEvent)
            .build()
        );
    }

    private void onEvent(EventData event) {
        transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
            onEventImpl(event);
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

        MutableBoolean isCase = new MutableBoolean();
        ecosTypeService.forEachAsc(event.typeRef, dto -> {
            if ("case".equals(dto.getId())) {
                isCase.setTrue();
                return true;
            }
            return false;
        });

        if (!isCase.getValue()) {
            log.info("Record is not a case");
            return;
        }

        if (event.newRecord) {
            eProcCaseImporter.importCase(event.recordRef);
        }

        ActivityRef activityRef = ActivityRef.of(CaseServiceType.EPROC, event.recordRef, ActivityRef.ROOT_ID);
        if (event.newRecord) {
            caseActivityEventService.fireEvent(activityRef, ICaseEventModel.CONSTR_CASE_CREATED);
        }
        caseActivityEventService.fireEvent(activityRef, ICaseEventModel.CONSTR_CASE_PROPERTIES_CHANGED);
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
