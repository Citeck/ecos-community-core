package ru.citeck.ecos.comment.event;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.events2.EventsService;
import ru.citeck.ecos.events2.emitter.EmitterConfig;
import ru.citeck.ecos.events2.emitter.EventsEmitter;

import javax.annotation.PostConstruct;

/**
 * @author Roman Makarskiy
 */
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class EcosCommentEventService {

    private static final String SOURCE = "alfresco";

    private static final String EVENT_CREATE = "ecos.comment.create";
    private static final String EVENT_UPDATE = "ecos.comment.update";
    private static final String EVENT_DELETE = "ecos.comment.delete";

    private EventsEmitter<EcosCommentEventDto> commentCreateEmitter;
    private EventsEmitter<EcosCommentEventDto> commentUpdateEmitter;
    private EventsEmitter<EcosCommentEventDto> commentDeleteEmitter;

    private final EventsService eventsService;

    @PostConstruct
    public void init() {
        commentCreateEmitter = eventsService.getEmitter(
            new EmitterConfig.Builder<EcosCommentEventDto>()
                .withEventClass(EcosCommentEventDto.class)
                .withEventType(EVENT_CREATE)
                .withSource(SOURCE)
                .build()
        );

        commentUpdateEmitter = eventsService.getEmitter(
            new EmitterConfig.Builder<EcosCommentEventDto>()
                .withEventClass(EcosCommentEventDto.class)
                .withEventType(EVENT_UPDATE)
                .withSource(SOURCE)
                .build()
        );

        commentDeleteEmitter = eventsService.getEmitter(
            new EmitterConfig.Builder<EcosCommentEventDto>()
                .withEventClass(EcosCommentEventDto.class)
                .withEventType(EVENT_DELETE)
                .withSource(SOURCE)
                .build()
        );
    }

    public void sendCreateEvent(EcosCommentEventDto eventDto) {
        commentCreateEmitter.emit(eventDto);
    }

    public void sendUpdateEvent(EcosCommentEventDto eventDto) {
        commentUpdateEmitter.emit(eventDto);
    }

    public void sendDeleteEvent(EcosCommentEventDto eventDto) {
        commentDeleteEmitter.emit(eventDto);
    }

}
