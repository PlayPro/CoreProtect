package net.coreprotect.model.lookup;

/**
 * The virtual containers selected by a container: lookup: every container of a type, or a single container when the
 * id is set. It travels in the lookup's include list.
 */
public final class VirtualContainerTarget {

    private final String type;
    private final String id;

    public VirtualContainerTarget(String type, String id) {
        this.type = type;
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }
}
