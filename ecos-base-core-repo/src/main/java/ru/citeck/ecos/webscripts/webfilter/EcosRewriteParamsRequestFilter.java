package ru.citeck.ecos.webscripts.webfilter;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

@WebFilter(urlPatterns = "/*")
public class EcosRewriteParamsRequestFilter implements Filter {

    private static final Pair<String, String> REPLACE_MAPPING = new ImmutablePair<>(
        "alfresco/@workspace://SpacesStore",
        "workspace://SpacesStore"
    );

    private static final List<String> SKIP_URI_CONTAINING = new ArrayList<>(Arrays.asList(
        "api/solr",
        "citeck/ecos/records"
    ));

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest &&
            needToFilter(servletRequest)
        ) {
            filterChain.doFilter(
                new RewriteParamsRequestWrapper((HttpServletRequest) servletRequest),
                servletResponse
            );
        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    @Override
    public void destroy() {

    }

    private boolean needToFilter(ServletRequest servletRequest) {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        for (String s : SKIP_URI_CONTAINING) {
            if (request.getRequestURI().contains(s)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("rawtypes")
    private class RewriteParamsRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String[]> parameters;

        public RewriteParamsRequestWrapper(HttpServletRequest request) {
            super(request);

            Map<String, String[]> newParams = new LinkedHashMap<>();
            Map requestParameterMap = request.getParameterMap();
            for (Object entryObj : requestParameterMap.entrySet()) {
                Map.Entry entry = (Map.Entry) entryObj;
                String paramName = String.valueOf(entry.getKey());
                Object paramValue = entry.getValue();

                final String[] paramValueArray = toParamValueArray(paramValue);
                replaceParamValues(paramValueArray);

                newParams.put(paramName, paramValueArray);
            }

            parameters = Collections.unmodifiableMap(newParams);
        }

        private String[] toParamValueArray(Object paramValue) {

            final String[] paramValueArray;

            if (paramValue == null) {
                paramValueArray = new String[] { null };
            } else if (paramValue instanceof String[]) {
                paramValueArray = (String[]) paramValue;
            } else if (paramValue instanceof Collection) {
                Collection col = (Collection) paramValue;
                paramValueArray = new String[col.size()];
                int i = 0;
                for (Object o : col) {
                    paramValueArray[i++] = String.valueOf(o);
                }
            } else if (paramValue.getClass().isArray()) {
                int len = Array.getLength(paramValue);
                paramValueArray = new String[len];
                for (int i = 0; i < len; ++i) {
                    paramValueArray[i] = String.valueOf(Array.get(paramValue, i));
                }
            } else {
                paramValueArray = new String[] { String.valueOf(paramValue) };
            }

            return paramValueArray;
        }

        private void replaceParamValues(String[] paramValueArray) {
            for (int i = 0; i < paramValueArray.length; i++) {
                String val = paramValueArray[i];
                paramValueArray[i] = val.replaceAll(REPLACE_MAPPING.getLeft(), REPLACE_MAPPING.getRight());
            }
        }

        @Override
        public String getParameter(String name) {
            String[] values = parameters.get(name);
            if (ArrayUtils.isNotEmpty(values)) {
                return values[0];
            }
            return null;
        }

        @Override
        public Map getParameterMap() {
            return parameters;
        }

        @Override
        public Enumeration getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return parameters.get(name);
        }
    }
}
