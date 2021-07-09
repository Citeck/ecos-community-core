package ru.citeck.ecos.records.type;

import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.lib.type.dto.DocLibDef;
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class TypeDto {

    @NotNull
    private String id;
    private MLText name;
    private MLText description;
    private String tenant;
    private String sourceId;
    private RecordRef parentRef;
    private RecordRef formRef;
    private RecordRef journalRef;
    private RecordRef inhNumTemplateRef;
    private boolean system;
    private String dashboardType;
    private boolean inheritActions;

    private MLText inhDispNameTemplate;
    private boolean inheritNumTemplate;

    private List<String> aliases = new ArrayList<>();

    private List<RecordRef> actions = new ArrayList<>();
    private List<AssociationDto> associations = new ArrayList<>();
    private List<CreateVariantDto> createVariants = new ArrayList<>();

    private ObjectData properties = ObjectData.create();
    private ObjectData inhAttributes = ObjectData.create();

    private RecordRef configFormRef;
    private ObjectData config = ObjectData.create();

    @AttName("model?json")
    private TypeModelDef model;

    @AttName("resolvedModel?json")
    private TypeModelDef resolvedModel;

    @AttName("_type?id")
    private RecordRef ecosType;

    @AttName("docLib?json")
    private DocLibDef docLib;

    @AttName("resolvedDocLib?json")
    private DocLibDef resolvedDocLib;

    @AttName("?disp")
    public MLText getDisplayName() {
        return name;
    }

    @AttName("_type")
    public RecordRef getEcosType() {
        return ecosType;
    }

    public void setDispNameTemplate(MLText dispNameTemplate) {
        if (MLText.isEmpty(inhDispNameTemplate)) {
            inhDispNameTemplate = dispNameTemplate;
        }
    }
}
