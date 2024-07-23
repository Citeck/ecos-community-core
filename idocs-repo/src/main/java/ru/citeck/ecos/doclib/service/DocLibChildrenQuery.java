package ru.citeck.ecos.doclib.service;

import lombok.Data;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

@Data
public class DocLibChildrenQuery {

    @Nullable
    private EntityRef parentRef;
    @Nullable
    private Predicate filter;
    @Nullable
    private DocLibNodeType nodeType;

    private boolean recursive;
}
