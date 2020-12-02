package ru.citeck.ecos.doclib.service;

import lombok.Data;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.model.Predicate;

@Data
public class DocLibChildrenQuery {

    @Nullable
    private RecordRef parentRef;
    @Nullable
    private Predicate filter;
    @Nullable
    private DocLibNodeType nodeType;

    private boolean recursive;
}
