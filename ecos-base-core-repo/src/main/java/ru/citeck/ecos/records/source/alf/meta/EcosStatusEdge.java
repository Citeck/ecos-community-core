package ru.citeck.ecos.records.source.alf.meta;

import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.model.lib.status.dto.StatusDef;
import ru.citeck.ecos.model.lib.status.service.StatusService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.graphql.meta.value.SimpleMetaEdge;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class EcosStatusEdge extends SimpleMetaEdge {

    private final StatusService statusService;
    private final RecordRef record;

    public EcosStatusEdge(RecordRef record, AlfGqlContext context, MetaValue scope) {
        super("_status", scope);
        this.statusService = context.getStatusService();
        this.record = record;
    }

    @Nullable
    @Override
    public List<StatusDef> getOptions() {
        if (statusService == null) {
            return new LinkedList<>();
        }
        Map<String, StatusDef> statuses = statusService.getStatusesByDocument(record);
        return new ArrayList<>(statuses.values());
    }
}
