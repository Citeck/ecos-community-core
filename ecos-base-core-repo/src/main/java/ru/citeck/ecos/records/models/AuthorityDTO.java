package ru.citeck.ecos.records.models;

import lombok.Data;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman Makarskiy
 */
@Data
public class AuthorityDTO {

    private String id;

    @AttName("cm:authorityName")
    private String authorityName;

    @AttName("cm:userName")
    private String userName;

    @AttName("cm:firstName")
    private String firstName;

    @AttName("cm:lastName")
    private String lastName;

    @AttName("cm:middleName")
    private String middleName;

    @AttName(".disp")
    private String displayName;

    private List<UserDTO> containedUsers = new ArrayList<>();
}
