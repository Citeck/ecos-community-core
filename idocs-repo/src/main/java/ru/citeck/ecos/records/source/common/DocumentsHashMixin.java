package ru.citeck.ecos.records.source.common;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.icase.completeness.records.CaseDocumentRecordsDao;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx;
import ru.citeck.ecos.records3.record.mixin.AttMixin;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.Collections;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DocumentsHashMixin implements AttMixin {

    private static final String DOCUMENTS_HASH_ATT = "documents-hash";

    private final CaseDocumentRecordsDao caseDocumentRecordsDao;
    private final AlfNodesRecordsDAO alfNodesRecordsDAO;

    @PostConstruct
    public void init() {
        alfNodesRecordsDAO.addAttributesMixin(this);
    }

    @Nullable
    @Override
    public Object getAtt(@NotNull String name, @NotNull AttValueCtx attValueCtx) throws Exception {
        if (!DOCUMENTS_HASH_ATT.equals(name)) {
            return null;
        }
        return caseDocumentRecordsDao.getAllDocsHash(attValueCtx.getRef());
    }

    @NotNull
    @Override
    public Collection<String> getProvidedAtts() {
        return Collections.singleton(DOCUMENTS_HASH_ATT);
    }
}
