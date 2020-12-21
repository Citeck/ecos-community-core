package ru.citeck.ecos.records.source.alf.meta;

import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.model.lib.status.dto.StatusDef;
import ru.citeck.ecos.model.lib.type.service.TypeDefService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.graphql.meta.value.SimpleMetaEdge;

import java.util.*;

public class EcosStatusEdge extends SimpleMetaEdge {

    private final TypeDefService typeDefService;
    private final RecordRef record;

    public EcosStatusEdge(RecordRef record, AlfGqlContext context, MetaValue scope) {
        super("_status", scope);
        this.typeDefService = context.getTypeDefService().orElse(null);
        this.record = record;
    }

    @Nullable
    @Override
    public List<StatusDef> getOptions() {
        if (typeDefService == null) {
            return new LinkedList<>();
        }
        RecordRef typeRef = typeDefService.getTypeRef(record);
        Map<String, StatusDef> statuses = typeDefService.getStatuses(typeRef);
        return new ArrayList<>(statuses.values());
    }
}
