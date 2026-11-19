package net.coreprotect.listener.player;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Bed.Part;
import org.bukkit.block.data.type.Cake;
import org.bukkit.block.data.type.TurtleEgg;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.block.CampfireStartListener;
import net.coreprotect.listener.player.inspector.BlockInspector;
import net.coreprotect.listener.player.inspector.ContainerInspector;
import net.coreprotect.listener.player.inspector.InteractionInspector;
import net.coreprotect.listener.player.inspector.SignInspector;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.model.SignState;
import net.coreprotect.model.action.SignActions;
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.BlockUtils;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.WorldUtils;
import net.coreprotect.utility.ErrorReporter;

public final class PlayerInteractListener extends Queue implements Listener {

    private static final Set<Material> SIGN_DYES = EnumSet.of(Material.BLACK_DYE, Material.BLUE_DYE, Material.BROWN_DYE, Material.CYAN_DYE,
            Material.GRAY_DYE, Material.GREEN_DYE, Material.LIGHT_BLUE_DYE, Material.LIGHT_GRAY_DYE, Material.LIME_DYE,
            Material.MAGENTA_DYE, Material.ORANGE_DYE, Material.PINK_DYE, Material.PURPLE_DYE, Material.RED_DYE, Material.WHITE_DYE, Material.YELLOW_DYE);
    private static final Set<Material> ENTITY_LAUNCH_ITEMS = EnumSet.of(Material.ARMOR_STAND, Material.END_CRYSTAL, Material.BOW, Material.CROSSBOW, Material.TRIDENT,
            Material.EXPERIENCE_BOTTLE, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.ENDER_PEARL, Material.FIREWORK_ROCKET, Material.EGG, Material.SNOWBALL);

    static {
        for (String name : new String[] { "WIND_CHARGE", "BLUE_EGG", "BROWN_EGG" }) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                ENTITY_LAUNCH_ITEMS.add(material);
            }
        }
    }

    public static ConcurrentHashMap<String, Object[]> lastInspectorEvent = new ConcurrentHashMap<>();

    private final BlockInspector blockInspector = new BlockInspector();
    private final SignInspector signInspector = new SignInspector();
    private final ContainerInspector containerInspector = new ContainerInspector();
    private final InteractionInspector interactionInspector = new InteractionInspector();

    private static boolean shouldInspect(PlayerInteractEvent event) {
        EquipmentSlot eventHand = event.getHand();
        String uuid = event.getPlayer().getUniqueId().toString();
        long systemTime = System.currentTimeMillis();
        Object[] lastEvent = lastInspectorEvent.get(uuid);

        if (lastEvent != null) {
            long lastTime = (long) lastEvent[0];
            EquipmentSlot lastHand = (EquipmentSlot) lastEvent[1];

            if (systemTime - lastTime < 50 && eventHand != lastHand) {
                return false;
            }
        }

        lastInspectorEvent.put(uuid, new Object[] { systemTime, eventHand });
        return true;
    }

    private static boolean containsItem(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() != Material.AIR;
    }

    private static ItemStack getHandItem(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.HAND) {
            return player.getInventory().getItemInMainHand();
        }
        else if (hand == EquipmentSlot.OFF_HAND) {
            return player.getInventory().getItemInOffHand();
        }

        return null;
    }

    private static boolean isLecternBook(ItemStack itemStack) {
        if (!containsItem(itemStack)) {
            return false;
        }

        Material type = itemStack.getType();
        return type == Material.WRITABLE_BOOK || type == Material.WRITTEN_BOOK;
    }

    private static boolean containsLecternBook(ItemStack[] contents) {
        return contents != null && contents.length > 0 && isLecternBook(contents[0]);
    }

    private static void logLecternBookPlacement(Player player, Block block, ItemStack[] oldContents) {
        Location location = block.getLocation();
        Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
            BlockState blockState = location.getBlock().getState();
            if (blockState.getType() == Material.LECTERN && blockState instanceof InventoryHolder) {
                InventoryHolder inventoryHolder = (InventoryHolder) blockState;
                if (containsLecternBook(inventoryHolder.getInventory().getContents())) {
                    InventoryChangeListener.inventoryTransaction(player.getName(), location, oldContents);
                }
            }
        }, location, 1);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    protected void onPlayerInspect(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!Boolean.TRUE.equals(ConfigHandler.inspecting.get(player.getName()))) {
            return;
        }

        if (!player.hasPermission("coreprotect.inspect")) {
            Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
            ConfigHandler.inspecting.put(player.getName(), false);
            return;
        }

        if (event.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
            BlockState checkBlock = event.getClickedBlock().getState();
            int x = checkBlock.getX();
            int y = checkBlock.getY();
            int z = checkBlock.getZ();

            /* Check if clicking top half of double plant */
            BlockData checkBlockData = checkBlock.getBlockData();
            if (checkBlockData instanceof Bisected && !(checkBlockData instanceof Waterlogged)) {
                if (((Bisected) checkBlockData).getHalf().equals(Half.TOP) && y > BukkitAdapter.ADAPTER.getMinHeight(world)) {
                    checkBlock = world.getBlockAt(checkBlock.getX(), checkBlock.getY() - 1, checkBlock.getZ()).getState();
                }
            }
            /* Check if clicking top half of bed */
            if (checkBlockData instanceof Bed) {
                Bed bed = (Bed) checkBlockData;
                if (bed.getPart().equals(Part.HEAD)) {
                    checkBlock = event.getClickedBlock().getRelative(bed.getFacing().getOppositeFace()).getState();
                }
            }

            blockInspector.performBlockLookup(player, checkBlock);

            if (checkBlockData instanceof Bisected) {
                PlayerInteractUtils.handleBisectedBlockVisualization(player, event.getClickedBlock(), world);
            }
            else {
                Block block = event.getClickedBlock();
                int blockX = block.getX();
                int blockY = block.getY();
                int blockZ = block.getZ();

                Block x1 = world.getBlockAt(blockX + 1, blockY, blockZ);
                Block x2 = world.getBlockAt(blockX - 1, blockY, blockZ);
                Block z1 = world.getBlockAt(blockX, blockY, blockZ + 1);
                Block z2 = world.getBlockAt(blockX, blockY, blockZ - 1);
                player.sendBlockChange(x1.getLocation(), x1.getBlockData());
                player.sendBlockChange(x2.getLocation(), x2.getBlockData());
                player.sendBlockChange(z1.getLocation(), z1.getBlockData());
                player.sendBlockChange(z2.getLocation(), z2.getBlockData());
            }

            event.setCancelled(true);
        }
        else if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            Block block = event.getClickedBlock();
            if (block != null) {
                final Material type = block.getType();
                boolean isInteractBlock = BlockGroup.INTERACT_BLOCKS.contains(type);
                boolean isContainerBlock = BlockGroup.CONTAINERS.contains(type);
                boolean isSignBlock = BukkitAdapter.ADAPTER.isSign(type);

                if (isInteractBlock || isContainerBlock || isSignBlock) {
                    final Block clickedBlock = event.getClickedBlock();

                    if (isSignBlock) {
                        if (shouldInspect(event)) {
                            Location location = clickedBlock.getLocation();
                            signInspector.performSignLookup(player, location);
                        }
                        event.setCancelled(true);
                    }
                    else if (isContainerBlock && Config.getConfig(world).ITEM_TRANSACTIONS) {
                        Location location = null;
                        if (type.equals(Material.CHEST) || type.equals(Material.TRAPPED_CHEST) || BukkitAdapter.ADAPTER.isCopperChest(type)) {
                            Chest chest = (Chest) clickedBlock.getState();
                            InventoryHolder inventoryHolder = chest.getInventory().getHolder();

                            if (inventoryHolder instanceof DoubleChest) {
                                DoubleChest doubleChest = (DoubleChest) inventoryHolder;
                                location = doubleChest.getLocation();
                            }
                            else {
                                location = chest.getLocation();
                            }
                        }

                        if (location == null) {
                            location = clickedBlock.getLocation();
                        }

                        if (shouldInspect(event)) {
                            containerInspector.performContainerLookup(player, location);
                        }
                        event.setCancelled(true);
                    }
                    else if (isInteractBlock) {
                        // standard player interactions
                        Block interactBlock = clickedBlock;
                        if (BlockGroup.DOORS.contains(type)) {
                            int y = interactBlock.getY() - 1;
                            Block blockUnder = interactBlock.getWorld().getBlockAt(interactBlock.getX(), y, interactBlock.getZ());

                            if (blockUnder.getType().equals(type)) {
                                interactBlock = blockUnder;
                            }
                        }

                        if (shouldInspect(event)) {
                            interactionInspector.performInteractionLookup(player, interactBlock);
                        }

                        if (!BlockGroup.SAFE_INTERACT_BLOCKS.contains(type) || player.isSneaking()) {
                            event.setCancelled(true);
                        }
                    }
                }
                else {
                    if (shouldInspect(event)) {
                        final BlockState finalBlock = event.getClickedBlock().getRelative(event.getBlockFace()).getState();
                        blockInspector.performAirBlockLookup(player, finalBlock);

                        ItemUtils.updateInventory(event.getPlayer());

                        if (event.hasItem()) {
                            Material eventItem = event.getItem().getType();
                            if (eventItem.isBlock() && (eventItem.createBlockData() instanceof Bisected)) {
                                int x = finalBlock.getX();
                                int y = finalBlock.getY();
                                int z = finalBlock.getZ();
                                int worldMaxHeight = world.getMaxHeight();
                                if (y < (worldMaxHeight - 1)) {
                                    Block blockBisected = world.getBlockAt(x, y + 1, z);
                                    player.sendBlockChange(blockBisected.getLocation(), blockBisected.getBlockData());
                                }
                                int worldMinHeight = BukkitAdapter.ADAPTER.getMinHeight(world);
                                if (y > worldMinHeight) {
                                    Block blockBisected = world.getBlockAt(x, y - 1, z);
                                    player.sendBlockChange(blockBisected.getLocation(), blockBisected.getBlockData());
                                }
                            }
                        }
                    }

                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onPlayerInteract(PlayerInteractEvent event) {
        /* Logging for players punching out fire blocks. */
        if (event.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
            World world = event.getClickedBlock().getWorld();
            if (event.useInteractedBlock() != Event.Result.DENY) {
                Block block = event.getClickedBlock();
                if (block.getType() == Material.DRAGON_EGG) {
                    PlayerInteractUtils.clickedDragonEgg(event.getPlayer(), block);
                }

                if (Config.getConfig(world).BLOCK_BREAK) {
                    Block relativeBlock = event.getClickedBlock().getRelative(event.getBlockFace());

                    if (BlockGroup.FIRE.contains(relativeBlock.getType())) {
                        Player player = event.getPlayer();
                        Material type = relativeBlock.getType();
                        Queue.queueBlockBreak(player.getName(), relativeBlock.getState(), type, relativeBlock.getBlockData().getAsString(), 0);
                    }
                }
            }
        }
        else if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK) || event.getAction().equals(Action.RIGHT_CLICK_AIR)) {
            Player player = event.getPlayer();
            Block block = event.getClickedBlock();
            World world = player.getWorld();

            if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK) && block != null) {
                final Material type = block.getType();
                if (event.useInteractedBlock() != Event.Result.DENY) {
                    boolean isCake = false;

                    if (BukkitAdapter.ADAPTER.isSign(type)) {
                        ItemStack hand = getHandItem(player, event.getHand());
                        Material handType = containsItem(hand) ? hand.getType() : null;
                        Config config = Config.getConfig(world);
                        if (handType != null && config.SIGN_TEXT
                                && (SIGN_DYES.contains(handType) || handType.name().endsWith("INK_SAC") || handType == Material.HONEYCOMB)) {
                            BlockState blockState = block.getState();
                            Sign sign = (Sign) blockState;
                            SignState oldSign = SignState.capture(sign);
                            boolean changingColor = SIGN_DYES.contains(handType);
                            boolean changingGlow = handType.name().endsWith("INK_SAC");
                            Boolean interactionFront = PaperAdapter.ADAPTER.getSignInteractionSide(sign, player);

                            if (!oldSign.isWaxed()) {
                                Scheduler.runTask(CoreProtect.getInstance(), () -> {
                                    BlockState newState = block.getState();
                                    if (!(newState instanceof Sign)) {
                                        return;
                                    }

                                    Sign newSign = (Sign) newState;
                                    SignState after = oldSign.withDecoration(BukkitAdapter.ADAPTER.getColor(newSign, true), BukkitAdapter.ADAPTER.getColor(newSign, false),
                                            BukkitAdapter.ADAPTER.isGlowing(newSign, true), BukkitAdapter.ADAPTER.isGlowing(newSign, false), BukkitAdapter.ADAPTER.isWaxed(newSign));
                                    boolean frontChanged = changingColor ? oldSign.getColor(true) != after.getColor(true) : changingGlow && oldSign.isGlowing(true) != after.isGlowing(true);
                                    boolean backChanged = changingColor ? oldSign.getColor(false) != after.getColor(false) : changingGlow && oldSign.isGlowing(false) != after.isGlowing(false);
                                    if (interactionFront == null && (changingColor || changingGlow) && frontChanged == backChanged) {
                                        return;
                                    }
                                    boolean modifyingFront = interactionFront != null ? interactionFront : !backChanged;
                                    boolean propertyChanged = changingColor || changingGlow ? (modifyingFront ? frontChanged : backChanged) : oldSign.isWaxed() != after.isWaxed();
                                    if (propertyChanged) {
                                        SignState before = oldSign.withDecoration(
                                                changingColor && modifyingFront ? oldSign.getColor(true) : after.getColor(true),
                                                changingColor && !modifyingFront ? oldSign.getColor(false) : after.getColor(false),
                                                changingGlow && modifyingFront ? oldSign.isGlowing(true) : after.isGlowing(true),
                                                changingGlow && !modifyingFront ? oldSign.isGlowing(false) : after.isGlowing(false),
                                                changingColor || changingGlow ? after.isWaxed() : oldSign.isWaxed());
                                        Location location = blockState.getLocation();
                                        Queue.queueSignText(player.getName(), location, SignActions.BEFORE, before, modifyingFront, 1);
                                        if (config.BLOCK_PLACE) {
                                            Queue.queueBlockPlace(player.getName(), blockState, blockState.getType(), blockState, blockState.getType(), -1, 0, blockState.getBlockData().getAsString());
                                        }
                                        Queue.queueSignText(player.getName(), location, SignActions.EDIT, after, modifyingFront, 0);
                                    }
                                }, block.getLocation());
                            }
                        }
                    }
                    else if (BlockGroup.INTERACT_BLOCKS.contains(type)) {
                        if (event.getHand().equals(EquipmentSlot.HAND) && Config.getConfig(world).PLAYER_INTERACTIONS) {
                            Block interactBlock = event.getClickedBlock();
                            if (BlockGroup.DOORS.contains(type)) {
                                int y = interactBlock.getY() - 1;
                                Block blockUnder = interactBlock.getWorld().getBlockAt(interactBlock.getX(), y, interactBlock.getZ());

                                if (blockUnder.getType().equals(type)) {
                                    interactBlock = blockUnder;
                                }
                            }

                            Queue.queuePlayerInteraction(player.getName(), interactBlock.getLocation(), type, interactBlock.getBlockData().getAsString());
                        }
                    }
                    else if (BlockGroup.LIGHTABLES.contains(type)) {
                        BlockData blockData = block.getBlockData();
                        ItemStack hand = getHandItem(player, event.getHand());
                        boolean candle = BlockGroup.CANDLES.contains(type);
                        boolean usableHand = event.getHand() == EquipmentSlot.HAND || event.getHand() == EquipmentSlot.OFF_HAND;
                        boolean extinguishing = usableHand && (candle ? !containsItem(hand) : containsItem(hand) && hand.getType().name().endsWith("_SHOVEL"));
                        if (extinguishing && blockData instanceof Lightable && ((Lightable) blockData).isLit()) {
                            if (Config.getConfig(world).BLOCK_PLACE) {
                                BlockState before = block.getState();
                                String user = player.getName();
                                Scheduler.runTask(CoreProtect.getInstance(), () -> {
                                    BlockState after = block.getState();
                                    BlockData afterData = after.getBlockData();
                                    if (after.getType() == type && afterData instanceof Lightable && !((Lightable) afterData).isLit()) {
                                        Queue.queueBlockPlace(user, before, type, before, type, -1, 0, afterData.getAsString());
                                    }
                                }, before.getLocation());
                            }
                        }
                        else if (CampfireStartListener.useCampfireStartEvent && (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE)
                                && containsItem(hand) && hand.getType() != Material.BUCKET && player.getGameMode() != GameMode.CREATIVE) {
                            Location location = block.getLocation();
                            int wid = WorldUtils.getWorldId(world.getName());
                            String coordinates = location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ() + "." + wid + "." + type.name();
                            CacheHandler.interactCache.put(coordinates, new Object[] { System.currentTimeMillis(), hand.clone(), player.getName() });
                        }

                        isCake = type.name().endsWith("_CANDLE_CAKE");
                    }
                    else if (type == Material.JUKEBOX) {
                        BlockState blockState = block.getState();
                        if (blockState instanceof Jukebox) {
                            ItemStack record = ((Jukebox) blockState).getRecord();
                            boolean ejecting = containsItem(record);
                            ItemStack hand = getHandItem(player, event.getHand());
                            boolean inserting = !ejecting && event.useItemInHand() != Event.Result.DENY && containsItem(hand) && hand.getType().name().startsWith("MUSIC_DISC_");
                            if (ejecting || inserting) {
                                Config config = Config.getConfig(world);
                                String user = player.getName();
                                Location location = blockState.getLocation();
                                if (config.PLAYER_INTERACTIONS) {
                                    Queue.queuePlayerInteraction(user, location, type, blockState.getBlockData().getAsString());
                                }
                                if (config.ITEM_TRANSACTIONS) {
                                    ItemStack before = ejecting ? record.clone() : new ItemStack(Material.AIR);
                                    ItemStack after = inserting ? hand.clone() : new ItemStack(Material.AIR);
                                    if (inserting) {
                                        after.setAmount(1);
                                    }
                                    PlayerInteractEntityListener.queueContainerSpecifiedItems(user, Material.JUKEBOX,
                                            new Object[] { new ItemStack[] { before }, new ItemStack[] { after } }, location, ejecting);
                                }
                            }
                        }
                    }
                    else if (type == Material.LECTERN && Config.getConfig(world).ITEM_TRANSACTIONS && event.useItemInHand() != Event.Result.DENY) {
                        BlockState blockState = block.getState();
                        if (blockState instanceof InventoryHolder) {
                            InventoryHolder inventoryHolder = (InventoryHolder) blockState;
                            ItemStack[] oldContents = ItemUtils.getContainerState(inventoryHolder.getInventory().getContents());
                            ItemStack handItem = getHandItem(player, event.getHand());
                            boolean oldContainsBook = containsLecternBook(oldContents);
                            boolean handIsBook = isLecternBook(handItem);

                            if (!oldContainsBook && handIsBook) {
                                logLecternBookPlacement(player, block, oldContents);
                            }
                        }
                    }
                    else if (BukkitAdapter.ADAPTER.isChiseledBookshelf(type)) {
                        BlockState blockState = block.getState();
                        if (blockState instanceof BlockInventoryHolder) {
                            ItemStack book = BukkitAdapter.ADAPTER.getChiseledBookshelfBook(blockState, event);
                            if (book != null) {
                                ItemStack oldItemState = book.clone();
                                ItemStack newItemState = new ItemStack(Material.AIR);

                                if (book.getType() == Material.AIR) {
                                    ItemStack handItem = null;
                                    ItemStack mainHand = player.getInventory().getItemInMainHand();
                                    ItemStack offHand = player.getInventory().getItemInOffHand();

                                    if (event.getHand().equals(EquipmentSlot.HAND) && mainHand != null && BukkitAdapter.ADAPTER.isBookshelfBook(mainHand.getType())) {
                                        handItem = mainHand;
                                    }
                                    else if (event.getHand().equals(EquipmentSlot.OFF_HAND) && offHand != null && BukkitAdapter.ADAPTER.isBookshelfBook(offHand.getType())) {
                                        handItem = offHand;
                                    }
                                    else {
                                        return;
                                    }

                                    oldItemState = new ItemStack(Material.AIR);
                                    newItemState = handItem.clone();
                                }

                                if (!oldItemState.equals(newItemState)) {
                                    if (Config.getConfig(player.getWorld()).PLAYER_INTERACTIONS) {
                                        Queue.queuePlayerInteraction(player.getName(), blockState.getLocation(), type, blockState.getBlockData().getAsString());
                                    }

                                    InventoryChangeListener.inventoryTransaction(player.getName(), blockState.getLocation(), null);
                                }
                            }
                            else { // fallback if unable to determine bookshelf slot
                                InventoryChangeListener.inventoryTransaction(player.getName(), blockState.getLocation(), null);
                            }
                        }
                    } else if (BukkitAdapter.ADAPTER.isShelf(type)) {
                        for (Location location : BukkitAdapter.ADAPTER.getShelfInteractionLocations(block, event.getBlockFace())) {
                            InventoryChangeListener.inventoryTransaction(player.getName(), location, null);
                        }
                    }
                    else if (BukkitAdapter.ADAPTER.isDecoratedPot(type)) {
                        BlockState blockState = block.getState();
                        InventoryChangeListener.inventoryTransaction(player.getName(), blockState.getLocation(), null);
                    }
                    else if (type == Material.DRAGON_EGG) {
                        PlayerInteractUtils.clickedDragonEgg(player, block);
                    }

                    if (isCake || type == Material.CAKE) {
                        boolean placeCandle = false;
                        if (type == Material.CAKE && event.useItemInHand() != Event.Result.DENY) {
                            ItemStack hand = getHandItem(player, event.getHand());
                            if (containsItem(hand) && BlockGroup.CANDLES.contains(hand.getType())) {
                                BlockState before = block.getState();
                                Material expected = Material.getMaterial(hand.getType().name() + "_CAKE");
                                if (expected != null && before.getBlockData() instanceof Cake && ((Cake) before.getBlockData()).getBites() == 0) {
                                    placeCandle = true;
                                    if (Config.getConfig(world).BLOCK_PLACE) {
                                        String user = player.getName();
                                        Scheduler.runTask(CoreProtect.getInstance(), () -> {
                                            BlockState after = block.getState();
                                            if (after.getType() == expected) {
                                                Queue.queueBlockPlace(user, before, type, before, expected, -1, 0, after.getBlockData().getAsString());
                                            }
                                        }, before.getLocation());
                                    }
                                }
                            }
                        }

                        if (!placeCandle) {
                            Config config = Config.getConfig(world);
                            if (config.BLOCK_BREAK || config.BLOCK_PLACE) {
                                FoodLevelChangeListener.recordCakeUse(player, block.getState());
                            }
                        }
                    }
                }
            }

            if (event.useItemInHand() != Event.Result.DENY) {
                ItemStack handItem = getHandItem(player, event.getHand());
                if (!containsItem(handItem) || !ENTITY_LAUNCH_ITEMS.contains(handItem.getType())) {
                    return;
                }

                if (handItem.getType().equals(Material.END_CRYSTAL)) {
                    if (block != null && Config.getConfig(world).BLOCK_PLACE && (block.getType().equals(Material.OBSIDIAN) || block.getType().equals(Material.BEDROCK))) {
                        Location crystalLocation = block.getLocation().clone();
                        crystalLocation.setY(crystalLocation.getY() + 1);
                        boolean exists = false;

                        for (Entity entity : crystalLocation.getChunk().getEntities()) {
                            if (entity instanceof EnderCrystal && entity.getLocation().getBlockX() == crystalLocation.getBlockX() && entity.getLocation().getBlockY() == crystalLocation.getBlockY() && entity.getLocation().getBlockZ() == crystalLocation.getBlockZ()) {
                                exists = true;
                                break;
                            }
                        }

                        if (!exists) {
                            final Player playerFinal = player;
                            final Location locationFinal = crystalLocation;
                            Scheduler.runTask(CoreProtect.getInstance(), () -> {
                                try {
                                    boolean blockExists = false;
                                    int showingBottom = 0;

                                    for (Entity entity : locationFinal.getChunk().getEntities()) {
                                        if (entity instanceof EnderCrystal && entity.getLocation().getBlockX() == locationFinal.getBlockX() && entity.getLocation().getBlockY() == locationFinal.getBlockY() && entity.getLocation().getBlockZ() == locationFinal.getBlockZ()) {
                                            EnderCrystal enderCrystal = (EnderCrystal) entity;
                                            showingBottom = enderCrystal.isShowingBottom() ? 1 : 0;
                                            blockExists = true;
                                            break;
                                        }
                                    }
                                    if (blockExists) {
                                        Queue.queueBlockPlace(playerFinal.getName(), locationFinal.getBlock().getState(), locationFinal.getBlock().getType(), locationFinal.getBlock().getState(), Material.END_CRYSTAL, showingBottom, 1, null);
                                    }
                                }
                                catch (Exception e) {
                                    ErrorReporter.report(e);
                                }
                            }, locationFinal);
                        }
                    }
                }
                else {
                    Location relativeBlockLocation = player.getLocation().clone();
                    relativeBlockLocation.setY(relativeBlockLocation.getY() + 1);
                    Location blockLocation = relativeBlockLocation.clone();
                    blockLocation.setY(blockLocation.getY() + 1);

                    if (handItem.getType() == Material.ARMOR_STAND && block == null) {
                        return;
                    }
                    if (block != null && (handItem.getType() == Material.ARMOR_STAND || handItem.getType() == Material.FIREWORK_ROCKET)) {

                        Block relativeBlock = block.getRelative(event.getBlockFace());
                        relativeBlockLocation = relativeBlock.getLocation();
                        blockLocation = block.getLocation();
                    }

                    String relativeBlockKey = world.getName() + "-" + relativeBlockLocation.getBlockX() + "-" + relativeBlockLocation.getBlockY() + "-" + relativeBlockLocation.getBlockZ();
                    String blockKey = world.getName() + "-" + blockLocation.getBlockX() + "-" + blockLocation.getBlockY() + "-" + blockLocation.getBlockZ();
                    Object[] keys = new Object[] { System.currentTimeMillis(), relativeBlockKey, blockKey, handItem.clone(), world.getUID() };
                    ConfigHandler.entityBlockMapper.put(player.getName(), keys);
                }
            }
        }
        else if (event.getAction() == Action.PHYSICAL && event.useInteractedBlock() != Event.Result.DENY) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.TURTLE_EGG) {
                Config config = Config.getConfig(block.getWorld());
                if (!config.BLOCK_BREAK && !config.BLOCK_PLACE) {
                    return;
                }
                BlockState before = block.getState();
                if (!(before.getBlockData() instanceof TurtleEgg)) {
                    return;
                }
                int eggs = ((TurtleEgg) before.getBlockData()).getEggs();
                String user = event.getPlayer().getName();
                Scheduler.runTask(CoreProtect.getInstance(), () -> {
                    BlockState after = block.getState();
                    BlockData afterData = after.getBlockData();
                    boolean removedLast = eggs == 1 && BlockUtils.isAir(after.getType());
                    boolean lostOne = afterData instanceof TurtleEgg && ((TurtleEgg) afterData).getEggs() == eggs - 1;
                    if (removedLast || lostOne) {
                        if (config.BLOCK_BREAK) {
                            Queue.queueBlockBreak(user, before, Material.TURTLE_EGG, before.getBlockData().getAsString(), 0);
                        }
                        if (config.BLOCK_PLACE && lostOne) {
                            Queue.queueBlockPlace(user, after, after.getType(), null, null, -1, 0, afterData.getAsString());
                        }
                    }
                }, before.getLocation());
            }
        }
    }
}
