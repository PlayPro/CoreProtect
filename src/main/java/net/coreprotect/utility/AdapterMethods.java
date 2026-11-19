package net.coreprotect.utility;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AdapterMethods {

    private static final ClassValue<ConcurrentMap<List<?>, Optional<Method>>> METHODS = new ClassValue<>() {
        @Override
        protected ConcurrentMap<List<?>, Optional<Method>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private AdapterMethods() {
    }

    public static Method find(Class<?> type, String name, Class<?>... parameterTypes) {
        return METHODS.get(type).computeIfAbsent(List.of(name, List.of(parameterTypes)), key -> {
            try {
                return Optional.of(type.getMethod(name, parameterTypes));
            }
            catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    public static Method findAssignableSingleArgument(Class<?> type, String name, Class<?> argumentType) {
        return METHODS.get(type).computeIfAbsent(List.of(name, argumentType), key -> {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(argumentType)) {
                    return Optional.of(method);
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    public static Object invokeNoArguments(Object target, String name) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Method method = find(target.getClass(), name);
        return method == null ? null : method.invoke(target);
    }

    public static Class<?> findType(String name) {
        try {
            return Class.forName(name);
        }
        catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
