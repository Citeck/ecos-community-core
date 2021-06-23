package ru.citeck.ecos.domain.servlet;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j
@WebFilter(urlPatterns = "/*")
public class ServletRequestContextFilter implements Filter {

    private static final ThreadLocal<ServletRequest> currentRequest = new ThreadLocal<>();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        currentRequest.set(request);
        try {
            chain.doFilter(request, response);
        } finally {
            currentRequest.remove();
        }
    }

    @Override
    public void destroy() {
    }

    @Nullable
    public static HttpServletRequest getCurrentHttpRequest() {
        ServletRequest request = currentRequest.get();
        if (request instanceof HttpServletRequest) {
            return (HttpServletRequest) request;
        }
        return null;
    }
}
