# Commands

You can access the commands using either `/coreprotect` or it's aliases `/core` or `/co`.

## Command Overview

| Command | Description |
| --- | --- |
| [help](#help) | Display a list of commands |
| [inspect](#inspect) | Toggle the inspector |
| [rollback](#rollback) | Rollback block data |
| [restore](#restore) | Restore block data |
| [lookup](#lookup) | Advanced block data lookup |
| [purge](#purge) | Delete old block data |
| [reload](#reload) | Reload the configuration file |
| [status](#status) | View the plugin status |
| [near](#near) | Alias for lookup with a radius of 5 |
| [undo](#undo) | Revert a rollback/restore |

## Commands Detailed

### help

Usage:
`/co help`

Displays the list of available commands

Example Output:

```text
----- CoreProtect Help -----
/co help <command> - Display more info for that command.
/co inspect - Turns the block inspector on or off.
/co rollback <params> - Rollback block data.
/co restore <params> - Restore block data.
/co lookup <params> - Advanced block data lookup.
/co purge <params> - Delete old block data.
/co reload - Reloads the configuration file.
/co status - Displays the plugin status.
```

### inspect

Usage:
`/co inspect`

Alias: `/co i`

Enables the in-game inspector. Once activated, right-click (use) button on blocks to get detailed history of that specific block. Run the command again to return your controls to normal.

### lookup

Usage:
`/co lookup <filter syntax>` See: [Filter Syntax](#filter-syntax)

Alias: `/co l`

Search through block data using parameters from filter syntax.

#### Pagination

If multiple pages are returned, use the command `/co lookup <page #>` to switch pages.
To change the number of lines displayed on a page, use the command `/co lookup <page #>:<# of lines>`.

Example: `/co l 1:10` will return 10 lines of data, starting at the first page.

### rollback

Usage:
`/co rollback <filter syntax>` See: [Filter Syntax](#filter-syntax)

Alias: `/co rb`

Undo all block data which matches the filter.

### restore

`/co restore <filter syntax>` See: [Filter Syntax](#filter-syntax)

Alias `/co rs`

Restoring can be used to undo rollbacks. This will playback all block data which matches the filter that may have been previously rolled back.

### purge

`/co purge t:<time> r:<world>`

Purge old block data. Useful for freeing up storage space if you don't need the older data.

Example: `/co purge t:30d` will delete all data older than one month, and only keep the last 30 days of data.
If used in-game, only data older than 30 days can be purged. If used from the console, only data older than 24 hours can be purged.

In CoreProtect v19+ | Specify World

Example: `/co purge t:30d r:#world_nether` will delete all data older than one month in the Nether, without deleting data in any other worlds.

In CoreProtect v2.15+ | Optimize tag for MySQL

Add `#optimize` to the end of the command like: `/co purge t:30d #optimize` will also optimize your tables and reclaim disk space.
This option is only available when using MySQL/MariaDB, as SQLite purges do this by default.

*Please note adding the #optimize option will significantly slow down your purge, and is generally unnecessary.*

## Examples

### rollback Examples

By default, if no radius is specified, a radius of 10 will be applied, restricting the rollback to within 10 blocks of you. Use `r:#global` to do a global rollback.

Rollback Notch 1 hour (with default radius of 10):
>`/co rollback Notch t:1h`

PREVIEW rolling back both Notch & Intelli 1 hour (with default radius of 10):
>`/co rollback u:Notch,Intelli t:1h #preview`

Rollback Notch 23 hours and 17 minutes (with default radius of 10):
>`/co rollback u:Notch t:23h17m`

Rollback ONLY stone placed/broken by Notch within the last hour (with default radius of 10):
>`/co rollback u:Notch t:1h b:1`

Rollback ONLY stone BROKEN by Notch within the last hour (with default radius of 10):
>`/co rollback u:Notch t:1h b:stone a:-block`

Rollback EVERYTHING Notch did in the last hour EXCEPT for stone and dirt placed/broken:
>`/co rollback u:Notch t:1h r:#global e:stone,dirt`

Rollback griefing Notch did in the last hour that is within 20 blocks of you:
>`/co rollback u:Notch t:1h r:20`

Rollback griefing Notch did in the last hour ONLY in the Nether:
>`/co rollback u:Notch t:1h r:#nether`

Rollback everything done in the last 15 minutes by anyone within 30 blocks of you:
>`/co rollback t:15m r:30`

Rollback everything done in the last 15 minutes in a WorldEdit selection:
>`/co rollback t:15m r:#worldedit`

### lookup examples

Lookup commands are generally the same as rollback commands. The primary difference is that a default radius is not applied to lookups, meaning all lookup commands do a global search by default.

Lookup all diamond ore mined in the last hour:
>`/co lookup b:56 t:1h a:-block`

Lookup all chat messages sent by Notch in the last 30 minutes:
>`/co lookup u:Notch t:30m a:chat`

Lookup all logins ever done by Notch:
>`/co lookup u:Notch a:login`

Lookup all logins ever done by Notch:
>`/co lookup u:Notch a:login`

Lookup previous usernames used by Notch:
>`/co lookup u:Notch a:username`

## Filter Syntax

`u:<user>` - Specify a user to rollback.
>Example: `u:Notch`

You can also combine multiple users.
>Example: `u:Notch,Intelli`

`t:<time>` - Specify the amount of time to rollback

You can specify weeks,days,hours,minutes, and seconds.

>Example: `t:2w,5d,7h,2m,10s`

You can pick and choose time amounts.
>Example: `t:5d2h`

You can also use decimals.
>Example: `t:2.50h` (2 and a half hours)

`r:<radius>` - Specify a radius. You can use this to only rollback blocks near you.

You can specify a number (e.g. `r:5`), a world (e.g. `r:#world_the_end`), a global rollback (`r:#global`), or a WorldEdit selection (`r:#worldedit` or `r:#we`)

For example, the following would only rollback damage within 10 blocks of where you are standing: `r:10`

`a:<action>` - Restrict the lookup to a certain action
For example, if you wanted to only rollback blocks placed, you would use `a:+block`

Here's a list of all the actions:

| Syntax | Description |
| --- | --- |
| `a:block` | blocks placed/broken |
| `a:+block` | blocks placed |
| `a:-block` | blocks broken |
| `a:chat` | messages sent in chat |
| `a:click` | player interactions |
| `a:command` | commands used |
| `a:container` | items taken from or put in chests |
| `a:+container` | items put in chests |
| `a:-container` | items taken from chests |
| `a:inventory` | items dropped or picked up by players |
| `a:+inventory` | items picked up by players |
| `a:-inventory` | items dropped by players |
| `a:item` | items dropped, picked up, taken from, or put in chests |
| `a:+item` | items picked up or put in chests |
| `a:-item` | items dropped or taken from chests |
| `a:kill` | mobs/animals killed |
| `a:session` | player logins/logouts |
| `a:+session` | player logins |
| `a:-session` | player logouts |
| `a:sign` | messages written on signs |
| `a:username` | username changes |

`b:<blocks>` - Restrict the rollback to certain block types.

For example, if you wanted to only rollback stone, you would use `b:stone`
You can specify multiple blocks, such as `b:stone,oak_wood,bedrock`

You can find a list of block type IDs at the [Gamepedia Minecraft Wiki](https://minecraft.fandom.com/wiki/Java_Edition_data_values)

`e:<exclude>` - Exclude certain block types from the rollback.
For example, if you don't want TNT to come back during a rollback, you would type `e:tnt`

`#<hashtag>` - Add a hashtag to the end of your command to perform additional actions.
For example, to perform a rollback preview, you would use `#preview`

Here's a list of available hashtags:

| Hashtag | Effect |
| --- | --- |
| `#preview` | Preview a rollback/restore |
| `#count` | Return the number of rows found in a lookup query |
| `#verbose` | Display additional information during a rollback/restore |
| `#silent` | Display minimal information during a rollback/restore |
