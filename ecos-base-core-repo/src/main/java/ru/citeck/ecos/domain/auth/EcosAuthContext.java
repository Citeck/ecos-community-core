package ru.citeck.ecos.domain.auth;

import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.commons.utils.ExceptionUtils;
import ru.citeck.ecos.commons.utils.func.UncheckedSupplier;

public class EcosAuthContext {

    private static final ThreadLocal<EcosAuthContextData> context = new ThreadLocal<>();

    private EcosAuthContext() {
    }

    public static <T> T doWith(EcosAuthContextData data, UncheckedSupplier<T> action) {
        final EcosAuthContextData dataBefore = context.get();
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
    public static EcosAuthContextData getCurrent() {
        return context.get();
    }
}
