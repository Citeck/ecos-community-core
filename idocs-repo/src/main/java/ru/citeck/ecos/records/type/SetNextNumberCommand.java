package ru.citeck.ecos.records.type;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.commands.annotation.CommandType;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

@Data
@NoArgsConstructor
@AllArgsConstructor
@CommandType("ecos.number.template.set-next")
public class SetNextNumberCommand {

    private EntityRef templateRef;
    private String counterKey;
    private long nextNumber;
}
