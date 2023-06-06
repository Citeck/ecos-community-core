package ru.citeck.ecos.records.value;

import lombok.RequiredArgsConstructor;
import org.alfresco.service.namespace.QName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.records3.record.atts.value.AttValue;
import ru.citeck.ecos.records3.record.atts.value.AttValuesConverter;
import ru.citeck.ecos.records3.record.atts.value.factory.AttValueFactory;

import java.util.Collections;
import java.util.List;

public class AlfQNameValueFactory implements AttValueFactory<QName> {

    @Nullable
    @Override
    public AttValue getValue(QName nodeRef) {
        return new QNameValue(nodeRef);
    }

    @NotNull
    @Override
    public List<Class<?>> getValueTypes() {
        return Collections.singletonList(QName.class);
    }

    @Override
    public void init(@NotNull AttValuesConverter attValuesConverter) {
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @RequiredArgsConstructor
    private static class QNameValue implements AttValue {

        private final QName qname;

        @Nullable
        @Override
        public Object getId() throws Exception {
            return qname.toString();
        }

        @Nullable
        @Override
        public Object getDisplayName() throws Exception {
            return qname.toString();
        }

        @Nullable
        @Override
        public String asText() throws Exception {
            return qname.toString();
        }
    }
}
