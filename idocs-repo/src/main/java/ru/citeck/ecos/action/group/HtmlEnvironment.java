package ru.citeck.ecos.action.group;

import freemarker.template.Configuration;
import lombok.Data;

import java.io.ByteArrayOutputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

/**
 * Objects necessary for export to Html-file
 */
@Data
public class HtmlEnvironment {
    private GroupActionConfig config;
    private Writer writer;
    private Configuration templateConfiguration;
    private ByteArrayOutputStream innerStream;
    private Map<String, Object> templateParams = new HashMap<>();

    HtmlEnvironment(GroupActionConfig config) {
        this.config = config;
    }
}
