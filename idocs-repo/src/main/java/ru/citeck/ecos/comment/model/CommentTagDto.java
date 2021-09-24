package ru.citeck.ecos.comment.model;

import lombok.Data;
import ru.citeck.ecos.comment.CommentTag;
import ru.citeck.ecos.commons.data.MLText;

/**
 * @author Roman Makarskiy
 */
@Data
public class CommentTagDto {

    private CommentTag type;
    private MLText name;

}
