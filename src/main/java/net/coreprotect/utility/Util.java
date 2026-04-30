package net.coreprotect.utility;

import java.util.function.Consumer;

import net.coreprotect.consumer.Queue;

/**
 * Central utility class that provides access to various utility functions.
 * Most methods delegate to specialized utility classes.
 */
public class Util extends Queue {

    private Util() {
        throw new IllegalStateException("Utility class");
    }

    public static <T> T make(T value, Consumer<T> initializer) {
        initializer.accept(value);
        return value;
    }
}
