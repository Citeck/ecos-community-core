package ru.citeck.ecos.cmmn.legacyeditor.dao;

import org.alfresco.service.cmr.repository.NodeRef;
import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.records2.RecordRef;

public interface CmmnLegacyEditorTemplateDao {

    @NotNull
    NodeRef createTempCase(@NotNull RecordRef templateRef, @NotNull RecordRef typeRef);

    @NotNull
    NodeRef createTemplateNode(@NotNull NodeRef tempCaseNode);

    void deleteTempCaseNode(@NotNull NodeRef tempCaseNode);

    @NotNull
    RecordRef getOriginalTemplateRef(@NotNull NodeRef tempCaseNode);
}
