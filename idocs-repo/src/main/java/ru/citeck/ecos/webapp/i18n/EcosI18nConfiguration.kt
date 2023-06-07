package ru.citeck.ecos.webapp.i18n

import org.springframework.context.annotation.Configuration
import org.springframework.extensions.surf.util.I18NUtil
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.context.lib.i18n.component.I18nComponent
import java.util.*
import javax.annotation.PostConstruct

@Configuration
class EcosI18nConfiguration {

    @PostConstruct
    fun init() {
        I18nContext.component = object : I18nComponent {
            override fun <T> doWithLocales(locales: List<Locale>, action: () -> T): T {
                I18NUtil.setLocale(locales[0])
                try {
                    return action.invoke()
                } finally {
                    I18NUtil.setLocale(I18nContext.DEFAULT[0])
                }
            }
            override fun getLocales(): List<Locale> {
                return listOf(I18NUtil.getLocale() ?: I18nContext.DEFAULT[0])
            }
        }
    }
}
