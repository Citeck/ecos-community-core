package ru.citeck.ecos.comment.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Roman Makarskiy
 */
@AllArgsConstructor
public enum CommentPermissions {

    CAN_EDIT("canEdit"), CAN_DELETE("canDelete");

    @Getter
    private String value;
}
