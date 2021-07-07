package ru.citeck.ecos.domain.auth;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j
@WebFilter(urlPatterns = "/*")
public class EcosReqContextRequestFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        String authorization = null;
        String ecosUser = null;
        String timezoneHeader = null;
        float utcOffset = 0;
        if (request instanceof HttpServletRequest) {
            authorization = ((HttpServletRequest) request).getHeader("Authorization");
            ecosUser = ((HttpServletRequest) request).getHeader("X-ECOS-User");
            timezoneHeader = ((HttpServletRequest) request).getHeader("X-ECOS-Timezone");
            if (StringUtils.isNotBlank(timezoneHeader)) {
                String utcOffsetPart = timezoneHeader.split(";")[0];
                if (StringUtils.isNotBlank(utcOffsetPart)) {
                    try {
                        utcOffset = Float.parseFloat(utcOffsetPart);
                    } catch (NumberFormatException e) {
                        log.warn("Incorrect UTC offset: '" + utcOffsetPart + "'");
                    }
                }
            }

        }
        if (StringUtils.isNotBlank(authorization)
                || StringUtils.isNotBlank(ecosUser)
                || StringUtils.isNotBlank(timezoneHeader)) {

            EcosReqContext.doWith(new EcosReqContextData(ecosUser, authorization, timezoneHeader, utcOffset), () -> {
                chain.doFilter(request, response);
                return null;
            });
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
    }
}
