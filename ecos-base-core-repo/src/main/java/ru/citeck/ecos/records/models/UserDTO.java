package ru.citeck.ecos.records.models;

import lombok.Data;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;

import java.util.Date;

@Data
public class UserDTO {

    private String id;
    private String userName;
    private String firstName;
    private String lastName;
    private String middleName;
    private Date birthDate;
    private String displayName;

    @MetaAtt("cm:userName")
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @MetaAtt("cm:firstName")
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @MetaAtt("cm:lastName")
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @MetaAtt("cm:middleName")
    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    @MetaAtt("ecos:birthDate")
    public void setBirthDate(Date birthDate) {
        this.birthDate = birthDate;
    }

    @MetaAtt(".disp")
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
