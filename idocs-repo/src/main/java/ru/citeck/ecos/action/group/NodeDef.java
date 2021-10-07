package ru.citeck.ecos.action.group;

import lombok.Data;

/**
 * Class represents attribute value for *.FTL template
 */
@Data
public class NodeDef {
    private String value;
    private String url;
    private boolean integerValue;

    public NodeDef(String value, String url, boolean isInteger) {
        this.value = value;
        this.url = url;
        this.integerValue = isInteger;
    }
}
