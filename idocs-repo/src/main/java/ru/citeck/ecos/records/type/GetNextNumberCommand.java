package ru.citeck.ecos.records.type;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.commands.annotation.CommandType;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

@Data
@NoArgsConstructor
@AllArgsConstructor
@CommandType("ecos.number.template.get-next")
public class GetNextNumberCommand {

    private EntityRef templateRef;
    private ObjectData model;
    private String counterKey;
    private Boolean increment = true;

    public GetNextNumberCommand(EntityRef templateRef, ObjectData model) {
        this.templateRef = templateRef;
        this.model = model;
    }

    public GetNextNumberCommand(EntityRef templateRef, String counterKey) {
        this.templateRef = templateRef;
        this.counterKey = counterKey;
    }
}
