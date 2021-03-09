package ru.citeck.ecos.comment;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Roman Makarskiy
 */
@AllArgsConstructor
public enum CommentTag {

    TASK("task"), ACTION("action");

    @Getter
    private final String value;

}
