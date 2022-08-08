package ru.citeck.ecos.records.models;

import lombok.Data;
import ru.citeck.ecos.records.source.PeopleRecordsDao;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;

import java.util.Date;

@Data
public class UserWithAvatarDto {

    private String id;
    private String userName;
    private String firstName;
    private String lastName;
    private String middleName;
    private Date birthDate;
    private String displayName;
    private String avatarUrl;

    @AttName("cm:userName")
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @AttName("cm:firstName")
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @AttName("cm:lastName")
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @AttName("cm:middleName")
    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    @AttName("ecos:birthDate")
    public void setBirthDate(Date birthDate) {
        this.birthDate = birthDate;
    }

    @AttName(".disp")
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @AttName(PeopleRecordsDao.PERSON_AVATAR + ".url")
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
