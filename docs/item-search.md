# Item search

`/co find` searches item properties in recorded transactions or online player inventories.

```
/co find i:netherite_pickaxe name:"Excavator" t:2d
/co find enchant:fortune=3 t:1d u:Steve
/co find source:online i:diamond
/co find source:online data:myplugin:reward_id=summer
/co find model:42 contents:true page:2
```

Names ignore colors and case. Add `exact:true` to require a full name match. Enchantments use a namespaced key and optional exact level (`enchant:minecraft:fortune=3`). `model:` matches integer custom model data. `data:` matches a persistent-data key, optionally with an exact string value. Matching properties does not uniquely identify an item.

Contents search is enabled by default. Results include slot paths through recorded or live bundles and shulker boxes. `contents:false` searches outer items only. Nested contents are shown as part of their parent's transaction, not as extra item transfers. Old logs can only expose metadata they actually recorded.

Historical search defaults to one day and accepts up to 31 days. It examines up to 10,000 recent inventory transaction rows, including containers and world-item transactions. It explicitly labels results partial when a limit is reached. Narrow `t:` or `u:` when that happens. Each page displays ten matching entries. Repeating a historical search queries the database again, so pages can change as new transactions arrive. Query failures are reported separately from empty results.

Live searches examine online players' inventories, armor, offhand, and Ender Chests on their owning scheduler. They include items inside carried bundles and shulker boxes. They do not scan offline player data or claim that world chests belong to a player. Players who disconnect or cannot be scanned are counted as unavailable. Each player's inventory is observed at its own scan time, not as one atomic server-wide snapshot.

Permissions: `coreprotect.find` plus `coreprotect.find.online` or `coreprotect.find.history`, all default to operators. Historical searches also require `coreprotect.lookup.inventory`, `coreprotect.lookup.container`, `coreprotect.lookup.item`, and `api-enabled`.
