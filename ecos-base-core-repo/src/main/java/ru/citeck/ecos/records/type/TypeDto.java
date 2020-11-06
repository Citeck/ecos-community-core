package ru.citeck.ecos.records.type;

import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;

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
    private RecordRef numTemplateRef;
    private boolean system;
    private String dashboardType;
    private boolean inheritActions;

    private MLText dispNameTemplate;
    private boolean inheritNumTemplate;

    private List<String> aliases = new ArrayList<>();

    private List<RecordRef> actions = new ArrayList<>();
    private List<AssociationDto> associations = new ArrayList<>();
    private List<CreateVariantDto> createVariants = new ArrayList<>();

    private ObjectData attributes = ObjectData.create();

    private RecordRef configFormRef;
    private ObjectData config = ObjectData.create();

    @MetaAtt("model?json")
    private TypeModelDef model;

    @MetaAtt("_type?id")
    private RecordRef ecosType;

    @MetaAtt("_type")
    public RecordRef getEcosType() {
        return ecosType;
    }
}
