package net.coreprotect.model.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VillagerReputationData {

    private final UUID uuid;
    private final List<Value> values;

    private VillagerReputationData(UUID uuid, List<Value> values) {
        this.uuid = uuid;
        this.values = values;
    }

    public static VillagerReputationData parse(Object reputationObject) {
        if (!(reputationObject instanceof List<?>)) {
            return null;
        }

        List<?> reputationData = (List<?>) reputationObject;
        if (reputationData.size() < 2 || !(reputationData.get(0) instanceof String) || !(reputationData.get(1) instanceof List<?>)) {
            return null;
        }

        UUID uuid = parseUuid((String) reputationData.get(0));
        if (uuid == null) {
            return null;
        }

        List<Value> values = new ArrayList<>();
        for (Object valueObject : (List<?>) reputationData.get(1)) {
            if (!(valueObject instanceof List<?>)) {
                continue;
            }

            List<?> valueData = (List<?>) valueObject;
            if (valueData.size() >= 2 && valueData.get(0) instanceof String && valueData.get(1) instanceof Number) {
                values.add(new Value((String) valueData.get(0), ((Number) valueData.get(1)).intValue()));
            }
        }

        return new VillagerReputationData(uuid, values);
    }

    public UUID uuid() {
        return uuid;
    }

    public List<Value> values() {
        return values;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        }
        catch (Exception e) {
            return null;
        }
    }

    public static final class Value {
        private final String type;
        private final int amount;

        private Value(String type, int amount) {
            this.type = type;
            this.amount = amount;
        }

        public String type() {
            return type;
        }

        public int amount() {
            return amount;
        }
    }
}
