package ru.citeck.ecos.action.group;

import lombok.Data;
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef;
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType;

/**
 * Class represents column of export file from columns list.
 * Used for export group action.
 * <p>Example:
 * <code>
 *     <pre>
 * "type": "server-group-action",
 * "config": {
 *   "params": {
 *      "columns": [{
 *           "attribute": "cm:title",
 *           "name": "Title"
 *         },
 *         {
 *           "attribute": "idocs:currencyDocument",
 *           "name": "Currency"
 *         }]
 *    }
 * }
 * </pre><code/>
 */
@Data
public class ReportColumnDef {

    private String name;
    private String attribute;
    private AttributeType type;
    private boolean multiple = false;

    public ReportColumnDef() {}

    public ReportColumnDef(String name, String attribute) {
        this(name, attribute, AttributeType.TEXT, false);
    }

    public ReportColumnDef(String name, String attribute, AttributeType type, Boolean multiple) {
        this.name = name;
        this.type = type;
        this.attribute = attribute;
        setMultiple(multiple);
    }

    public void setMultiple(Boolean multiple) {
        this.multiple = Boolean.TRUE.equals(multiple);
    }
}
