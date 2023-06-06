package ru.citeck.ecos.records.value;

import lombok.RequiredArgsConstructor;
import org.alfresco.service.cmr.repository.NodeRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.atts.value.AttValue;
import ru.citeck.ecos.records3.record.atts.value.AttValuesConverter;
import ru.citeck.ecos.records3.record.atts.value.factory.AttValueFactory;

import java.util.Collections;
import java.util.List;

public class NodeRefValueFactory implements AttValueFactory<NodeRef> {

    @Nullable
    @Override
    public AttValue getValue(NodeRef nodeRef) {
        return new NodeRefValue(nodeRef);
    }

    @NotNull
    @Override
    public List<Class<?>> getValueTypes() {
        return Collections.singletonList(NodeRef.class);
    }

    @Override
    public void init(@NotNull AttValuesConverter attValuesConverter) {
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @RequiredArgsConstructor
    private static class NodeRefValue implements AttValue {

        private final NodeRef nodeRef;

        @Nullable
        @Override
        public Object getId() throws Exception {
            return nodeRef.toString();
        }

        @Nullable
        @Override
        public Object getDisplayName() throws Exception {
            return nodeRef.toString();
        }

        @Nullable
        @Override
        public Object getAs(@NotNull String type) throws Exception {
            if ("ref".equals(type)) {
                return RecordRef.create("", "", nodeRef.toString());
            }
            return null;
        }

        @Nullable
        @Override
        public String asText() throws Exception {
            return nodeRef.toString();
        }
    }
}
