package net.coreprotect.bukkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.Art;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.TreeSpecies;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.utility.AdapterMethods;
import net.coreprotect.utility.BlockUtils;

/**
 * Base adapter implementation for Bukkit API compatibility.
 * Provides default implementations for methods that work across multiple Minecraft versions.
 * Version-specific implementations extend this class to provide specialized behavior.
 */
public class BukkitAdapter implements BukkitInterface {

    /** The currently active adapter instance */
    public static BukkitInterface ADAPTER;

    // Version constants for Bukkit implementations
    public static final int BUKKIT_V1_13 = 13;
    public static final int BUKKIT_V1_14 = 14;
    public static final int BUKKIT_V1_15 = 15;
    public static final int BUKKIT_V1_16 = 16;
    public static final int BUKKIT_V1_17 = 17;
    public static final int BUKKIT_V1_18 = 18;
    public static final int BUKKIT_V1_19 = 19;
    public static final int BUKKIT_V1_20 = 20;
    public static final int BUKKIT_V1_21 = 21;
    public static final int BUKKIT_V1_21_5 = 21005;
    public static final int BUKKIT_V1_21_11 = 21011;
    public static final int BUKKIT_V26_0 = 260000;
    public static final int BUKKIT_V26_1 = 260100;
    public static final int BUKKIT_V26_2 = 260200;
    public static final int BUKKIT_V26_3 = 260300;

    public static int getAdapterVersion(int major, int minor) {
        return getAdapterVersion(major, minor, 0);
    }

    public static int getAdapterVersion(int major, int minor, int patch) {
        if (major == 1) {
            return minor == 21 && patch >= 5 ? (minor * 1000) + patch : minor;
        }

        return (major * 10000) + (minor * 100) + patch;
    }

    /**
     * Initializes the appropriate Bukkit adapter based on the server version.
     * This method should be called during plugin initialization.
     */
    public static void loadAdapter() {
        int bukkitVersion = ConfigHandler.SERVER_VERSION;
        if (bukkitVersion >= BUKKIT_V26_3) {
            ADAPTER = new Bukkit_v26_3();
        }
        else if (bukkitVersion >= BUKKIT_V26_2) {
            ADAPTER = new Bukkit_v26_2();
        }
        else if (bukkitVersion >= BUKKIT_V1_21_11) {
            ADAPTER = new Bukkit_v1_21_11();
        }
        else if (bukkitVersion >= BUKKIT_V1_21_5) {
            ADAPTER = new Bukkit_v1_21_5();
        }
        else if (bukkitVersion >= BUKKIT_V1_21) {
            ADAPTER = new Bukkit_v1_21();
        }
        else if (bukkitVersion >= BUKKIT_V1_20) {
            ADAPTER = new Bukkit_v1_20();
        }
        else if (bukkitVersion >= BUKKIT_V1_19) {
            ADAPTER = new Bukkit_v1_19();
        }
        else if (bukkitVersion >= BUKKIT_V1_18) {
            ADAPTER = new Bukkit_v1_18();
        }
        else if (bukkitVersion >= BUKKIT_V1_17) {
            ADAPTER = new Bukkit_v1_17();
        }
        else {
            ADAPTER = new BukkitAdapter();
        }
    }

    // -------------------- Basic data conversion methods --------------------
    public static final Set<Material> EMPTY_SET = Collections.emptySet();

    @Override
    public String parseLegacyName(String name) {
        return name;
    }

    @Override
    public int getLegacyBlockId(Material material) {
        return -1;
    }

    // -------------------- Entity methods --------------------

    @Override
    public boolean isChunkEntitiesLoaded(Chunk chunk) {
        return true;
    }

    @Override
    public String getBoatVariant(Boat boat) {
        TreeSpecies wood = boat.getWoodType();
        return wood == null ? null : wood.name();
    }

    @Override
    public void setBoatVariant(Boat boat, String variant) {
        try {
            String legacy = "OAK".equals(variant) ? "GENERIC" : "SPRUCE".equals(variant) ? "REDWOOD" : variant;
            boat.setWoodType(TreeSpecies.valueOf(legacy));
        }
        catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public EntityType resolveStoredBoatType(boolean chest, String variant) {
        try {
            return EntityType.valueOf(chest ? "CHEST_BOAT" : "BOAT");
        }
        catch (IllegalArgumentException ignored) {
            String wood = normalizeBoatVariant(variant);
            if (wood != null) {
                String suffix = "BAMBOO".equals(wood) ? "RAFT" : "BOAT";
                try {
                    return EntityType.valueOf(wood + (chest ? "_CHEST_" : "_") + suffix);
                }
                catch (IllegalArgumentException unsupportedVariant) {
                }
            }
            try {
                return EntityType.valueOf(chest ? "OAK_CHEST_BOAT" : "OAK_BOAT");
            }
            catch (IllegalArgumentException unavailableType) {
                return EntityType.UNKNOWN;
            }
        }
    }

    protected static String normalizeBoatVariant(String variant) {
        if ("GENERIC".equals(variant)) {
            return "OAK";
        }
        if ("REDWOOD".equals(variant)) {
            return "SPRUCE";
        }
        return variant;
    }

    @Override
    public boolean getEntityMeta(LivingEntity entity, List<Object> info) {
        return false;
    }

    @Override
    public boolean hasBodyItemSlot() {
        return false;
    }

    @Override
    public ItemStack getBodyItem(EntityEquipment equipment) {
        return null;
    }

    @Override
    public void setBodyItem(EntityEquipment equipment, ItemStack item) {
    }

    @Override
    public boolean setEntityMeta(Entity entity, Object value, int count) {
        return false;
    }

    /**
     * Accesses registry-backed entity variants without exposing the entity interface to legacy
     * server bytecode remapping.
     */
    public static boolean getRegistryVariant(BukkitInterface adapter, Entity entity, List<Object> info, String getterName) {
        try {
            Object variant = AdapterMethods.invokeNoArguments(entity, getterName);
            if (variant == null) {
                return false;
            }

            info.add(adapter.getRegistryKey(variant));
            return true;
        }
        catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    /**
     * Restores registry-backed entity variants without exposing the entity interface to legacy
     * server bytecode remapping.
     */
    public static boolean setRegistryVariant(BukkitInterface adapter, Entity entity, Object value, String getterName, String setterName) {
        try {
            Method getter = AdapterMethods.find(entity.getClass(), getterName);
            if (getter == null) {
                return false;
            }
            Class<?> variantClass = getter.getReturnType();
            Object variant = resolveRegistryValue(adapter, value, variantClass);
            if (variant == null) {
                return false;
            }

            Method setter = AdapterMethods.find(entity.getClass(), setterName, variantClass);
            if (setter == null) {
                return false;
            }
            setter.invoke(entity, variant);
            return true;
        }
        catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    public static Object getLegacyEnumValue(Class<?> type, String name) {
        if (name == null || !type.isEnum()) {
            return null;
        }
        try {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Object value = Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
            return value;
        }
        catch (IllegalArgumentException | LinkageError exception) {
            return null;
        }
    }

    public static <T> T resolveRegistryValue(BukkitInterface adapter, Object value, Class<T> type) {
        try {
            Object resolved = adapter != null && value instanceof String ? adapter.getRegistryValue((String) value, type) : value;
            return type.isInstance(resolved) ? type.cast(resolved) : null;
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void addMerchantRecipeMeta(MerchantRecipe recipe, List<Object> recipeData) {
    }

    @Override
    public void setMerchantRecipeMeta(MerchantRecipe recipe, List<?> recipeData) {
    }

    @Override
    public void refreshVillagerBrain(Villager villager) {
        try {
            Object handle = AdapterMethods.invokeNoArguments(villager, "getHandle");
            Object level = AdapterMethods.invokeNoArguments(villager.getWorld(), "getHandle");
            if (handle != null && level != null) {
                Method refresh = AdapterMethods.findAssignableSingleArgument(handle.getClass(), "refreshBrain", level.getClass());
                if (refresh != null) {
                    refresh.invoke(handle, level);
                }
            }
        }
        catch (ReflectiveOperationException | LinkageError e) {
        }
    }

    @Override
    public void getWolfVariant(org.bukkit.entity.Wolf wolf, List<Object> info) {
        // Base implementation does nothing - Wolf variants only exist in 1.21+
    }

    @Override
    public void setWolfVariant(org.bukkit.entity.Wolf wolf, Object value) {
        // Base implementation does nothing - Wolf variants only exist in 1.21+
    }

    @Override
    public EntityType getEntityType(Material material) {
        switch (material) {
            case END_CRYSTAL:
                return EntityType.valueOf("ENDER_CRYSTAL");
            default:
                return EntityType.UNKNOWN;
        }
    }

    // -------------------- Item handling methods --------------------

    @Override
    public boolean getItemMeta(ItemMeta itemMeta, List<Map<String, Object>> list, List<List<Map<String, Object>>> metadata, int slot) {
        return false;
    }

    @Override
    public boolean setItemMeta(Material rowType, ItemStack itemstack, List<Map<String, Object>> map) {
        return false;
    }

    @Override
    public String getItemName(ItemMeta itemMeta) {
        return "";
    }

    @Override
    public Material getPlantSeeds(Material material) {
        switch (material) {
            case WHEAT:
                return Material.WHEAT_SEEDS;
            case PUMPKIN_STEM:
            case ATTACHED_PUMPKIN_STEM:
                return Material.PUMPKIN_SEEDS;
            case MELON_STEM:
            case ATTACHED_MELON_STEM:
                return Material.MELON_SEEDS;
            case BEETROOTS:
                return Material.BEETROOT_SEEDS;
            case CARROTS:
                return Material.CARROT;
            case POTATOES:
                return Material.POTATO;
            case SWEET_BERRY_BUSH:
                return Material.SWEET_BERRIES;
            case COCOA:
                return Material.COCOA_BEANS;
            case REDSTONE_WIRE:
                return Material.REDSTONE;
            case TRIPWIRE:
                return Material.STRING;
            case BAMBOO_SAPLING:
                return Material.BAMBOO;
            default:
                return material;
        }
    }

    @Override
    public boolean isSingleBlockPitcherCrop(BlockData blockData) {
        return false;
    }

    @Override
    public ItemStack adjustIngredient(MerchantRecipe recipe, ItemStack itemStack) {
        return null;
    }

    @Override
    public ItemStack getArrowItem(AbstractArrow arrow) {
        return null;
    }

    @Override
    public LivingEntity getFireworkRider(Firework firework) {
        return null;
    }

    @Override
    public ItemStack getArrowMeta(Arrow arrow, ItemStack itemStack) {
        PotionData data = arrow.getBasePotionData();
        if (data.getType() != PotionType.valueOf("UNCRAFTABLE")) {
            itemStack = new ItemStack(Material.TIPPED_ARROW);
            PotionMeta meta = (PotionMeta) itemStack.getItemMeta();
            meta.setBasePotionData(data);
            for (PotionEffect effect : arrow.getCustomEffects()) {
                meta.addCustomEffect(effect, false);
            }
            itemStack.setItemMeta(meta);
        }

        return itemStack;
    }

    // -------------------- Block methods --------------------

    @Override
    public boolean isAttached(Block block, Block scanBlock, BlockData blockData, int scanMin) {
        if (blockData instanceof Directional && blockData instanceof FaceAttachable) {
            Directional directional = (Directional) blockData;
            FaceAttachable faceAttachable = (FaceAttachable) blockData;

            boolean scanButton = false;
            switch (faceAttachable.getAttachedFace()) {
                case WALL:
                    scanButton = (scanMin < 5 && scanBlock.getRelative(directional.getFacing().getOppositeFace()).getLocation().equals(block.getLocation()));
                    break;
                case FLOOR:
                    scanButton = (scanMin == 5);
                    break;
                case CEILING:
                    scanButton = (scanMin == 6);
                    break;
                default:
                    break;
            }

            return scanButton;
        }

        return true; // unvalidated attachments default to true
    }

    @Override
    public int getMinHeight(World world) {
        return 0;
    }

    @Override
    public boolean isUltraWarm(World world) {
        return world.getEnvironment() == World.Environment.NETHER;
    }

    @Override
    public Material getBucketContents(Material material) {
        return Material.AIR;
    }

    @Override
    public boolean hasBlockType(String key) {
        return false;
    }

    @Override
    public BlockData createBlockData(String key) {
        return null;
    }

    @Override
    public BlockData createBlockDataFromString(String blockData) {
        return null;
    }

    @Override
    public boolean isInvisible(Material material) {
        return BlockUtils.isAir(material);
    }

    // -------------------- Special block type checkers --------------------

    @Override
    public boolean isItemFrame(Material material) {
        return material == Material.ITEM_FRAME;
    }

    @Override
    public Material getFrameType(Entity entity) {
        return Material.ITEM_FRAME;
    }

    @Override
    public Material getFrameType(EntityType type) {
        return type == EntityType.ITEM_FRAME ? Material.ITEM_FRAME : null;
    }

    @Override
    public Class<? extends ItemFrame> getFrameClass(Material material) {
        return ItemFrame.class;
    }

    @Override
    public <T extends Entity> T spawn(World world, Location location, Class<T> entityClass, Consumer<? super T> function) {
        // Bukkit 1.20.1 and earlier only have World#spawn(Location, Class, org.bukkit.util.Consumer), which is deprecated for removal in the current API
        Class<?> consumerClass = LegacySpawn.CONSUMER_CLASS;
        if (LegacySpawn.METHOD == null) {
            throw new IllegalStateException("Legacy Bukkit spawn method is unavailable");
        }
        try {
            Object consumer = Proxy.newProxyInstance(consumerClass.getClassLoader(), new Class<?>[] { consumerClass }, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    switch (method.getName()) {
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        default:
                            return consumerClass.getName();
                    }
                }
                function.accept(entityClass.cast(args[0]));
                return null;
            });
            return entityClass.cast(LegacySpawn.METHOD.invoke(world, location, entityClass, consumer));
        }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class LegacySpawn {
        private static final Class<?> CONSUMER_CLASS = AdapterMethods.findType("org.bukkit.util.Consumer");
        private static final Method METHOD = CONSUMER_CLASS == null ? null : AdapterMethods.find(World.class, "spawn", Location.class, Class.class, CONSUMER_CLASS);
    }

    @Override
    public boolean isDecoratedPot(Material material) {
        return false;
    }

    @Override
    public boolean isSuspiciousBlock(Material material) {
        return false;
    }

    @Override
    public boolean isSign(Material material) {
        return Tag.SIGNS.isTagged(material);
    }

    @Override
    public boolean isChiseledBookshelf(Material material) {
        return false;
    }

    @Override
    public boolean isBookshelfBook(Material material) {
        return false;
    }

    @Override
    public ItemStack getChiseledBookshelfBook(BlockState blockState, PlayerInteractEvent event) {
        return null;
    }

    // -------------------- Sign handling methods --------------------

    @Override
    public void sendSignUpdate(Player player, Location location, Sign sign) {
        player.sendSignChange(location, sign.getLines(), sign.getColor());
    }

    @Override
    public String getLine(Sign sign, int line) {
        if (line >= 0 && line < 4) {
            return sign.getLine(line);
        }
        else {
            return "";
        }
    }

    @Override
    public void setLine(Sign sign, int line, String string) {
        if (string == null) {
            string = "";
        }

        if (line >= 0 && line < 4) {
            sign.setLine(line, string);
        }
    }

    @Override
    public boolean isSignFront(SignChangeEvent event) {
        return true;
    }

    @Override
    public Boolean getSignInteractionSide(Sign sign, Player player) {
        return true;
    }

    @Override
    public boolean isGlowing(Sign sign, boolean isFront) {
        return false;
    }

    @Override
    public boolean isWaxed(Sign sign) {
        return false;
    }

    @Override
    public boolean shouldLogExplosion(Event event){
        return true;
    }

    @Override
    public Material getExplodedBlock(BlockExplodeEvent event){
        // accoding to the Bukkit docs this will always return air
        return event.getBlock().getType();
    }

    @Override
    public void setGlowing(Sign sign, boolean isFront, boolean isGlowing) {
        // Base implementation does nothing
    }

    @Override
    public void setColor(Sign sign, boolean isFront, int color) {
        DyeColor dye = isFront ? getSignDyeColor(color) : null;
        if (dye != null) {
            sign.setColor(dye);
        }
    }

    protected static DyeColor getSignDyeColor(int color) {
        return (color & ~0xFFFFFF) == 0 ? DyeColor.getByColor(Color.fromRGB(color)) : null;
    }

    @Override
    public void setWaxed(Sign sign, boolean isWaxed) {
        // Base implementation does nothing
    }

    @Override
    public int getColor(Sign sign, boolean isFront) {
        DyeColor dye = isFront ? sign.getColor() : null;
        return dye == null ? 0 : dye.getColor().asRGB();
    }

    // -------------------- Registry methods --------------------

    @Override
    public Object getRegistryKey(Object value) {
        return value;
    }

    @Override
    public Object getRegistryValue(String key, Object tClass) {
        return null;
    }

    @Override
    public String getPaintingArtKey(Painting painting) {
        try {
            return painting.getArt().name();
        }
        catch (IncompatibleClassChangeError e) {
            return painting.getArt().toString();
        }
    }

    @Override
    public Art getPaintingArt(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        return Art.getByName(name.toUpperCase(Locale.ROOT));
    }

    @Override
    public boolean isCrafter(InventoryType type) {
        return false;
    }


    @Override
    public boolean isBundle(Material material) {
        return false;
    }

    @Override
    public boolean isCopperChest(Material material) {
        return false;
    }

    @Override
    public boolean isShelf(Material material){
        return false;
    }

    @Override
    public List<Location> getShelfInteractionLocations(Block block, BlockFace blockFace) {
        return Collections.emptyList();
    }

    @Override
    public Set<Material> copperChestMaterials() {
        return EMPTY_SET;
    }

    @Override
    public Set<Material> shelfMaterials() {
        return EMPTY_SET;
    }
}
