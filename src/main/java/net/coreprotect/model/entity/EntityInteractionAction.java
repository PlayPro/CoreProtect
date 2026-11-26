package net.coreprotect.model.entity;

public enum EntityInteractionAction {

    GENERIC(0, "clicked"),
    SHEAR(1, "sheared"),
    LEASH(2, "leashed"),
    UNLEASH(3, "unleashed");

    private final int id;
    private final String displayVerb;

    EntityInteractionAction(int id, String displayVerb) {
        this.id = id;
        this.displayVerb = displayVerb;
    }

    public static EntityInteractionAction fromId(int id) {
        for (EntityInteractionAction action : values()) {
            if (action.id == id) {
                return action;
            }
        }
        return GENERIC;
    }

    public int getId() {
        return id;
    }

    public String getDisplayVerb() {
        return displayVerb;
    }
}
