package ru.citeck.ecos.domain.auth;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j
@WebFilter(urlPatterns = "/*")
public class EcosAuthContextRequestFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        String authorization = null;
        String ecosUser = null;
        if (request instanceof HttpServletRequest) {
            authorization = ((HttpServletRequest) request).getHeader("Authorization");
            ecosUser = ((HttpServletRequest) request).getHeader("X-ECOS-User");
        }
        if (StringUtils.isNotBlank(authorization) || StringUtils.isNotBlank(ecosUser)) {
            EcosAuthContext.doWith(new EcosAuthContextData(ecosUser, authorization), () -> {
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
