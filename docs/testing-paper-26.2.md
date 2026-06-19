# Paper 26.2 Compatibility Test Matrix

Use this matrix for the `support-paper-26.2` branch before running the build on a live server. Test on a copied world first. Do not run the first pass on a production SMP world.

## Useful Commands

```text
/co status
/co inspect
/co lookup r:20 t:10m
/co rollback u:<user> t:10m r:20
/co restore u:<user> t:10m r:20
/co rollback u:#explosion t:10m r:30
/co restore u:#explosion t:10m r:30
```

## Environment

| Done | Setup / Action | CoreProtect command to verify | Expected result | Notes / failure |
| --- | --- | --- | --- | --- |
| [ ] | Run Paper `26.2.build.23-alpha` or the exact Paper 26.2 build selected for release validation. | `/co status` | CoreProtect reports enabled status on Paper 26.2. | Record exact Paper build. |
| [ ] | Run with the Java version used for release validation. Paper 26.2 API builds require Java 25 for compilation; runtime should use the Java version required by the Paper server build. | `/co status` | No Java linkage or class version errors appear. | Record `java -version`. |
| [ ] | Install the built jar from `target/CoreProtect-24.0.jar`. | `/plugins` | CoreProtect is listed and enabled. | Record jar checksum if needed. |
| [ ] | Create a fresh copied SQLite test world with default CoreProtect SQLite config. | `/co status` | Database connection is healthy. | Do not use the original production world. |
| [ ] | Optionally create a copied MySQL test world with a disposable CoreProtect database. | `/co status` | MySQL connection is healthy. | Skip only if MySQL is not part of deployment. |

## Startup

| Done | Setup / Action | CoreProtect command to verify | Expected result | Notes / failure |
| --- | --- | --- | --- | --- |
| [ ] | Start the Paper 26.2 server with CoreProtect installed. | `/plugins` | Server completes boot and CoreProtect appears green. | Capture startup log if it fails. |
| [ ] | Run status after boot. | `/co status` | Command returns normal CoreProtect status. | Note database mode and version shown. |
| [ ] | Inspect `logs/latest.log` after startup. | `/co status` | No CoreProtect exceptions, linkage errors, or adapter warnings. | Include stack trace if any. |
| [ ] | Restart the server once after initial startup. | `/plugins` | CoreProtect remains enabled after restart. | Confirms persistence path loads cleanly. |

## Baseline CoreProtect Behavior

| Done | Setup / Action | CoreProtect command to verify | Expected result | Notes / failure |
| --- | --- | --- | --- | --- |
| [ ] | Place a normal full block such as stone. | `/co lookup r:20 t:10m` | Placement is logged for the player. |  |
| [ ] | Break the same normal block. | `/co lookup r:20 t:10m` | Break is logged for the player. |  |
| [ ] | Insert an item into a chest and remove it. | `/co lookup r:20 t:10m` | Chest insert and remove transactions are logged. |  |
| [ ] | Insert fuel and smeltable input into a furnace, then remove output. | `/co lookup r:20 t:10m` | Furnace item movement is logged. |  |
| [ ] | Open and close a door and trapdoor. | `/co lookup r:20 t:10m` | Interactions are logged if interaction logging is enabled. |  |
| [ ] | Place and remove water with a bucket. | `/co lookup r:20 t:10m` | Water place and remove are logged. |  |
| [ ] | Place and remove lava with a bucket. | `/co lookup r:20 t:10m` | Lava place and remove are logged. |  |
| [ ] | Kill a normal entity such as a cow. | `/co lookup r:20 t:10m` | Entity kill is logged with entity type. |  |
| [ ] | Detonate TNT near disposable blocks. | `/co lookup r:20 t:10m` | TNT priming and explosion block breaks are logged. |  |
| [ ] | Enable inspect and click recent test blocks or containers. | `/co inspect` | Inspect reports the expected recent actions. | Run `/co inspect` again to disable. |
| [ ] | Roll back the baseline player actions in the test radius. | `/co rollback u:<user> t:10m r:20` | Blocks and containers revert as expected. | Replace `<user>` with tester name. |
| [ ] | Restore the same baseline player actions. | `/co restore u:<user> t:10m r:20` | Blocks and containers return as expected. | Replace `<user>` with tester name. |
| [ ] | Restart the server, then repeat lookup and rollback on recent actions. | `/co lookup r:20 t:10m` | Lookup, rollback, and restore still work after restart. | Confirms persisted data reload. |

## Minecraft 26.2 Blocks

| Done | Setup / Action | CoreProtect command to verify | Expected result | Notes / failure |
| --- | --- | --- | --- | --- |
| [ ] | Place and break each sulfur block family variant available in Paper 26.2. | `/co lookup r:20 t:10m` | Each placement and break logs with the correct material. | Record any missing material names. |
| [ ] | Place and break each cinnabar block family variant available in Paper 26.2. | `/co lookup r:20 t:10m` | Each placement and break logs with the correct material. | Record any missing material names. |
| [ ] | Place and break sulfur and cinnabar slab variants, including top, bottom, and double states where available. | `/co lookup r:20 t:10m` | Slab block states are logged. |  |
| [ ] | Place and break sulfur and cinnabar stair variants in several facing and shape states. | `/co lookup r:20 t:10m` | Stair block states are logged. |  |
| [ ] | Place and break sulfur and cinnabar wall variants with connected and unconnected states. | `/co lookup r:20 t:10m` | Wall block states are logged. |  |
| [ ] | Place, update, and break `POTENT_SULFUR`. | `/co lookup r:20 t:10m` | Placement, break, and state-changing updates are logged when events are exposed. | Note any state changes not exposed by Bukkit/Paper events. |
| [ ] | Place `SULFUR_SPIKE` on supported faces, remove support, and break it directly. | `/co lookup r:20 t:10m` | Spike placement, direct break, and support break results are logged. | Verify vertical attachment behavior. |
| [ ] | Roll back and restore all 26.2 block placements and breaks. | `/co rollback u:<user> t:10m r:20` then `/co restore u:<user> t:10m r:20` | Exact material and block states return. | Check orientation, waterlogging, slab halves, stairs, and walls. |
| [ ] | Trigger potent sulfur or geyser state changes if testable in the server build. | `/co lookup r:20 t:10m` | State changes are logged when CoreProtect receives block events for them. | Record server steps used to trigger the change. |

## Sulfur Cube Spawn And Bucket

| Done | Setup / Action | CoreProtect command to verify | Expected result | Notes / failure |
| --- | --- | --- | --- | --- |
| [ ] | Spawn a Sulfur Cube with `SULFUR_CUBE_SPAWN_EGG`. | `/co lookup r:20 t:10m` | Spawn egg placement is logged through entity placement handling. |  |
| [ ] | Place a Sulfur Cube from `SULFUR_CUBE_BUCKET`. | `/co lookup r:20 t:10m` | Bucket placement is logged through entity placement handling. |  |
| [ ] | Capture a Sulfur Cube into a bucket. | `/co lookup r:20 t:10m` | Empty bucket removal and Sulfur Cube bucket creation are logged. |  |
| [ ] | Verify bucket transaction details after capture. | `/co lookup r:20 t:10m` | Item transaction history shows the bucket conversion. |  |
| [ ] | Kill or remove a Sulfur Cube if entity kill logging is enabled. | `/co lookup r:20 t:10m` | Entity lookup shows `SULFUR_CUBE` if supported by the command path. |  |
| [ ] | Roll back and restore Sulfur Cube entity placement/removal where supported. | `/co rollback u:<user> t:10m r:20` then `/co restore u:<user> t:10m r:20` | Entity restore works, including size, ageable state, fuse ticks, and from-bucket state when captured in metadata. | Entity rollback must be enabled in config. |

## Sulfur Cube Absorbed Content

| Done | Setup / Action | CoreProtect command to verify | Expected result | Notes / failure |
| --- | --- | --- | --- | --- |
| [ ] | Create a `SULFUR_CUBE_BUCKET` with absorbed content, then log it through capture or inventory movement. | `/co lookup r:20 t:10m` | Logged item data preserves the absorbed content component when the bucket item is restored. | Requires a reliable in-game way to create absorbed content. |
| [ ] | Roll back and restore a container or transaction involving the absorbed-content bucket. | `/co rollback u:<user> t:10m r:20` then `/co restore u:<user> t:10m r:20` | Restored bucket still contains the absorbed content item data. | Compare tooltip/NBT-equivalent behavior in game. |
| [ ] | Roll back and restore a live Sulfur Cube with absorbed entity content. | `/co rollback u:<user> t:10m r:20` then `/co restore u:<user> t:10m r:20` | Known limitation: absorbed live entity content is not restored because Paper exposes entity data components read-only here. | Do not fail the build for this limitation unless Paper exposes a safe setter. |

## Sulfur Cube Shearing

| Done | Setup / Action | CoreProtect command to verify | Expected result | Notes / failure |
| --- | --- | --- | --- | --- |
| [ ] | Player shears a Sulfur Cube with absorbed content. | `/co lookup r:20 t:10m` | Player-attributed shearing interaction and resulting dropped content are logged through existing patterns. |  |
| [ ] | Player shears a Sulfur Cube with no absorbed content. | `/co lookup r:20 t:10m` | No error occurs and no invalid item transaction is logged. | Check `latest.log`. |
| [ ] | Dispenser shears a Sulfur Cube with absorbed content. | `/co lookup r:20 t:10m` | Block/dispenser-attributed shearing and dropped content are logged if the event exposes them. |  |
| [ ] | Dispenser shears a Sulfur Cube with no absorbed content. | `/co lookup r:20 t:10m` | No error occurs and no invalid item transaction is logged. | Check `latest.log`. |
| [ ] | Cancel player shearing with another plugin or test protection rule if available. | `/co lookup r:20 t:10m` | Cancelled shearing is ignored by CoreProtect. | Optional if no cancellation plugin is available. |
| [ ] | Cancel dispenser shearing with another plugin or test protection rule if available. | `/co lookup r:20 t:10m` | Cancelled dispenser shearing is ignored by CoreProtect. | Optional if no cancellation plugin is available. |

## Sulfur Cube Dispenser Interaction

| Done | Setup / Action | CoreProtect command to verify | Expected result | Notes / failure |
| --- | --- | --- | --- | --- |
| [ ] | Dispenser inserts content into a Sulfur Cube. | `/co lookup r:20 t:10m` | Input item removal is logged if existing dispenser inventory logging does not already cover it. | Check for duplicate input logs. |
| [ ] | Dispenser swaps absorbed content on a Sulfur Cube. | `/co lookup r:20 t:10m` | Removed dispenser item and returned item/drop are logged when exposed by events. |  |
| [ ] | Dispenser removes absorbed content from a Sulfur Cube if the server exposes this behavior. | `/co lookup r:20 t:10m` | Returned item/drop is logged when exposed by events. | Mark not applicable if Paper exposes no event/drop. |
| [ ] | Verify existing dispenser inventory logging during Sulfur Cube insertion. | `/co lookup r:20 t:10m` | No duplicate input item removal is recorded. |  |
| [ ] | Verify returned drop attribution from dispenser interactions. | `/co lookup r:20 t:10m` | Returned drop is logged as `#dispenser` when emitted. |  |

## Sulfur Cube Explosion / TNT

| Done | Setup / Action | CoreProtect command to verify | Expected result | Notes / failure |
| --- | --- | --- | --- | --- |
| [ ] | Create a TNT-absorbed Sulfur Cube using the server-supported 26.2 interaction. | `/co lookup r:20 t:10m` | Setup actions are logged where existing item/entity paths expose them. | Record exact setup steps. |
| [ ] | Trigger the TNT-absorbed Sulfur Cube explosion near disposable blocks. | `/co lookup r:20 t:10m` | Affected blocks are logged as `#explosion`. | Custom `#sulfur_cube` attribution is intentionally not implemented. |
| [ ] | Roll back affected explosion blocks. | `/co rollback u:#explosion t:10m r:30` | Blocks damaged by the Sulfur Cube explosion are restored. |  |
| [ ] | Restore affected explosion blocks. | `/co restore u:#explosion t:10m r:30` | Explosion damage is reapplied as expected. |  |
| [ ] | Inspect `latest.log` after the Sulfur Cube explosion. | `/co lookup r:20 t:10m` | No CoreProtect exceptions or null handling errors. |  |

## Storage

| Done | Setup / Action | CoreProtect command to verify | Expected result | Notes / failure |
| --- | --- | --- | --- | --- |
| [ ] | Run the full matrix on SQLite. | `/co status` | SQLite test pass completes without CoreProtect errors. | Required. |
| [ ] | Restart SQLite server after full matrix, then run lookup and rollback spot checks. | `/co lookup r:20 t:10m` | Persisted SQLite data remains usable. | Required. |
| [ ] | Run the full matrix on MySQL if available. | `/co status` | MySQL test pass completes without CoreProtect errors. | Required only for MySQL deployments. |
| [ ] | Restart MySQL server after full matrix, then run lookup and rollback spot checks. | `/co lookup r:20 t:10m` | Persisted MySQL data remains usable. | Required only for MySQL deployments. |

## Final Acceptance

| Done | Setup / Action | CoreProtect command to verify | Expected result | Notes / failure |
| --- | --- | --- | --- | --- |
| [ ] | Confirm every required SQLite row above passed on a copied Paper 26.2 server. | `/co status` | Branch is safe for copied test servers. | Do not use production data for first validation. |
| [ ] | Confirm MySQL rows passed if MySQL is used by the target deployment. | `/co status` | Branch is safe for MySQL-backed copied test servers. |  |
| [ ] | Review all notes and failures from this matrix. | `/co lookup r:20 t:10m` | No unresolved required failures remain. |  |
| [ ] | Approve real SMP testing only after all required tests pass on copied worlds. | `/plugins` | CoreProtect remains enabled and stable. | Real SMP rollout is not approved before this point. |
