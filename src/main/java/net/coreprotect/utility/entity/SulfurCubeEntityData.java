package net.coreprotect.utility.entity;

import java.lang.reflect.Method;
import java.util.List;

import org.bukkit.entity.Entity;

public final class SulfurCubeEntityData {

    private static volatile EntityAccess entityAccess;
    private static volatile boolean entityAccessUnavailable;

    private SulfurCubeEntityData() {
        throw new IllegalStateException("Utility class");
    }

    public static void appendMetadata(Entity entity, List<Object> info) {
        EntityAccess access = getEntityAccess();
        if (access == null || !access.sulfurCubeClass.isInstance(entity)) {
            return;
        }

        try {
            info.add(access.getSize.invoke(entity));
            info.add(access.getFuseTicks.invoke(entity));
            info.add(access.isFromBucket.invoke(entity));
        }
        catch (ReflectiveOperationException | LinkageError | ClassCastException | IllegalArgumentException exception) {
            // Ignore missing or incompatible Paper 26.2 Sulfur Cube APIs.
        }
    }

    public static boolean applyMetadata(Entity entity, Object value, int count) {
        EntityAccess access = getEntityAccess();
        if (access == null || !access.sulfurCubeClass.isInstance(entity)) {
            return false;
        }

        try {
            if (count == 0 && value instanceof Number) {
                access.setSize.invoke(entity, ((Number) value).intValue());
            }
            else if (count == 1 && value instanceof Number) {
                access.setFuseTicks.invoke(entity, ((Number) value).intValue());
            }
            else if (count == 2 && value instanceof Boolean) {
                access.setFromBucket.invoke(entity, value);
            }
        }
        catch (ReflectiveOperationException | LinkageError | ClassCastException | IllegalArgumentException exception) {
            // Ignore missing or incompatible Paper 26.2 Sulfur Cube APIs.
        }

        return true;
    }

    private static EntityAccess getEntityAccess() {
        if (entityAccessUnavailable) {
            return null;
        }

        EntityAccess access = entityAccess;
        if (access != null) {
            return access;
        }

        try {
            Class<?> sulfurCubeClass = Class.forName("org.bukkit.entity.SulfurCube");
            Class<?> abstractCubeMobClass = Class.forName("org.bukkit.entity.AbstractCubeMob");
            Class<?> bucketableClass = Class.forName("io.papermc.paper.entity.Bucketable");

            access = new EntityAccess(
                    sulfurCubeClass,
                    abstractCubeMobClass.getMethod("getSize"),
                    abstractCubeMobClass.getMethod("setSize", int.class),
                    sulfurCubeClass.getMethod("getFuseTicks"),
                    sulfurCubeClass.getMethod("setFuseTicks", int.class),
                    bucketableClass.getMethod("isFromBucket"),
                    bucketableClass.getMethod("setFromBucket", boolean.class));
            entityAccess = access;
            return access;
        }
        catch (ReflectiveOperationException | LinkageError exception) {
            entityAccessUnavailable = true;
            return null;
        }
    }

    private static final class EntityAccess {

        private final Class<?> sulfurCubeClass;
        private final Method getSize;
        private final Method setSize;
        private final Method getFuseTicks;
        private final Method setFuseTicks;
        private final Method isFromBucket;
        private final Method setFromBucket;

        private EntityAccess(Class<?> sulfurCubeClass, Method getSize, Method setSize, Method getFuseTicks,
                Method setFuseTicks, Method isFromBucket, Method setFromBucket) {
            this.sulfurCubeClass = sulfurCubeClass;
            this.getSize = getSize;
            this.setSize = setSize;
            this.getFuseTicks = getFuseTicks;
            this.setFuseTicks = setFuseTicks;
            this.isFromBucket = isFromBucket;
            this.setFromBucket = setFromBucket;
        }
    }
}
