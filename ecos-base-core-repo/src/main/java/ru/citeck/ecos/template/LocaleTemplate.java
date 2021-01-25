package ru.citeck.ecos.template;

import lombok.Setter;
import org.alfresco.repo.template.BaseTemplateProcessorExtension;
import org.alfresco.web.scripts.WebScriptUtils;

public class LocaleTemplate extends BaseTemplateProcessorExtension {
    @Setter
    private WebScriptUtils utilsScript;

    public String setLocale(String locale) {
        String curLocale = utilsScript.getLocale();
        utilsScript.setLocale(locale);
        return curLocale;
    }
}
