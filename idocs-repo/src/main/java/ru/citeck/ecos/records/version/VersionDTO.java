package ru.citeck.ecos.records.version;

import lombok.Data;
import ru.citeck.ecos.records.models.UserWithAvatarDto;

import java.util.Date;

/**
 * @author Roman Makarskiy
 */
@Data
public class VersionDTO {

    private String id;

    private String  name;
    private String version;
    private Date modified;
    private UserWithAvatarDto modifier;
    private String comment;
    private String downloadUrl;
}
