package net.coreprotect.consumer;

public final class QueuePayload {

    private final Object primary;
    private final Object detail;

    public QueuePayload(Object primary, Object detail) {
        this.primary = primary;
        this.detail = detail;
    }

    public static Object primary(Object value) {
        return value instanceof QueuePayload ? ((QueuePayload) value).primary : value;
    }

    @SuppressWarnings("unchecked")
    public static <T> T detail(Object value) {
        return value instanceof QueuePayload ? (T) ((QueuePayload) value).detail : null;
    }
}
