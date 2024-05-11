package net.coreprotect.listener;

import org.bukkit.plugin.PluginManager;

import net.coreprotect.CoreProtect;
import net.coreprotect.listener.block.BlockBreakListener;
import net.coreprotect.listener.block.BlockBurnListener;
import net.coreprotect.listener.block.BlockDispenseListener;
import net.coreprotect.listener.block.BlockExplodeListener;
import net.coreprotect.listener.block.BlockFadeListener;
import net.coreprotect.listener.block.BlockFertilizeListener;
import net.coreprotect.listener.block.BlockFormListener;
import net.coreprotect.listener.block.BlockFromToListener;
import net.coreprotect.listener.block.BlockIgniteListener;
import net.coreprotect.listener.block.BlockPistonListener;
import net.coreprotect.listener.block.BlockPlaceListener;
import net.coreprotect.listener.block.BlockSpreadListener;
import net.coreprotect.listener.block.CampfireStartListener;
import net.coreprotect.listener.channel.PluginChannelHandshakeListener;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.listener.entity.CreatureSpawnListener;
import net.coreprotect.listener.entity.EntityBlockFormListener;
import net.coreprotect.listener.entity.EntityChangeBlockListener;
import net.coreprotect.listener.entity.EntityDamageByBlockListener;
import net.coreprotect.listener.entity.EntityDamageByEntityListener;
import net.coreprotect.listener.entity.EntityDeathListener;
import net.coreprotect.listener.entity.EntityExplodeListener;
import net.coreprotect.listener.entity.EntityInteractListener;
import net.coreprotect.listener.entity.EntityPickupItemListener;
import net.coreprotect.listener.entity.HangingBreakByEntityListener;
import net.coreprotect.listener.entity.HangingBreakListener;
import net.coreprotect.listener.entity.HangingPlaceListener;
import net.coreprotect.listener.player.ArmorStandManipulateListener;
import net.coreprotect.listener.player.CraftItemListener;
import net.coreprotect.listener.player.FoodLevelChangeListener;
import net.coreprotect.listener.player.InventoryChangeListener;
import net.coreprotect.listener.player.InventoryClickListener;
import net.coreprotect.listener.player.PlayerBucketEmptyListener;
import net.coreprotect.listener.player.PlayerBucketFillListener;
import net.coreprotect.listener.player.PlayerChatListener;
import net.coreprotect.listener.player.PlayerCommandListener;
import net.coreprotect.listener.player.PlayerDeathListener;
import net.coreprotect.listener.player.PlayerDropItemListener;
import net.coreprotect.listener.player.PlayerInteractEntityListener;
import net.coreprotect.listener.player.PlayerInteractListener;
import net.coreprotect.listener.player.PlayerItemBreakListener;
import net.coreprotect.listener.player.PlayerJoinListener;
import net.coreprotect.listener.player.PlayerPickupArrowListener;
import net.coreprotect.listener.player.PlayerQuitListener;
import net.coreprotect.listener.player.PlayerTakeLecternBookListener;
import net.coreprotect.listener.player.ProjectileLaunchListener;
import net.coreprotect.listener.player.SignChangeListener;
import net.coreprotect.listener.world.ChunkPopulateListener;
import net.coreprotect.listener.world.LeavesDecayListener;
import net.coreprotect.listener.world.PortalCreateListener;
import net.coreprotect.listener.world.StructureGrowListener;
import net.coreprotect.paper.listener.BlockPreDispenseListener;
import net.coreprotect.paper.listener.PaperChatListener;

public final class ListenerHandler {

    public ListenerHandler(CoreProtect plugin) {

        PluginManager pluginManager = plugin.getServer().getPluginManager();

        // Paper Listeners / Fallbacks (Block Listeners)
        try {
            Class.forName("io.papermc.paper.event.block.BlockPreDispenseEvent"); // Paper 1.16+
            pluginManager.registerEvents(new BlockPreDispenseListener(), plugin);
        }
        catch (Exception e) {
            BlockPreDispenseListener.useBlockPreDispenseEvent = false;
        }

        // Block Listeners
        pluginManager.registerEvents(new BlockBreakListener(), plugin);
        pluginManager.registerEvents(new BlockBurnListener(), plugin);
        pluginManager.registerEvents(new BlockDispenseListener(), plugin);
        pluginManager.registerEvents(new BlockExplodeListener(), plugin);
        pluginManager.registerEvents(new BlockFadeListener(), plugin);
        pluginManager.registerEvents(new BlockFertilizeListener(), plugin);
        pluginManager.registerEvents(new BlockFormListener(), plugin);
        pluginManager.registerEvents(new BlockFromToListener(), plugin);
        pluginManager.registerEvents(new BlockIgniteListener(), plugin);
        pluginManager.registerEvents(new BlockPistonListener(), plugin);
        pluginManager.registerEvents(new BlockPlaceListener(), plugin);
        pluginManager.registerEvents(new BlockSpreadListener(), plugin);
        try {
            Class.forName("org.bukkit.event.block.CampfireStartEvent"); // Bukkit 1.20+
            pluginManager.registerEvents(new CampfireStartListener(), plugin);
        }
        catch (Exception e) {
            CampfireStartListener.useCampfireStartEvent = false;
        }

        // Entity Listeners
        pluginManager.registerEvents(new CreatureSpawnListener(), plugin);
        pluginManager.registerEvents(new EntityBlockFormListener(), plugin);
        pluginManager.registerEvents(new EntityChangeBlockListener(), plugin);
        pluginManager.registerEvents(new EntityDamageByBlockListener(), plugin);
        pluginManager.registerEvents(new EntityDamageByEntityListener(), plugin);
        pluginManager.registerEvents(new EntityDeathListener(), plugin);
        pluginManager.registerEvents(new EntityExplodeListener(), plugin);
        pluginManager.registerEvents(new EntityInteractListener(), plugin);
        pluginManager.registerEvents(new EntityPickupItemListener(), plugin);
        pluginManager.registerEvents(new HangingPlaceListener(), plugin);
        pluginManager.registerEvents(new HangingBreakListener(), plugin);
        pluginManager.registerEvents(new HangingBreakByEntityListener(), plugin);

        // Paper Listeners / Fallbacks (Player Listeners)
        try {
            Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer"); // Paper 1.16+
            pluginManager.registerEvents(new PaperChatListener(), plugin);
        }
        catch (Exception e) {
            pluginManager.registerEvents(new PlayerChatListener(), plugin);
        }

        // Player Listeners
        pluginManager.registerEvents(new ArmorStandManipulateListener(), plugin);
        pluginManager.registerEvents(new CraftItemListener(), plugin);
        pluginManager.registerEvents(new FoodLevelChangeListener(), plugin);
        pluginManager.registerEvents(new InventoryChangeListener(), plugin);
        pluginManager.registerEvents(new InventoryClickListener(), plugin);
        pluginManager.registerEvents(new PlayerBucketEmptyListener(), plugin);
        pluginManager.registerEvents(new PlayerBucketFillListener(), plugin);
        pluginManager.registerEvents(new PlayerCommandListener(), plugin);
        pluginManager.registerEvents(new PlayerDeathListener(), plugin);
        pluginManager.registerEvents(new PlayerDropItemListener(), plugin);
        pluginManager.registerEvents(new PlayerPickupArrowListener(), plugin);
        pluginManager.registerEvents(new PlayerInteractEntityListener(), plugin);
        pluginManager.registerEvents(new PlayerInteractListener(), plugin);
        pluginManager.registerEvents(new PlayerItemBreakListener(), plugin);
        pluginManager.registerEvents(new PlayerJoinListener(), plugin);
        pluginManager.registerEvents(new PlayerQuitListener(), plugin);
        pluginManager.registerEvents(new SignChangeListener(), plugin);
        pluginManager.registerEvents(new PlayerTakeLecternBookListener(), plugin);
        pluginManager.registerEvents(new ProjectileLaunchListener(), plugin);

        // World Listeners
        pluginManager.registerEvents(new ChunkPopulateListener(), plugin);
        pluginManager.registerEvents(new LeavesDecayListener(), plugin);
        pluginManager.registerEvents(new PortalCreateListener(), plugin);
        pluginManager.registerEvents(new StructureGrowListener(), plugin);

        // Plugin channel events
        pluginManager.registerEvents(new PluginChannelListener(), plugin);
    }

    public static void registerNetworking() {
        CoreProtect.getInstance().getServer().getMessenger().registerIncomingPluginChannel(CoreProtect.getInstance(), PluginChannelHandshakeListener.pluginChannel, new PluginChannelHandshakeListener());
        CoreProtect.getInstance().getServer().getMessenger().registerOutgoingPluginChannel(CoreProtect.getInstance(), PluginChannelHandshakeListener.pluginChannel);
        CoreProtect.getInstance().getServer().getMessenger().registerOutgoingPluginChannel(CoreProtect.getInstance(), PluginChannelListener.pluginChannel);
    }

    public static void unregisterNetworking() {
        CoreProtect.getInstance().getServer().getMessenger().unregisterIncomingPluginChannel(CoreProtect.getInstance(), PluginChannelHandshakeListener.pluginChannel);
        CoreProtect.getInstance().getServer().getMessenger().unregisterOutgoingPluginChannel(CoreProtect.getInstance(), PluginChannelHandshakeListener.pluginChannel);
        CoreProtect.getInstance().getServer().getMessenger().unregisterOutgoingPluginChannel(CoreProtect.getInstance(), PluginChannelListener.pluginChannel);
    }

}
