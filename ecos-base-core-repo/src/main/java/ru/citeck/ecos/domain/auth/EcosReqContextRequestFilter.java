package ru.citeck.ecos.domain.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.web.context.support.WebApplicationContextUtils;
import ru.citeck.ecos.context.lib.auth.AuthConstants;

import javax.crypto.SecretKey;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

@Slf4j
@WebFilter(urlPatterns = "/*")
public class EcosReqContextRequestFilter implements Filter {

    private static final String JWT_AUTHORITIES_KEY = "auth";
    private static final String JWT_TOKEN_PREFIX = "Bearer ";
    private static final String JWT_SECRET_PROP_KEY = "ecos.security.authentication.jwt.secret";
    private static final String AUTH_ROLE_SYSTEM = "ROLE_SYSTEM";

    private SecretKey jwtSecretKey;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

        Properties properties = (Properties) WebApplicationContextUtils
            .getRequiredWebApplicationContext(filterConfig.getServletContext())
            .getBean("global-properties");

        String jwtSecret = properties.getProperty(JWT_SECRET_PROP_KEY, "");
        if (StringUtils.isNotBlank(jwtSecret)) {
            byte[] jwtSecretBytes;
            try {
                jwtSecretBytes = Base64.getDecoder().decode(jwtSecret);
            } catch (Exception e) {
                jwtSecretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            }
            jwtSecretKey = Keys.hmacShaKeyFor(jwtSecretBytes);
        }

        String secretEmptyMsg = jwtSecretKey != null ? "non-empty" : "empty";
        log.info("Filter initialized with " + secretEmptyMsg + " jwt secret");
        if (jwtSecretKey == null) {
            log.warn("WARNING: JWT token is undefined and any unauthorized " +
                "service can supply authorities. " +
                "Please set " + JWT_SECRET_PROP_KEY + " to secure your app");
        }
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

        boolean isSystemRequest = false;
        if (StringUtils.isNotBlank(authorization)
            && authorization.startsWith(JWT_TOKEN_PREFIX)
            && authorization.length() > JWT_TOKEN_PREFIX.length()) {

            String token = authorization.substring(JWT_TOKEN_PREFIX.length());
            if (jwtSecretKey == null) {
                int signIdx = token.lastIndexOf('.');
                token = token.substring(0, signIdx + 1);
            }
            Claims claims;
            if (jwtSecretKey != null) {
                claims = Jwts.parser()
                    .setSigningKey(jwtSecretKey)
                    .parseClaimsJws(token)
                    .getBody();
            } else {
                claims = Jwts.parser()
                    .parseClaimsJwt(token)
                    .getBody();
            }
            //String[] jwtAuthorities = claims.get(JWT_AUTHORITIES_KEY).toString().split(",");
            if (AuthConstants.SYSTEM_USER.equals(claims.getSubject())) {
                isSystemRequest = true;
            }
        }

        if (StringUtils.isNotBlank(authorization)
                || StringUtils.isNotBlank(ecosUser)
                || StringUtils.isNotBlank(timezoneHeader)) {

            EcosReqContext.doWith(new EcosReqContextData(
                ecosUser,
                authorization,
                timezoneHeader,
                isSystemRequest,
                utcOffset
            ), () -> {
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
