![CoreProtect](https://userfolio.com/uploads/coreprotect-banner-v19.png)

[![Artistic License 2.0](https://img.shields.io/github/license/PlayPro/CoreProtect?&logo=github)](https://github.com/PlayPro/CoreProtect/blob/master/LICENSE)
[![GitHub Workflows](https://github.com/PlayPro/CoreProtect/actions/workflows/build.yml/badge.svg)](https://github.com/PlayPro/CoreProtect/actions)
[![Netlify Status](https://img.shields.io/netlify/c1d26a0f-65c5-4e4b-95d7-e08af671ab67)](https://app.netlify.com/sites/coreprotect/deploys)
[![CodeFactor](https://www.codefactor.io/repository/github/playpro/coreprotect/badge)](https://www.codefactor.io/repository/github/playpro/coreprotect)
[![Join us on Discord](https://img.shields.io/discord/348680641560313868.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/b4DZ4jy)

# CoreProtect

CoreProtect is a fast, efficient data logging and anti-griefing tool. Inspect, lookup, rollback, and restore any amount of damage without impacting your server performance.

CoreProtect is the [#1 anti-griefing plugin](https://bstats.org/plugin/bukkit/CoreProtect), and has been actively developed since early 2012.

| Quick Links |  |
| --- | --- |
| CoreProtect Discord: | [discord.gg/b4DZ4jy](https://discord.gg/b4DZ4jy) |
| CoreProtect Patreon: | [patreon.com/coreprotect](https://www.patreon.com/coreprotect) |
| CoreProtect Documentation: | [docs.coreprotect.net](https://docs.coreprotect.net) |
| Downloads for MC 1.14 - 26.1: | [coreprotect.net/latest](https://coreprotect.net/latest/) |
| Downloads for MC 1.8 - 1.12: | [coreprotect.net/legacy](https://coreprotect.net/legacy/) |
| Downloads for Fabric: | [coreprotect.net/fabric](https://coreprotect.net/fabric) |
| Downloads for Hytale: | [coreprotect.net/hytale](https://coreprotect.net/hytale) |

## API

### [API Documentation](https://docs.coreprotect.net/api/)

### Dependency Information

Maven:

```xml
<repository>
    <id>playpro-repo</id>
    <url>https://maven.playpro.com</url>
</repository>
```

```xml
<dependency>
    <groupId>net.coreprotect</groupId>
    <artifactId>coreprotect</artifactId>
    <version>23.2</version>
    <scope>provided</scope>
</dependency>
```

## Donation Keys

To support the project and obtain a donation key, visit [coreprotect.net/donate](https://coreprotect.net/donate/).

## Other Plugins

* DarkerNights: [spigotmc.org/resources/darkernights.87814](https://www.spigotmc.org/resources/darkernights.87814/)
* TransitTubes: [patreon.com/posts/75731668](https://www.patreon.com/posts/75731668)

## Video Tutorial

[![CoreProtect video tutorial](https://www.userfolio.com/uploads/coreprotect-video-tutorial.png)](https://youtu.be/JwijCiueZ3Y)

## Core Features

* Fast, efficient data logging.
* Rollbacks and restores with lag-free processing.
* Multi-threaded to ensure server performance is never impacted.
* No configuration required. Install the plugin, and you're good to go.
* SQLite storage by default.
* Optional MySQL support.
* Supports Bukkit, Spigot, Paper, Folia, MultiPaper, and more.
* Permission system support.
* Easy-to-use commands and inspector.
* Advanced lookup filters.
* Paginated results with clickable pagination.
* Preview rollbacks and restores.
* Rollback or restore specific users, multiple users, or global activity.
* Limit operations by radius, world, or WorldEdit selection.
* Undo rollbacks by restoring the same data.
* Inventory rollback support.
* Include or exclude specific blocks, items, entities, or users.
* Purge old data by time, world, and block type.
* Multi-world support.
* Configurable logging settings.
* Per-world logging settings.
* Safe default parameters.
* User, command, and block logging blacklists.
* Advanced lookup permissions.
* WorldEdit and supported FAWE logging.
* Enhanced item lookup tooltips.
* Localization and translation support.
* Automatic update checker.
* Automatic error reporting.
* Developer API.
* Networking API support.
* *...and much more!*

## What does it log?

### Blocks and World Changes

* Blocks placed by players.
* Blocks broken by players.
* Natural block breakage, such as attached blocks breaking when their support is removed.
* Block movement, including falling sand and gravel.
* Blocks moved by pistons.
* Bucket usage.
* Water and lava placement or removal.
* Water and lava flow.
* Liquid tracking, associating flowing liquids with the player who placed the source.
* Flint and steel, fire charge, and fire ignition.
* Block burning.
* Fire fade.
* Optional natural fire extinguishing.
* Explosions from TNT, Creepers, Ghasts, and other sources.
* Removal of primed TNT.
* Entities changing blocks, including Endermen and Ender Dragons.
* Leaf decay.
* Tree growth linked back to the player who planted the sapling.
* Mushroom growth.
* Vine growth.
* Amethyst, chorus, and bamboo growth.
* Sculk spread from sculk catalysts.
* Nether portal and other natural portal generation.
* Crops trampled by players.
* Turtle eggs trampled by players or entities.
* Snow generated by snow golems.
* Liquid-formed blocks such as obsidian, cobblestone, and concrete.
* Zombies breaking doors.
* Flowers placed in flower pots.
* Dragon egg teleportation.
* Suspicious sand and gravel brushing.
* Suspicious sand and gravel destruction.
* Custom blocks, such as CraftEngine blocks.

### Containers, Inventories, and Items

* Items taken from or placed into containers.
* Supported third-party containers.
* Hopper transactions.
* Items dropped into hoppers.
* Dropper and dispenser transactions.
* Items moved into other containers.
* Supported block-removal actions.
* Player inventory transactions.
* Items dropped by players.
* Items thrown by players.
* Items shot by players.
* Items picked up by players.
* Items broken, created, or destroyed by players.
* Items deposited or withdrawn by players.
* Items placed on campfires.
* Lectern book transactions.
* Items stored in or removed from chiseled bookshelves.
* Decorated pot and shelf inventory transactions.
* Copper Golem chest transactions.
* Jukebox transactions.
* Crafter slot changes.
* Bundle actions through the `#bundle` tag.
* Items crafted or traded with villagers.
* Allay item exchanges.
* Paintings and item frames.

### Entities and Player Activity

* Entity deaths.
* Detailed death reasons.
* Player-caused entity kills, including animals and monsters.
* Armor stands and End Crystals.
* Villagers killed by lightning.
* Villager gossip data.
* Villager job-site memories.
* Thrown eggs.
* Player interactions.
* Doors, buttons, and levers.
* Container interactions.
* Chiseled bookshelf interactions.
* Jukebox interactions.
* Sign text, color, glow, wax, and modern double-sided signs.
* Player chat messages.
* Commands used by players.
* Player sessions, including logins and logouts.
* Player deaths.
* Username changes.
* Skull skin texture data on Paper servers.
* Custom skull textures.
* WorldEdit changes and supported FAWE clipboard pastes.
* *...and the list is still expanding!*

## How to use the inspector

Once you have the inspector enabled with `/core inspect` or `/co i`, you can do the following:

* Left-click a block to see who placed that block.
* Right-click a block to see what adjacent block was removed.
* Right-click while placing a block in a location to see what block was removed at that location.
* Right-click while placing a block in another block to see who placed it. For example, place dirt in water to see who placed the water.
* Right-click a door, button, lever, container, or similar block to see who last used it.

## A Few Reviews

* *"It's easy to use and lightning fast when it comes to rollbacks."*
* *"I've been running a MC server since before Bukkit, so I've used just about every block logger out there. CoreProtect is the best and the fastest."*
* *"Go with CoreProtect. It's fast, updated frequently, and logs WorldEdit."*
* *"Overall it felt more natural, simpler, and much faster to use CoreProtect than Prism, which just doesn't compare."*
* *"CoreProtect brings complex logging and rollbacks, whilst at the same time keeping a low profile."*
* *Featured on [Linus Tech Tips](https://www.userfolio.com/uploads/coreprotect-linus.png).*

## Useful Links

**Documentation**  
https://docs.coreprotect.net

**Commands**  
https://docs.coreprotect.net/commands/

**Permissions**  
https://docs.coreprotect.net/permissions/

**API Documentation**  
https://docs.coreprotect.net/api/

**Feedback & Support**  
https://github.com/PlayPro/CoreProtect/issues

**Discord**  
Join us on Discord: https://discord.gg/b4DZ4jy

**Donate**  
Enjoy using CoreProtect and want to show your support? [Join our Patreon!](https://www.patreon.com/coreprotect)

**Sponsors**  
Thanks to [HostHorde](https://www.hosthorde.com) for sponsoring CoreProtect!

## Bug Reports

For any bug reports, please submit a ticket here:  
https://github.com/PlayPro/CoreProtect/issues

Thanks for your support!

## bStats

[![bStats Graph Data](https://bstats.org/signatures/bukkit/CoreProtect.svg)](https://bstats.org/plugin/bukkit/CoreProtect)

## Contributing

CoreProtect is an open source project, and gladly accepts community contributions.

If you'd like to contribute, please read our contributing guidelines here: [CONTRIBUTING.md](CONTRIBUTING.md)

[![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-2.0-4baaaa.svg)](CONTRIBUTING.md#code-of-conduct)
