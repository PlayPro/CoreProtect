package net.coreprotect.model.rollback;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.bukkit.Material;

import net.coreprotect.model.SignState;

public final class SignRollbackChange {
    private final SignState after;
    private final boolean front;
    private final List<?> included;
    private final List<?> excluded;
    private SignState before;

    public SignRollbackChange(SignState after, boolean front) {
        this(after, front, Collections.emptyList(), Collections.emptyList());
    }

    public SignRollbackChange(SignState after, boolean front, Collection<?> included, Collection<?> excluded) {
        this.after = after;
        this.front = front;
        this.included = List.copyOf(included);
        this.excluded = List.copyOf(excluded);
    }

    public SignState getAfter() {
        return after;
    }

    public SignState getBefore() {
        return before;
    }

    public void setBefore(SignState before) {
        this.before = before;
    }

    public boolean isFront() {
        return front;
    }

    public boolean allows(Material type) {
        return (included.isEmpty() || contains(included, type)) && !contains(excluded, type);
    }

    private static boolean contains(List<?> types, Material type) {
        return types.contains(type) || types.contains(type.getKey().toString()) || types.contains(type.name());
    }
}
