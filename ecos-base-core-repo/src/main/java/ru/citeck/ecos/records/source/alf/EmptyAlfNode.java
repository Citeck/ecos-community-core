package ru.citeck.ecos.records.source.alf;

import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.records2.graphql.GqlContext;
import ru.citeck.ecos.records2.graphql.meta.value.MetaEdge;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;

public class EmptyAlfNode implements MetaValue {

    private AlfGqlContext context;

    @Override
    public <T extends GqlContext> void init(T context, MetaField field) {
        this.context = (AlfGqlContext) context;
    }

    @Override
    public MetaEdge getEdge(String name, MetaField field) {
        return new AlfNodeMetaEdge(context, null, name, this);
    }

    @Override
    public String getString() {
        return null;
    }
}
