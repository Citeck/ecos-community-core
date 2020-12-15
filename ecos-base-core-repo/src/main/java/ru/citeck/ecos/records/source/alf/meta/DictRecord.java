package ru.citeck.ecos.records.source.alf.meta;

import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.records.source.alf.AlfNodeMetaEdge;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.graphql.meta.value.MetaEdge;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.utils.DictUtils;

public class DictRecord implements MetaValue {

    private QName fullName;
    private String formKey;
    private String shortName;

    private AlfGqlContext context;
    private DictUtils dictUtils;

    public DictRecord(QName fullName, String shortName, String formKey) {
        this.formKey = formKey;
        this.fullName = fullName;
        this.shortName = shortName;
    }

    @Override
    public String getId() {
        return shortName;
    }

    @Override
    public <T extends QueryContext> void init(T context, MetaField field) {
        this.context = (AlfGqlContext) context;
        this.dictUtils = this.context.getService(DictUtils.QNAME);
    }

    @Override
    public Object getAttribute(String name, MetaField field) {

        switch (name) {
            case RecordConstants.ATT_FORM_KEY:
                return formKey;
            case RecordConstants.ATT_FORM_MODE:
                return RecordConstants.FORM_MODE_CREATE;
            default:
                return null;
        }
    }

    @Override
    public String getDisplayName() {
        return dictUtils.getTypeTitle(fullName);
    }

    @Override
    public MetaEdge getEdge(String name, MetaField field) {
        return new AlfNodeMetaEdge(context, fullName, name, name, this);
    }

    @Override
    public boolean has(String parentShortName) {
        if (StringUtils.isBlank(parentShortName)) {
            return false;
        }

        QName parentFullName = QName.resolveToQName(context.getNamespaceService(), parentShortName);

        return parentFullName != null ? context.getDictionaryService().isSubClass(fullName, parentFullName) : false;
    }
}
