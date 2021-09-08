package ru.citeck.ecos.action.group;

import lombok.Data;

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
}
