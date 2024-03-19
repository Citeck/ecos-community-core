package ru.citeck.ecos.icase.activity.service.eproc;

import org.alfresco.util.Pair;
import ru.citeck.ecos.cmmn.model.Definitions;
import ru.citeck.ecos.icase.activity.dto.*;
import ru.citeck.ecos.icase.activity.service.eproc.importer.pojo.OptimizedProcessDefinition;
import ru.citeck.ecos.records2.RecordRef;

import java.util.List;
import java.util.Optional;

public interface EProcActivityService {

    Optional<Pair<String, OptimizedProcessDefinition>> getOptimizedDefinitionWithRevisionId(RecordRef caseRef);

    Optional<ProcessDefinition> getFullDefinition(RecordRef caseRef);

    Optional<Definitions> getXmlProcDefinition(RecordRef caseRef);

    ProcessInstance createDefaultState(RecordRef caseRef);

    ProcessInstance createDefaultState(RecordRef caseRef, String revisionId, OptimizedProcessDefinition definition);

    Optional<ProcessInstance> getFullState(RecordRef caseRef);

    void saveState(RecordRef caseRef);

    ActivityDefinition getActivityDefinition(ActivityRef activityRef);

    ActivityInstance getStateInstance(ActivityRef activityRef);

    SentryDefinition getSentryDefinition(EventRef eventRef);

    List<SentryDefinition> findSentriesBySourceRefAndEventType(RecordRef caseRef, String sourceRef, String eventType);

    List<SentryDefinition> findSentriesBySourceRefAndEventType(
        RecordRef caseRef,
        String sourceRef,
        String eventType,
        boolean procDefRequired
    );
}
