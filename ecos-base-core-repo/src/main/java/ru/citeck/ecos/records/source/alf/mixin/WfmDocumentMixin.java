package ru.citeck.ecos.records.source.alf.mixin;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx;
import ru.citeck.ecos.records3.record.mixin.AttMixin;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
public class WfmDocumentMixin implements AttMixin {

    private static final String WFM_DOCUMENT = "wfm:document";
    private static final String WFM_DOCUMENT_PROP = WFM_DOCUMENT + "-prop";
    private static final List<String> PROVIDED_ATTS = Collections.singletonList(WFM_DOCUMENT);

    @Autowired
    public WfmDocumentMixin(AlfNodesRecordsDAO alfNodesRecordsDAO) {
        alfNodesRecordsDAO.addAttributesMixin(this);
    }

    @Nullable
    @Override
    public Object getAtt(@NotNull String name, @NotNull AttValueCtx value) throws Exception {
        if (WFM_DOCUMENT.equals(name)) {
            DataValue attValue = value.getAtt(WFM_DOCUMENT + "?id");
            if (attValue.isNull() || StringUtils.isBlank(attValue.asText())) {
                attValue = value.getAtt(WFM_DOCUMENT_PROP + "?id");
            }
            return RecordRef.valueOf(attValue.asText());
        }
        return null;
    }

    @NotNull
    @Override
    public Collection<String> getProvidedAtts() {
        return PROVIDED_ATTS;
    }
}
