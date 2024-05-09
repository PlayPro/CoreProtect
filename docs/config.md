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
2. Enter the names of the users (or commands) you'd like to disable logging for (each username on a new line).
3. Either restart your server, or type "/co reload" in-game.

This can be used to disable logging for non-player users, such as "#creeper". For example, if you'd like to disable logging for the user "Notch", TNT explosions, stone blocks, and the "/help" command, the blacklist.txt file would look like this:
```text
Notch
#tnt
/help
minecraft:stone
```

*Please note that to disable logging for blocks, CoreProtect v23+ is required, and you must include the namespace. For example, to disable logging for dirt, you must add it as "minecraft:dirt".*