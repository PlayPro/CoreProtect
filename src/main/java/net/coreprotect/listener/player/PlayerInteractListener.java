package net.coreprotect.listener.player;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
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
import net.coreprotect.paper.PaperAdapter;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.thread.Scheduler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.ItemUtils;
import net.coreprotect.utility.WorldUtils;

public final class PlayerInteractListener extends Queue implements Listener {

    public static ConcurrentHashMap<String, Object[]> lastInspectorEvent = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Object[]> suspiciousBlockEvent = new ConcurrentHashMap<>();

    private final BlockInspector blockInspector = new BlockInspector();
    private final SignInspector signInspector = new SignInspector();
    private final ContainerInspector containerInspector = new ContainerInspector();
    private final InteractionInspector interactionInspector = new InteractionInspector();

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
                        Location location = clickedBlock.getLocation();
                        signInspector.performSignLookup(player, location);
                        event.setCancelled(true);
                    }
                    else if (isContainerBlock && Config.getConfig(world).ITEM_TRANSACTIONS) {
                        Location location = null;
                        if (type.equals(Material.CHEST) || type.equals(Material.TRAPPED_CHEST)) {
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

                        containerInspector.performContainerLookup(player, location);
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

                        interactionInspector.performInteractionLookup(player, interactBlock);

                        if (!BlockGroup.SAFE_INTERACT_BLOCKS.contains(type) || player.isSneaking()) {
                            event.setCancelled(true);
                        }
                    }
                }
                else {
                    boolean performLookup = true;
                    EquipmentSlot eventHand = event.getHand();
                    String uuid = event.getPlayer().getUniqueId().toString();
                    long systemTime = System.currentTimeMillis();

                    if (lastInspectorEvent.get(uuid) != null) {
                        Object[] lastEvent = lastInspectorEvent.get(uuid);
                        long lastTime = (long) lastEvent[0];
                        EquipmentSlot lastHand = (EquipmentSlot) lastEvent[1];

                        long timeSince = systemTime - lastTime;
                        if (timeSince < 50 && !eventHand.equals(lastHand)) {
                            performLookup = false;
                        }
                    }

                    if (performLookup) {
                        final BlockState finalBlock = event.getClickedBlock().getRelative(event.getBlockFace()).getState();
                        blockInspector.performAirBlockLookup(player, finalBlock);

                        ItemUtils.updateInventory(event.getPlayer());
                        lastInspectorEvent.put(uuid, new Object[] { systemTime, eventHand });

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
                        // check if right clicked sign with dye
                        Set<Material> dyeSet = EnumSet.of(Material.BLACK_DYE, Material.BLUE_DYE, Material.BROWN_DYE, Material.CYAN_DYE, Material.GRAY_DYE, Material.GREEN_DYE, Material.LIGHT_BLUE_DYE, Material.LIGHT_GRAY_DYE, Material.LIME_DYE, Material.MAGENTA_DYE, Material.ORANGE_DYE, Material.PINK_DYE, Material.PURPLE_DYE, Material.RED_DYE, Material.WHITE_DYE, Material.YELLOW_DYE);
                        Material handType = null;

                        ItemStack mainHand = player.getInventory().getItemInMainHand();
                        if (event.getHand().equals(EquipmentSlot.HAND) && mainHand != null) {
                            handType = mainHand.getType();
                        }

                        if (handType != null && (dyeSet.contains(handType) || handType.name().endsWith("INK_SAC") || handType == Material.HONEYCOMB) && Config.getConfig(block.getWorld()).SIGN_TEXT) {
                            BlockState blockState = block.getState();
                            Sign sign = (Sign) blockState;
                            String line1 = PaperAdapter.ADAPTER.getLine(sign, 0);
                            String line2 = PaperAdapter.ADAPTER.getLine(sign, 1);
                            String line3 = PaperAdapter.ADAPTER.getLine(sign, 2);
                            String line4 = PaperAdapter.ADAPTER.getLine(sign, 3);
                            String line5 = PaperAdapter.ADAPTER.getLine(sign, 4);
                            String line6 = PaperAdapter.ADAPTER.getLine(sign, 5);
                            String line7 = PaperAdapter.ADAPTER.getLine(sign, 6);
                            String line8 = PaperAdapter.ADAPTER.getLine(sign, 7);

                            boolean isFront = true;
                            int oldColor = BukkitAdapter.ADAPTER.getColor(sign, isFront);
                            int oldColorSecondary = BukkitAdapter.ADAPTER.getColor(sign, !isFront);
                            boolean oldFrontGlowing = BukkitAdapter.ADAPTER.isGlowing(sign, isFront);
                            boolean oldBackGlowing = BukkitAdapter.ADAPTER.isGlowing(sign, !isFront);
                            boolean oldIsWaxed = BukkitAdapter.ADAPTER.isWaxed(sign);

                            if (!oldIsWaxed) {
                                Scheduler.runTask(CoreProtect.getInstance(), () -> {
                                    BlockState newState = block.getState();
                                    if (newState instanceof Sign) {
                                        Sign newSign = (Sign) newState;
                                        int newColor = BukkitAdapter.ADAPTER.getColor(newSign, isFront);
                                        int newColorSecondary = BukkitAdapter.ADAPTER.getColor(newSign, !isFront);
                                        boolean newFrontGlowing = BukkitAdapter.ADAPTER.isGlowing(newSign, isFront);
                                        boolean newBackGlowing = BukkitAdapter.ADAPTER.isGlowing(newSign, !isFront);
                                        boolean newIsWaxed = BukkitAdapter.ADAPTER.isWaxed(newSign);

                                        boolean modifyingFront = oldBackGlowing == newBackGlowing && oldColorSecondary == newColorSecondary;
                                        if (oldColor != newColor || oldColorSecondary != newColorSecondary || oldFrontGlowing != newFrontGlowing || oldBackGlowing != newBackGlowing || oldIsWaxed != newIsWaxed) {
                                            Location location = blockState.getLocation();
                                            Queue.queueSignText(player.getName(), location, 0, oldColor, oldColorSecondary, oldFrontGlowing, oldBackGlowing, oldIsWaxed, modifyingFront, line1, line2, line3, line4, line5, line6, line7, line8, 1); // 1 second timeOffset
                                            Queue.queueBlockPlace(player.getName(), blockState, block.getType(), blockState, block.getType(), -1, 0, blockState.getBlockData().getAsString());
                                            Queue.queueSignText(player.getName(), location, 2, newColor, newColorSecondary, newFrontGlowing, newBackGlowing, newIsWaxed, modifyingFront, line1, line2, line3, line4, line5, line6, line7, line8, 0);
                                        }

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

                            Queue.queuePlayerInteraction(player.getName(), interactBlock.getState(), type);
                        }
                    }
                    else if (BlockGroup.LIGHTABLES.contains(type)) { // extinguishing a lit block such as a campfire
                        BlockData blockData = block.getBlockData();
                        if (blockData instanceof Lightable && ((Lightable) blockData).isLit() && ((BlockGroup.CANDLES.contains(type) && event.getMaterial() == Material.AIR) || (!BlockGroup.CANDLES.contains(type) && event.getMaterial().name().endsWith("_SHOVEL")))) {
                            ((Lightable) blockData).setLit(false);
                            Queue.queueBlockPlace(player.getName(), block.getState(), type, block.getState(), type, -1, 0, blockData.getAsString());

                            /*
                            BlockState blockState = block.getState();
                            Bukkit.getServer().getScheduler().runTask(CoreProtect.getInstance(), () -> {
                                try {
                                    BlockData validateBlockData = block.getBlockData();
                                    if (validateBlockData instanceof Lightable && !((Lightable) validateBlockData).isLit()) {
                                        Queue.queueBlockPlace(player.getName(), blockState, type, blockState, type, -1, 0, validateBlockData.getAsString());
                                    }
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                            */
                        }
                        else if (CampfireStartListener.useCampfireStartEvent && (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE)) {
                            ItemStack handItem = null;
                            ItemStack mainHand = player.getInventory().getItemInMainHand();
                            ItemStack offHand = player.getInventory().getItemInOffHand();

                            if (event.getHand().equals(EquipmentSlot.HAND) && mainHand != null && mainHand.getType() != Material.BUCKET) {
                                handItem = mainHand;
                            }
                            else if (event.getHand().equals(EquipmentSlot.OFF_HAND) && offHand != null) {
                                handItem = offHand;
                            }
                            else {
                                return;
                            }

                            if (player.getGameMode() != GameMode.CREATIVE) {
                                Location location = block.getLocation();
                                long time = System.currentTimeMillis();
                                int wid = WorldUtils.getWorldId(location.getWorld().getName());
                                int x = location.getBlockX();
                                int y = location.getBlockY();
                                int z = location.getBlockZ();
                                String coordinates = x + "." + y + "." + z + "." + wid + "." + type.name();
                                CacheHandler.interactCache.put(coordinates, new Object[] { time, handItem, player.getName() });
                            }
                        }

                        isCake = type.name().endsWith(Material.CAKE.name());
                    }
                    else if (type == Material.JUKEBOX) {
                        BlockState blockState = block.getState();
                        if (blockState instanceof Jukebox) {
                            Jukebox jukebox = (Jukebox) blockState;
                            ItemStack jukeboxRecord = jukebox.isPlaying() ? jukebox.getRecord() : new ItemStack(Material.AIR);
                            ItemStack oldItemState = jukeboxRecord.clone();
                            ItemStack newItemState = new ItemStack(Material.AIR);

                            if (jukeboxRecord.getType() == Material.AIR) {
                                ItemStack handItem = null;
                                ItemStack mainHand = player.getInventory().getItemInMainHand();
                                ItemStack offHand = player.getInventory().getItemInOffHand();

                                if (event.getHand().equals(EquipmentSlot.HAND) && mainHand != null && mainHand.getType().name().startsWith("MUSIC_DISC")) {
                                    handItem = mainHand;
                                }
                                else if (event.getHand().equals(EquipmentSlot.OFF_HAND) && offHand != null && offHand.getType().name().startsWith("MUSIC_DISC")) {
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
                                    Queue.queuePlayerInteraction(player.getName(), blockState, type);
                                }

                                if (Config.getConfig(block.getWorld()).ITEM_TRANSACTIONS) {
                                    boolean logDrops = player.getGameMode() != GameMode.CREATIVE;
                                    ItemStack[] oldState = new ItemStack[] { oldItemState };
                                    ItemStack[] newState = new ItemStack[] { newItemState };
                                    PlayerInteractEntityListener.queueContainerSpecifiedItems(player.getName(), Material.JUKEBOX, new Object[] { oldState, newState }, jukebox.getLocation(), logDrops);
                                }
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
                                        Queue.queuePlayerInteraction(player.getName(), blockState, type);
                                    }

                                    InventoryChangeListener.inventoryTransaction(player.getName(), blockState.getLocation(), null);
                                }
                            }
                            else { // fallback if unable to determine bookshelf slot
                                InventoryChangeListener.inventoryTransaction(player.getName(), blockState.getLocation(), null);
                            }
                        }
                    }
                    else if (BukkitAdapter.ADAPTER.isDecoratedPot(type)) {
                        BlockState blockState = block.getState();
                        InventoryChangeListener.inventoryTransaction(player.getName(), blockState.getLocation(), null);
                    }
                    else if (BukkitAdapter.ADAPTER.isSuspiciousBlock(type)) {
                        ItemStack handItem = null;
                        ItemStack mainHand = player.getInventory().getItemInMainHand();
                        ItemStack offHand = player.getInventory().getItemInOffHand();
                        if (event.getHand().equals(EquipmentSlot.HAND) && mainHand != null) {
                            handItem = mainHand;
                        }
                        else if (event.getHand().equals(EquipmentSlot.OFF_HAND) && offHand != null) {
                            handItem = offHand;
                        }

                        if (handItem.getType() == Material.BRUSH) {
                            BlockState blockState = block.getState();
                            Location blockLocation = block.getLocation();
                            Scheduler.scheduleSyncDelayedTask(CoreProtect.getInstance(), () -> {
                                Material newType = block.getType();
                                if (type == newType || (newType != Material.SAND && newType != Material.GRAVEL)) {
                                    return;
                                }

                                long systemTime = System.currentTimeMillis();
                                boolean logChange = true;
                                if (suspiciousBlockEvent.get(player.getName()) != null) {
                                    Object[] lastEvent = suspiciousBlockEvent.get(player.getName());
                                    long lastTime = (long) lastEvent[0];
                                    long timeSince = systemTime - lastTime;
                                    Location lastLocation = (Location) lastEvent[1];
                                    if (timeSince < 5000 && blockLocation.equals(lastLocation)) {
                                        logChange = false;
                                    }
                                }

                                if (logChange) {
                                    Queue.queueBlockPlace(player.getName(), blockState, newType, blockState, newType, -1, 0, null);
                                    suspiciousBlockEvent.put(player.getName(), new Object[] { systemTime, blockLocation });
                                }
                            }, blockLocation, 100);
                        }
                    }
                    else if (type == Material.DRAGON_EGG) {
                        PlayerInteractUtils.clickedDragonEgg(player, block);
                    }

                    if (isCake || type == Material.CAKE) {
                        boolean placeCandle = false;
                        if (type == Material.CAKE) { // check if placing a candle on a cake
                            Material handType = null;
                            ItemStack mainHand = player.getInventory().getItemInMainHand();
                            if (event.getHand().equals(EquipmentSlot.HAND) && mainHand != null) {
                                handType = mainHand.getType();
                            }

                            if (handType != null && BlockGroup.CANDLES.contains(handType) && Config.getConfig(block.getWorld()).BLOCK_PLACE) {
                                BlockState blockState = block.getState();
                                BlockData blockData = blockState.getBlockData();
                                Material newMaterial = Material.getMaterial(handType.name() + "_" + Material.CAKE.name());
                                if (newMaterial != null && ((Cake) blockData).getBites() == 0) {
                                    Queue.queueBlockPlace(player.getName(), block.getState(), block.getType(), blockState, newMaterial, -1, 0, blockData.getAsString());
                                    placeCandle = true;
                                }
                            }
                        }

                        if (!placeCandle) {
                            String userUUID = player.getUniqueId().toString();
                            Location location = player.getLocation();
                            long time = System.currentTimeMillis();
                            int wid = WorldUtils.getWorldId(location.getWorld().getName());
                            int x = location.getBlockX();
                            int y = location.getBlockY();
                            int z = location.getBlockZ();
                            String coordinates = x + "." + y + "." + z + "." + wid + "." + userUUID;
                            CacheHandler.interactCache.put(coordinates, new Object[] { time, Material.CAKE, block.getState() });
                        }
                    }
                }
            }

            if (event.useItemInHand() != Event.Result.DENY) {
                List<Material> entityBlockTypes = Arrays.asList(Material.ARMOR_STAND, Material.END_CRYSTAL, Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.EXPERIENCE_BOTTLE, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.ENDER_PEARL, Material.FIREWORK_ROCKET, Material.EGG, Material.SNOWBALL);
                try {
                    entityBlockTypes.add(Material.valueOf("WIND_CHARGE"));
                }
                catch (Exception e) {
                    // not running MC 1.21+
                }
                ItemStack handItem = null;
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                ItemStack offHand = player.getInventory().getItemInOffHand();

                if (event.getHand().equals(EquipmentSlot.HAND) && mainHand != null && entityBlockTypes.contains(mainHand.getType())) {
                    handItem = mainHand;
                }
                else if (event.getHand().equals(EquipmentSlot.OFF_HAND) && offHand != null && entityBlockTypes.contains(offHand.getType())) {
                    handItem = offHand;
                }
                else {
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
                                    e.printStackTrace();
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

                    if (handItem.getType() == Material.ARMOR_STAND || handItem.getType() == Material.FIREWORK_ROCKET) {
                        if (block == null) {
                            return;
                        }

                        Block relativeBlock = block.getRelative(event.getBlockFace());
                        relativeBlockLocation = relativeBlock.getLocation();
                        blockLocation = block.getLocation();
                    }

                    String relativeBlockKey = world.getName() + "-" + relativeBlockLocation.getBlockX() + "-" + relativeBlockLocation.getBlockY() + "-" + relativeBlockLocation.getBlockZ();
                    String blockKey = world.getName() + "-" + blockLocation.getBlockX() + "-" + blockLocation.getBlockY() + "-" + blockLocation.getBlockZ();
                    Object[] keys = new Object[] { System.currentTimeMillis(), relativeBlockKey, blockKey, handItem };
                    ConfigHandler.entityBlockMapper.put(player.getName(), keys);
                }
            }
        }
        else if (event.getAction().equals(Action.PHYSICAL)) {
            Block block = event.getClickedBlock();
            if (block == null || (!block.getType().equals(Material.FARMLAND) && !block.getType().equals(Material.TURTLE_EGG))) {
                return;
            }

            World world = block.getWorld();
            if (event.useInteractedBlock() != Event.Result.DENY && Config.getConfig(world).BLOCK_BREAK) {
                Player player = event.getPlayer();
                if (block.getType().equals(Material.FARMLAND)) {
                    Block blockAbove = world.getBlockAt(block.getX(), block.getY() + 1, block.getZ());
                    Material type = blockAbove.getType();

                    if (!type.equals(Material.AIR) && !type.equals(Material.CAVE_AIR)) {
                        /* Trampled crops, such as wheat */
                        Queue.queueBlockBreak(player.getName(), blockAbove.getState(), type, blockAbove.getBlockData().getAsString(), 0);
                    }
                }

                Queue.queueBlockBreak(player.getName(), block.getState(), block.getType(), block.getBlockData().getAsString(), 0);
                Queue.queueBlockPlaceDelayed(player.getName(), block.getLocation(), block.getType(), null, null, 0);
            }
        }
    }
}
