package ru.citeck.ecos.cmmn.legacyeditor.api.records;

import lombok.Data;
import org.alfresco.service.cmr.repository.NodeRef;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.cmmn.legacyeditor.service.CmmnLegacyEditorService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts;
import ru.citeck.ecos.records3.record.dao.RecordsDao;
import ru.citeck.ecos.records3.record.dao.delete.DelStatus;
import ru.citeck.ecos.records3.record.dao.delete.RecordDeleteDao;
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao;

/**
 * Mutate without
 */
@Component
public class CmmnLegacyEditorRecordsDao implements RecordsDao, RecordMutateDao, RecordDeleteDao {

    private final CmmnLegacyEditorService cmmnLegacyEditorService;

    @Autowired
    public CmmnLegacyEditorRecordsDao(CmmnLegacyEditorService cmmnLegacyEditorService) {
        this.cmmnLegacyEditorService = cmmnLegacyEditorService;
    }

    @NotNull
    @Override
    public String mutate(@NotNull LocalRecordAtts localRecordAtts) {

        MutateAtts atts = localRecordAtts.getAttributes().getAs(MutateAtts.class);
        if (atts == null) {
            throw new RuntimeException("Incorrect request: " + localRecordAtts);
        }

        if (localRecordAtts.getId().isEmpty()) {
            if (RecordRef.isEmpty(atts.templateRef)) {
                throw new RuntimeException("Template ref is a mandatory attribute " +
                    "for record with empty ID. Atts: " + localRecordAtts);
            }

            NodeRef templateRoot = cmmnLegacyEditorService.getTempCaseWithTemplate(atts.templateRef);

            return templateRoot.toString();
        }

        if (Boolean.TRUE.equals(atts.save)) {
            if (!localRecordAtts.getId().startsWith("workspace://")) {
                throw new RuntimeException("Record ID should start with workspace://... to save template");
            }
            cmmnLegacyEditorService.saveTemplate(new NodeRef(localRecordAtts.getId()));
        }

        return localRecordAtts.getId();
    }

    @NotNull
    @Override
    public DelStatus delete(@NotNull String tempCaseNodeRefStr) {
        cmmnLegacyEditorService.deleteTempCase(new NodeRef(tempCaseNodeRefStr));
        return DelStatus.OK;
    }

    @NotNull
    @Override
    public String getId() {
        return "cmmn-legacy-editor";
    }

    @Data
    public static class MutateAtts {
        private RecordRef templateRef;
        private Boolean save;
    }
}
