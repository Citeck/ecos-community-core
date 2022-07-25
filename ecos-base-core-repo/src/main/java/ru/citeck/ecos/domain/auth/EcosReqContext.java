package ru.citeck.ecos.domain.auth;

import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.commons.utils.ExceptionUtils;
import ru.citeck.ecos.commons.utils.func.UncheckedSupplier;

public class EcosReqContext {

    private static final ThreadLocal<EcosReqContextData> context = new ThreadLocal<>();

    private EcosReqContext() {
    }

    public static <T> T doWith(EcosReqContextData data, UncheckedSupplier<T> action) {
        final EcosReqContextData dataBefore = context.get();
        if (data != null) {
            context.set(data);
        } else {
            context.remove();
        }
        try {
            return action.get();
        } catch (Exception e) {
            ExceptionUtils.throwException(e);
            return null;
        } finally {
            if (dataBefore == null) {
                context.remove();
            } else {
                context.set(dataBefore);
            }
        }
    }

    @Nullable
    public static EcosReqContextData getCurrent() {
        return context.get();
    }

    public static boolean isSystemRequest() {
        EcosReqContextData data = context.get();
        return data != null && data.isSystemRequest();
    }
}
