package ru.citeck.ecos.icase.status.event;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.events2.EventsService;
import ru.citeck.ecos.events2.emitter.EmitterConfig;
import ru.citeck.ecos.events2.emitter.EventsEmitter;
import ru.citeck.ecos.records2.RecordRef;

import javax.annotation.PostConstruct;

/**
 * @author Roman Makarskiy
 */
@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class StatusEventEmitter {

    private static final String SOURCE = "alfresco";

    private final EventsService eventsService;

    private EventsEmitter<StatusEventDto> statusChangeEmitter;

    @PostConstruct
    public void init() {
        statusChangeEmitter = eventsService.getEmitter(
            new EmitterConfig.Builder<StatusEventDto>()
                .withEventClass(StatusEventDto.class)
                .withEventType(StatusEventConstants.STATUS_UPDATE_EVENT)
                .withSource(SOURCE)
                .build()
        );
    }

    public void send(RecordRef rec, RecordRef caseStatusBefore, RecordRef caseStatusAfter) {

        StatusEventDto statusEventDto = StatusEventDto.builder()
            .rec(rec)
            .statusBefore(caseStatusBefore)
            .statusAfter(caseStatusAfter)
            .build();

        statusChangeEmitter.emit(statusEventDto);
    }

}
