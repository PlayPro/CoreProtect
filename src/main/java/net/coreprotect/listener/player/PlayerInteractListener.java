package net.coreprotect.listener.player;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Waterlogged;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.CoreProtect;
import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.database.lookup.BlockLookup;
import net.coreprotect.database.lookup.ChestTransactionLookup;
import net.coreprotect.database.lookup.InteractionLookup;
import net.coreprotect.database.lookup.SignMessageLookup;
import net.coreprotect.language.Phrase;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.Util;

public final class PlayerInteractListener extends Queue implements Listener {

    public static ConcurrentHashMap<String, Object[]> lastInspectorEvent = new ConcurrentHashMap<>();

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

            final BlockState blockFinal = checkBlock;
            class BasicThread implements Runnable {
                @Override
                public void run() {
                    if (ConfigHandler.converterRunning) {
                        player.sendMessage(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
                        return;
                    }
                    if (ConfigHandler.purgeRunning) {
                        player.sendMessage(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
                        return;
                    }
                    if (ConfigHandler.lookupThrottle.get(player.getName()) != null) {
                        Object[] lookupThrottle = ConfigHandler.lookupThrottle.get(player.getName());
                        if ((boolean) lookupThrottle[0] || (System.currentTimeMillis() - (long) lookupThrottle[1]) < 100) {
                            player.sendMessage(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                            return;
                        }
                    }

                    try (Connection connection = Database.getConnection(true)) {
                        if (connection != null) {
                            ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { true, System.currentTimeMillis() });
                            Statement statement = connection.createStatement();

                            String resultData = BlockLookup.performLookup(null, statement, blockFinal, player, 0, 1, 7);
                            if (resultData.contains("\n")) {
                                for (String b : resultData.split("\n")) {
                                    Chat.sendComponent(player, b);
                                }
                            }
                            else if (resultData.length() > 0) {
                                Chat.sendComponent(player, resultData);
                            }

                            statement.close();
                            ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });

                            if (blockFinal instanceof Sign && player.getGameMode() != GameMode.CREATIVE) {
                                Thread.sleep(1500);
                                Sign sign = (Sign) blockFinal;
                                BukkitAdapter.ADAPTER.sendSignChange(player, sign);
                            }
                        }
                        else {
                            player.sendMessage(Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            Runnable runnable = new BasicThread();
            Thread thread = new Thread(runnable);
            thread.start();

            if (checkBlockData instanceof Bisected) {
                int worldMaxHeight = world.getMaxHeight();
                if (y < (worldMaxHeight - 1)) {
                    Block y1 = world.getBlockAt(x, y + 1, z);
                    player.sendBlockChange(y1.getLocation(), y1.getBlockData());
                }

                int worldMinHeight = BukkitAdapter.ADAPTER.getMinHeight(world);
                if (y > worldMinHeight) {
                    Block y2 = world.getBlockAt(x, y - 1, z);
                    player.sendBlockChange(y2.getLocation(), y2.getBlockData());
                }
            }

            Block x1 = world.getBlockAt(x + 1, y, z);
            Block x2 = world.getBlockAt(x - 1, y, z);
            Block z1 = world.getBlockAt(x, y, z + 1);
            Block z2 = world.getBlockAt(x, y, z - 1);
            player.sendBlockChange(x1.getLocation(), x1.getBlockData());
            player.sendBlockChange(x2.getLocation(), x2.getBlockData());
            player.sendBlockChange(z1.getLocation(), z1.getBlockData());
            player.sendBlockChange(z2.getLocation(), z2.getBlockData());
            event.setCancelled(true);
        }
        else if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            Block block = event.getClickedBlock();
            if (block != null) {
                final Material type = block.getType();
                boolean isInteractBlock = BlockGroup.INTERACT_BLOCKS.contains(type);
                boolean isContainerBlock = BlockGroup.CONTAINERS.contains(type);
                boolean isSignBlock = Tag.SIGNS.isTagged(type);

                if (isInteractBlock || isContainerBlock || isSignBlock) {
                    final Block clickedBlock = event.getClickedBlock();

                    if (isSignBlock) {
                        Location location = clickedBlock.getLocation();

                        // sign messages
                        class BasicThread implements Runnable {
                            @Override
                            public void run() {
                                if (ConfigHandler.converterRunning) {
                                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
                                    return;
                                }

                                if (ConfigHandler.purgeRunning) {
                                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
                                    return;
                                }

                                if (ConfigHandler.lookupThrottle.get(player.getName()) != null) {
                                    Object[] lookupThrottle = ConfigHandler.lookupThrottle.get(player.getName());
                                    if ((boolean) lookupThrottle[0] || (System.currentTimeMillis() - (long) lookupThrottle[1]) < 100) {
                                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                                        return;
                                    }
                                }

                                ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { true, System.currentTimeMillis() });

                                try (Connection connection = Database.getConnection(true)) {
                                    if (connection != null) {
                                        Statement statement = connection.createStatement();
                                        List<String> signData = SignMessageLookup.performLookup(null, statement, location, player, 1, 7);
                                        for (String signMessage : signData) {
                                            String bypass = null;

                                            if (signMessage.contains("\n")) {
                                                String[] split = signMessage.split("\n");
                                                signMessage = split[0];
                                                bypass = split[1];
                                            }

                                            if (signMessage.length() > 0) {
                                                Chat.sendComponent(player, signMessage, bypass);
                                            }
                                        }

                                        statement.close();
                                    }
                                    else {
                                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                                    }
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                }

                                ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
                            }
                        }

                        Runnable runnable = new BasicThread();
                        Thread thread = new Thread(runnable);
                        thread.start();
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

                        Location finalLocation = location;

                        // logged chest items
                        class BasicThread implements Runnable {
                            @Override
                            public void run() {
                                if (ConfigHandler.converterRunning) {
                                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
                                    return;
                                }

                                if (ConfigHandler.purgeRunning) {
                                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
                                    return;
                                }

                                if (ConfigHandler.lookupThrottle.get(player.getName()) != null) {
                                    Object[] lookupThrottle = ConfigHandler.lookupThrottle.get(player.getName());
                                    if ((boolean) lookupThrottle[0] || (System.currentTimeMillis() - (long) lookupThrottle[1]) < 100) {
                                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                                        return;
                                    }
                                }

                                ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { true, System.currentTimeMillis() });

                                try (Connection connection = Database.getConnection(true)) {
                                    if (connection != null) {
                                        Statement statement = connection.createStatement();
                                        String blockData = ChestTransactionLookup.performLookup(null, statement, finalLocation, player, 1, 7, false);

                                        if (blockData.contains("\n")) {
                                            for (String splitData : blockData.split("\n")) {
                                                Chat.sendComponent(player, splitData);
                                            }
                                        }
                                        else {
                                            Chat.sendComponent(player, blockData);
                                        }

                                        statement.close();
                                    }
                                    else {
                                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                                    }
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                }

                                ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
                            }
                        }

                        Runnable runnable = new BasicThread();
                        Thread thread = new Thread(runnable);
                        thread.start();
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

                        final Block finalInteractBlock = interactBlock;
                        class BasicThread implements Runnable {
                            @Override
                            public void run() {
                                if (ConfigHandler.converterRunning) {
                                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
                                    return;
                                }
                                if (ConfigHandler.purgeRunning) {
                                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
                                    return;
                                }
                                if (ConfigHandler.lookupThrottle.get(player.getName()) != null) {
                                    Object[] lookupThrottle = ConfigHandler.lookupThrottle.get(player.getName());
                                    if ((boolean) lookupThrottle[0] || (System.currentTimeMillis() - (long) lookupThrottle[1]) < 100) {
                                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                                        return;
                                    }
                                }

                                ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { true, System.currentTimeMillis() });

                                try (Connection connection = Database.getConnection(true)) {
                                    if (connection != null) {
                                        Statement statement = connection.createStatement();
                                        String blockData = InteractionLookup.performLookup(null, statement, finalInteractBlock, player, 0, 1, 7);

                                        if (blockData.contains("\n")) {
                                            for (String splitData : blockData.split("\n")) {
                                                Chat.sendComponent(player, splitData);
                                            }
                                        }
                                        else {
                                            Chat.sendComponent(player, blockData);
                                        }

                                        statement.close();
                                    }
                                    else {
                                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                                    }
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                }

                                ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
                            }
                        }

                        Runnable runnable = new BasicThread();
                        Thread thread = new Thread(runnable);
                        thread.start();

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
                        final Player finalPlayer = player;
                        final BlockState finalBlock = event.getClickedBlock().getRelative(event.getBlockFace()).getState();

                        class BasicThread implements Runnable {
                            @Override
                            public void run() {
                                if (ConfigHandler.converterRunning) {
                                    Chat.sendMessage(finalPlayer, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
                                    return;
                                }

                                if (ConfigHandler.purgeRunning) {
                                    Chat.sendMessage(finalPlayer, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
                                    return;
                                }

                                if (ConfigHandler.lookupThrottle.get(finalPlayer.getName()) != null) {
                                    Object[] lookupThrottle = ConfigHandler.lookupThrottle.get(finalPlayer.getName());
                                    if ((boolean) lookupThrottle[0] || (System.currentTimeMillis() - (long) lookupThrottle[1]) < 100) {
                                        Chat.sendMessage(finalPlayer, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                                        return;
                                    }
                                }

                                ConfigHandler.lookupThrottle.put(finalPlayer.getName(), new Object[] { true, System.currentTimeMillis() });

                                try (Connection connection = Database.getConnection(true)) {
                                    if (connection != null) {
                                        Statement statement = connection.createStatement();
                                        if (finalBlock.getType().equals(Material.AIR) || finalBlock.getType().equals(Material.CAVE_AIR)) {
                                            String blockData = BlockLookup.performLookup(null, statement, finalBlock, finalPlayer, 0, 1, 7);

                                            if (blockData.contains("\n")) {
                                                for (String b : blockData.split("\n")) {
                                                    Chat.sendComponent(finalPlayer, b);
                                                }
                                            }
                                            else if (blockData.length() > 0) {
                                                Chat.sendComponent(finalPlayer, blockData);
                                            }
                                        }
                                        else {
                                            String blockData = BlockLookup.performLookup(null, statement, finalBlock, finalPlayer, 0, 1, 7);
                                            if (blockData.contains("\n")) {
                                                for (String splitData : blockData.split("\n")) {
                                                    Chat.sendComponent(finalPlayer, splitData);
                                                }
                                            }
                                            else if (blockData.length() > 0) {
                                                Chat.sendComponent(finalPlayer, blockData);
                                            }
                                        }

                                        statement.close();
                                    }
                                    else {
                                        Chat.sendMessage(finalPlayer, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                                    }
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                }

                                ConfigHandler.lookupThrottle.put(finalPlayer.getName(), new Object[] { false, System.currentTimeMillis() });
                            }
                        }

                        Runnable runnable = new BasicThread();
                        Thread thread = new Thread(runnable);
                        thread.start();

                        Util.updateInventory(event.getPlayer());
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
            if (event.useInteractedBlock() != Event.Result.DENY && Config.getConfig(world).BLOCK_BREAK) {
                Block relativeBlock = event.getClickedBlock().getRelative(event.getBlockFace());

                if (BlockGroup.FIRE.contains(relativeBlock.getType())) {
                    Player player = event.getPlayer();
                    Material type = relativeBlock.getType();
                    Queue.queueBlockBreak(player.getName(), relativeBlock.getState(), type, relativeBlock.getBlockData().getAsString(), 0);
                }
            }
        }
        else if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            Player player = event.getPlayer();
            Block block = event.getClickedBlock();
            World world = player.getWorld();

            if (block != null) {
                final Material type = block.getType();
                if (event.useInteractedBlock() != Event.Result.DENY) {
                    boolean isCake = false;

                    if (Tag.SIGNS.isTagged(type)) {
                        // check if right clicked sign with dye
                        Set<Material> dyeSet = EnumSet.of(Material.BLACK_DYE, Material.BLUE_DYE, Material.BROWN_DYE, Material.CYAN_DYE, Material.GRAY_DYE, Material.GREEN_DYE, Material.LIGHT_BLUE_DYE, Material.LIGHT_GRAY_DYE, Material.LIME_DYE, Material.MAGENTA_DYE, Material.ORANGE_DYE, Material.PINK_DYE, Material.PURPLE_DYE, Material.RED_DYE, Material.WHITE_DYE, Material.YELLOW_DYE);
                        Material handType = null;

                        ItemStack mainHand = player.getInventory().getItemInMainHand();
                        if (event.getHand().equals(EquipmentSlot.HAND) && mainHand != null) {
                            handType = mainHand.getType();
                        }

                        if (handType != null && (dyeSet.contains(handType) || handType.name().endsWith("INK_SAC")) && Config.getConfig(block.getWorld()).SIGN_TEXT) {
                            BlockState blockState = block.getState();
                            Sign sign = (Sign) blockState;
                            String line1 = sign.getLine(0);
                            String line2 = sign.getLine(1);
                            String line3 = sign.getLine(2);
                            String line4 = sign.getLine(3);
                            int oldColor = sign.getColor().getColor().asRGB();
                            int newColor = oldColor;
                            boolean oldGlowing = BukkitAdapter.ADAPTER.isGlowing(sign);
                            boolean newGlowing = oldGlowing;

                            if (dyeSet.contains(handType)) {
                                newColor = (DyeColor.valueOf(handType.name().replaceFirst("_DYE", ""))).getColor().asRGB();
                            }
                            else {
                                newGlowing = (handType != Material.INK_SAC);
                            }

                            if (oldGlowing != newGlowing || oldColor != newColor) {
                                Location location = blockState.getLocation();
                                Queue.queueSignText(player.getName(), location, 0, oldColor, oldGlowing, line1, line2, line3, line4, 1); // 1 second timeOffset
                                Queue.queueBlockPlace(player.getName(), block.getState(), block.getType(), blockState, block.getType(), -1, 0, blockState.getBlockData().getAsString());
                                Queue.queueSignText(player.getName(), location, 2, newColor, newGlowing, line1, line2, line3, line4, 0);
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

                            Queue.queuePlayerInteraction(player.getName(), interactBlock.getState());
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

                        isCake = type.name().endsWith(Material.CAKE.name());
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
                            int wid = Util.getWorldId(location.getWorld().getName());
                            int x = location.getBlockX();
                            int y = location.getBlockY();
                            int z = location.getBlockZ();
                            String coordinates = x + "." + y + "." + z + "." + wid + "." + userUUID;
                            CacheHandler.interactCache.put(coordinates, new Object[] { time, Material.CAKE, block.getState() });
                        }
                    }

                    if (event.useItemInHand() != Event.Result.DENY && Config.getConfig(world).BLOCK_PLACE) {
                        List<Material> entityBlockTypes = Arrays.asList(Material.ARMOR_STAND, Material.END_CRYSTAL);
                        Material handType = null;
                        ItemStack mainHand = player.getInventory().getItemInMainHand();
                        ItemStack offHand = player.getInventory().getItemInOffHand();

                        if (event.getHand().equals(EquipmentSlot.HAND) && mainHand != null && entityBlockTypes.contains(mainHand.getType())) {
                            handType = mainHand.getType();
                        }
                        else if (event.getHand().equals(EquipmentSlot.OFF_HAND) && offHand != null && entityBlockTypes.contains(offHand.getType())) {
                            handType = offHand.getType();
                        }
                        else {
                            return;
                        }

                        if (handType.equals(Material.END_CRYSTAL)) {
                            Location crystalLocation = block.getLocation();
                            if (crystalLocation.getBlock().getType().equals(Material.OBSIDIAN) || crystalLocation.getBlock().getType().equals(Material.BEDROCK)) {
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
                                    Bukkit.getServer().getScheduler().runTask(CoreProtect.getInstance(), () -> {
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
                                    });
                                }
                            }
                        }
                        else {
                            Block relativeBlock = block.getRelative(event.getBlockFace());
                            Location relativeBlockLocation = relativeBlock.getLocation();
                            Location blockLocation = block.getLocation();
                            String relativeBlockKey = world.getName() + "-" + relativeBlockLocation.getBlockX() + "-" + relativeBlockLocation.getBlockY() + "-" + relativeBlockLocation.getBlockZ();
                            String blockKey = world.getName() + "-" + blockLocation.getBlockX() + "-" + blockLocation.getBlockY() + "-" + blockLocation.getBlockZ();
                            Object[] keys = new Object[] { relativeBlockKey, blockKey, handType };

                            ConfigHandler.entityBlockMapper.put(player.getUniqueId(), keys);
                        }
                    }
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
