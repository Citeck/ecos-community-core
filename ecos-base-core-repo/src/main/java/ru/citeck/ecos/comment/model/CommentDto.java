package ru.citeck.ecos.comment.model;

import ecos.com.fasterxml.jackson210.databind.JsonNode;
import ecos.com.fasterxml.jackson210.databind.node.NullNode;
import lombok.Data;
import ru.citeck.ecos.records.models.AuthorityDTO;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author Roman Makarskiy
 */
@Data
public class CommentDto {

    private String id;
    private String text;

    private String record;

    private Date createdAt;
    private Date modifiedAt;

    private AuthorityDTO author;
    private AuthorityDTO editor;

    private boolean edited;

    private JsonNode permissions = NullNode.getInstance();

    private List<CommentTagDto> tags = Collections.emptyList();

}
