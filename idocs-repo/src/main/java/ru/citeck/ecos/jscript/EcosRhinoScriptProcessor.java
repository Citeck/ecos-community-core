package ru.citeck.ecos.jscript;

import ecos.guava30.com.google.common.cache.Cache;
import ecos.guava30.com.google.common.cache.CacheBuilder;
import ecos.guava30.com.google.common.cache.CacheStats;
import lombok.SneakyThrows;
import org.alfresco.repo.jscript.RhinoScriptProcessor;
import org.alfresco.scripts.ScriptException;
import org.alfresco.scripts.ScriptResourceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import ru.citeck.ecos.commons.utils.digest.DigestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;

public class EcosRhinoScriptProcessor extends RhinoScriptProcessor {

    // use this logger instead of slf4j to allow pass it to ScriptResourceHelper
    private static final Log log = LogFactory.getLog(EcosRhinoScriptProcessor.class);

    private static final String EXEC_SCRIPT_IMPL_METHOD_NAME = "executeScriptImpl";
    private static final String MESSAGE_ERROR = "Failed to execute supplied script: ";
    private final Method executeScriptImplMethod;

    private boolean stringScriptsCacheEnabled = true;
    private int stringScriptsCacheMaxSize = 1000;
    private String stringScriptsCacheExpireAfterAccess = "PT1H";

    private Cache<String, Script> stringScriptsCache;

    @SneakyThrows
    public EcosRhinoScriptProcessor() {
        // get private method to call it from executeString
        executeScriptImplMethod = RhinoScriptProcessor.class.getDeclaredMethod(
            EXEC_SCRIPT_IMPL_METHOD_NAME,
            Script.class,
            Map.class,
            boolean.class,
            String.class
        );
        executeScriptImplMethod.setAccessible(true);
    }

    @Override
    public void register() {
        log.info("=== Register EcosRhinoScriptProcessor. " +
            "String scripts cache params -" +
            " enabled: " + stringScriptsCacheEnabled +
            " maxSize: " + stringScriptsCacheMaxSize +
            " expireAfterAccess: " + stringScriptsCacheExpireAfterAccess
        );
        stringScriptsCache = CacheBuilder.newBuilder()
            .maximumSize(stringScriptsCacheMaxSize)
            .expireAfterAccess(Duration.parse(stringScriptsCacheExpireAfterAccess))
            .recordStats()
            .build();
        super.register();
    }

    @Override
    public Object executeString(String source, Map<String, Object> model) {
        if (!stringScriptsCacheEnabled) {
            return super.executeString(source, model);
        }
        try {
            String key = DigestUtils.getSha256(source).getHash() + source.length();
            Script script = stringScriptsCache.get(key, () -> {
                Context cx = Context.enter();
                try {
                    return cx.compileString(resolveScriptImports(source), "AlfrescoJS", 1, null);
                } finally {
                    Context.exit();
                }
            });
            return executeScriptImplMethod.invoke(this, script, model, true, "string script");
        } catch (Throwable err) {
            if (err instanceof InvocationTargetException) {
                Throwable t = ((InvocationTargetException) err).getTargetException();
                if (t != null) {
                    throw new ScriptException(MESSAGE_ERROR + t.getMessage(), t);
                }
            }
            throw new ScriptException(MESSAGE_ERROR + err.getMessage(), err);
        }
    }

    private String resolveScriptImports(String script) {
        return ScriptResourceHelper.resolveScriptImports(script, this, log);
    }

    public CacheStats getStringScriptsCacheStats() {
        return stringScriptsCache.stats();
    }

    @Override
    public void reset() {
        super.reset();
        this.stringScriptsCache.invalidateAll();
    }

    public void setStringScriptsCacheEnabled(boolean stringScriptsCacheEnabled) {
        this.stringScriptsCacheEnabled = stringScriptsCacheEnabled;
    }

    public void setStringScriptsCacheMaxSize(int stringScriptsCacheMaxSize) {
        this.stringScriptsCacheMaxSize = stringScriptsCacheMaxSize;
    }

    public void setStringScriptsCacheExpireAfterAccess(String stringScriptsCacheExpireAfterAccess) {
        this.stringScriptsCacheExpireAfterAccess = stringScriptsCacheExpireAfterAccess;
    }
}
