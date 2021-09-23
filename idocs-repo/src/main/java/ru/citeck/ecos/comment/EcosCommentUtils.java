package ru.citeck.ecos.comment;

import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.records2.RecordRef;

/**
 * @author Roman Makarskiy
 */
public class EcosCommentUtils {

    private static final String APP_ALFRESCO = "alfresco";

    public static RecordRef commentIdToRecordRef(String id) {
        if (StringUtils.isBlank(id)) {
            return RecordRef.EMPTY;
        }

        RecordRef commentRef = RecordRef.valueOf(id);

        if (StringUtils.isBlank(commentRef.getAppName())) {
            commentRef = commentRef.withAppName(APP_ALFRESCO);
        }

        if (StringUtils.isBlank(commentRef.getSourceId())) {
            commentRef = commentRef.withSourceId(CommentRecords.ID);
        }

        return commentRef;
    }

}
