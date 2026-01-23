# Configuration

The CoreProtect configuration file can be found within the CoreProtect folder, at `config.yml`.

## Per-World Configuration

If you'd like to modify the logging settings for a specific world, simply do the following:

1. Copy the config.yml file to the name of the world (e.g. world_nether.yml)
2. In the new file, modify the logging settings as desired.
3. Either restart your server, or type "/co reload" in-game.

Secondary configuration files override the value specified in config.yml. If you leave an option out of a secondary configuration file, then the option specified in config.yml will be used.

#### Examples
* If you'd like to disable all logging for the End, copy the `config.yml` file to `world_the_end.yml` (matching the folder name for the world). Then, simply disable all logging options within the new file.
* If you just want to disable entity death logging in the Nether, but keep all other logging options the same, simply create a file named `world_nether.yml` containing the text "rollback-entities: false".

## Disabling Logging

To disable logging for specific users, blocks or commands, simply do the following:

1. In the CoreProtect plugin directory, create a file named `blacklist.txt`.
2. Enter the names of the users, commands, blocks, or entities you'd like to disable logging for (each usentry ername on a new line).
3. Either restart your server, or type "/co reload" in-game.

The blacklist supports disabling logs for:
- Users, which includes Players and non-player users, such as "#creeper"
- Commands, such as `/help`
- Blocks, such as minecraft:stone. *Please note that you must include the namespace (e.g. minecraft:). Only `block` actions are affected*
- Entities, such as minecraft:creeper. *Please note that you must include the namespace.*
- Filters can also be specified for a particular user, by use of the @ symbol after the specific item id. This is especially useful for farms where the same non-player user interacts with the same block. *This will filter the `block`, `kill`, `item` and `container` actions*. 

An example blacklist.txt file would look like this:

```text
Notch
#creeper
/help
minecraft:stone
minecraft:creeper
minecraft:shears@#dispenser
```

Comments can be added with the `%` at the start of a line.

*Please note that to disable logging for blocks, CoreProtect v23+ is required.*
*To disable logging for item pickups, entities CoreProtect v23.4+ is required.*
*To enable more complex per user filters, CoreProtect v23.4+ is required.*