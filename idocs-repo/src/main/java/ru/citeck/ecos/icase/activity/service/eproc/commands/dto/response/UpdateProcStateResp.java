package ru.citeck.ecos.icase.activity.service.eproc.commands.dto.response;

import lombok.Data;

@Data
public class UpdateProcStateResp {
    private String procStateId;
    private int version;
}
