package ru.citeck.ecos.comment.model;

import lombok.Data;
import ru.citeck.ecos.commons.data.MLText;

/**
 * @author Roman Makarskiy
 */
@Data
public class CommentTagDto {

    private String type;
    private MLText name;

}
