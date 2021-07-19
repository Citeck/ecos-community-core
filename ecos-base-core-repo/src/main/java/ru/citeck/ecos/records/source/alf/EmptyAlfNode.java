package ru.citeck.ecos.records.source.alf;

import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.graphql.meta.value.MetaEdge;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;

public class EmptyAlfNode implements MetaValue {

    private AlfGqlContext context;

    @Override
    public <T extends QueryContext> void init(T context, MetaField field) {
        this.context = (AlfGqlContext) context;
    }

    @Override
    public MetaEdge getEdge(String name, MetaField field) {
        return new AlfNodeMetaEdge(context, null, name, name, this);
    }

    @Override
    public String getString() {
        return null;
    }

    @Override
    public Object getAttribute(String name, MetaField field) {

        if (RecordConstants.ATT_FORM_MODE.equals(name)) {
            return RecordConstants.FORM_MODE_CREATE;
        }

        return null;
    }
}
