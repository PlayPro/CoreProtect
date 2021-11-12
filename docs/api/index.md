# CoreProtect API

The CoreProtect API enables you to log your own block changes, perform lookups, rollbacks, restores, and more. 

| API Details |  |
| --- | --- |
| **API Version:** | 8 |
| **Plugin Version:** | v20.1+ |
| **Maven:** | [maven.playpro.com](https://maven.playpro.com) |

*Documentation for the previous API version can be found [here](https://www.minerealm.com/community/viewtopic.php?f=32&t=16687).*

---

## Upgrading from API v7

The changes from the previous API version are as follows:

- The following methods have been added:
```java
// CoreProtectAPI
List<String[]> blockLookup(Block block)

List<String[]> containerLookup(Block block, long time)

List<String[]> containerLookup(Block block)

List<BlockLookupResults> blockLookupParsed(Block block, long time)

List<BlockLookupResults> blockLookupParsed(Block block)

List<ContainerLookupResults> containerLookupParsed(Block block, long time)

List<ContainerLookupResults> containerLookupParsed(Block block)

BlockLookupResults parseBlockLookupResults(String[] results)

ContainerLookupResults parseContainerLookupResult(String[] results)

boolean hasPlaced(String user, Block block)

boolean hasRemoved(String user, Block block)

boolean hasPlaced(LivingEntity entity, Block block)

boolean hasPlaced(LivingEntity entity, Block block, long time, long offset)

boolean hasRemoved(LivingEntity entity, Block block, long time, long offset)

boolean hasRemoved(LivingEntity entity, Block block)

boolean hasRemoved(LivingEntity entity, BlockLookupResults result)

boolean hasPlaced(LivingEntity entity, BlockLookupResults result)

boolean hasPlaced(String user, BlockLookupResults result)

boolean hasRemoved(String user, BlockLookupResults result)
        
// ParseResult
String getEntity()

long getTimeLong()

Block getBlock()

Location getLocation()

World getWorld()

// BlockLookupResults
boolean hasRemoved(String entity)

boolean hasPlaced(String entity)

boolean hasRemoved(LivingEntity entity)

boolean hasPlaced(LivingEntity entity)
        
// ContainerLookupResults
Inventory getInventory()

ItemStack getItem()

int getAmount()
``` 

---

- The following methods have been deprecated:
```java
// ParseResult
String getPlayer()
``` 

---

- The following methods have been changed: </br>
  **All methods have time in arguments, instead of `int` are now `long` type**
```java
String worldName() -> String getWorldName()

ParseResult parseResult(String[] result) ->
        BlockLookupResults parseBlockLookupResults(String[] results)
        // OR
        ContainerLookupResults parseContainerLookupResult(String[] results)

BlockData ParseResult.getBlockData() -> BlockData BlockLookupResults.getBlockData()
``` 

---

- The following methods have been removed: </br>
```java
ParseResult parseResult(String[] result)
``` 

---

- The following classes have been added:

[BlockLookupResults.class](#blocklookupresults-extends-parseresult) </br>
[ContainerLookupResults.class](#containerlookupresults-extends-parseresult)

---

## Getting Started

Ensure you're using CoreProtect 20.1 or higher. Add it as an external jar to your plugin in your IDE.  
Alternatively, if using Maven, you can add it via the repository [https://maven.playpro.com](https://maven.playpro.com) (net.coreprotect, 20.1).

The first thing you need to do is get access to CoreProtect. You can do this by using code similar to the following:

```java
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;

private CoreProtectAPI getCoreProtect() {
        Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");
     
        // Check that CoreProtect is loaded
        if (plugin == null || !(plugin instanceof CoreProtect)) {
            return null;
        }

        // Check that the API is enabled
        CoreProtectAPI CoreProtect = ((CoreProtect) plugin).getAPI();
        if (CoreProtect.isEnabled() == false) {
            return null;
        }

        // Check that a compatible version of the API is loaded
        if (CoreProtect.APIVersion() < 8) {
            return null;
        }

        return CoreProtect;
}
``` 

With this code, you can then access the API with a call like the following:

```java
CoreProtectAPI api = getCoreProtect();
if (api != null) { // Ensure we have access to the API
    api.testAPI(); // Will print out "[CoreProtect] API test successful." in the console.
}
```

Yay, you're now using the CoreProtect API!

---

## API Overview

### Available Methods

___

#### CoreProtectAPI

```java
boolean isEnabled()

void testAPI()

List<String[]> performLookup(long time, List<String> restrict_users, List<String> exclude_users, List<Object> restrict_blocks, List<Object> exclude_blocks, List<Integer> action_list, int radius, Location radius_location)
    
List<String[]> performRollback(long time, List<String> restrict_users, List<String> exclude_users, List<Object> restrict_blocks, List<Object> exclude_blocks, List<Integer> action_list, int radius, Location radius_location)
    
List<String[]> performRestore(long time, List<String> restrict_users, List<String> exclude_users, List<Object> restrict_blocks, List<Object> exclude_blocks, List<Integer> action_list, int radius, Location radius_location)
        
List<String[]> blockLookup(Block block, long time)
        
List<String[]> blockLookup(Block block)

List<String[]> containerLookup(Block block, long time)

List<String[]> containerLookup(Block block)

List<BlockLookupResults> blockLookupParsed(Block block, long time)
        
List<BlockLookupResults> blockLookupParsed(Block block)

List<ContainerLookupResults> containerLookupParsed(Block block, long time)
        
List<ContainerLookupResults> containerLookupParsed(Block block)

BlockLookupResults parseBlockLookupResults(String[] result)

ContainerLookupResults parseContainerLookupResult(String[] result)

boolean logChat(Player player, String message)

boolean logCommand(Player player, String command)

boolean logPlacement(String user, Location location, Material type, BlockData blockData)

boolean logRemoval(String user, Location location, Material type, BlockData blockData)

boolean logContainerTransaction(String user, Location location)

boolean logInteraction(String user, Location location)

boolean hasPlaced(String user, Block block, long time, int offset)

boolean hasRemoved(String user, Block block, long time, int offset)

void performPurge(long time)
``` 
___
#### ParseResult

```java
int getActionId()

String getActionString()

int getData()

String getEntity()
        
long getTimeLong()

long getTimestamp()

Material getType()

boolean isRolledBack()

int getX()

int getY()

int getZ()

Location getLocation()

String getWorldName()

World getWorld()

Block getBlock()
```

___
#### BlockLookupResults extends ParseResult

```java
BlockData getBlockData()

boolean hasRemoved(String entity)

boolean hasPlaced(String entity)

boolean hasRemoved(LivingEntity entity)

boolean hasPlaced(LivingEntity entity)
```

___
#### ContainerLookupResults extends ParseResult

```java
Inventory getInventory()

ItemStack getItem()

int getAmount()
```

---

### Available Events

*The following events are emitted by CoreProtect.*

#### CoreProtectPreLogEvent

Fired when a CoreProtect logger is about to log an action. Not cancellable.

| Property | Description | Mutable |
| --- | --- | --- |
| User | The name of the user under which this action will be logged. | Yes |

---

### Method Usage

*Detailed method information is listed below.*

#### `isEnabled()`

Calling this will return true if the server has the CoreProtect API enabled, and false if it does not.

---

#### `testAPI()`

Running this will print out "[CoreProtect] API Test Successful." in the server console.

---

#### `performLookup(long time, List<String> restrict_users, List<String> exclude_users, List<Object> restrict_blocks, List<Object> exclude_blocks, List<Integer> action_list, int radius, Location radius_location)`

This will perform a lookup.

* **time:** Specify the amount of time to search back. "5" would return results from the last 5 seconds.
* **restrict_users:** Specify any usernames to perform the lookup on. Can be set to "null" if both a radius and a location are specified.
* **exclude_users:** Specify any usernames to exclude from the lookup. Can be set to "null".
* **restrict_blocks:** Specify a list of EntityType's or Material's to restrict the search to. Can be set to "null".
* **exclude_blocks:** Specify a list of EntityType's or Material's to exclude from the search. Can be set to "null".
* **action_list:** Specify a list of action types to restrict the search to. Can be set to "null"
* **radius:** Specify a radius to restrict the search to. A location must be specified if using this. Set to "0" to disable.
* **radius_location:** Specify a location to search around. Can be set to "null" if no radius is specified, and a user is specified.

---

#### `performRollback(long time, List<String> restrict_users, List<String> exclude_users, List<Object> restrict_blocks, List<Object> exclude_blocks, List<Integer> action_list, int radius, Location radius_location)`

This will perform a rollback. Method must be called async.

* **time:** Specify the amount of time to rollback. "5" would return results from the last 5 seconds.
* **restrict_users:** Specify any usernames to perform the rollback on. Can be set to "null" if both a radius and a location are specified.
* **exclude_users:** Specify any usernames to exclude from the rollback. Can be set to "null".
* **restrict_blocks:** Specify a list of EntityType's or Material's to restrict the rollback to. Can be set to "null".
* **exclude_blocks:** Specify a list of EntityType's or Material's to exclude from the rollback. Can be set to "null".
* **action_list:** Specify a list of action types to restrict the rollback to. Can be set to "null"
* **radius:** Specify a radius to restrict the rollback to. A location must be specified if using this. Set to "0" to disable.
* **radius_location:** Specify a location to rollback around. Can be set to "null" if no radius is specified, and a user is specified.

---

#### `performRestore(long time, List<String> restrict_users, List<String> exclude_users, List<Object> restrict_blocks, List<Object> exclude_blocks, List<Integer> action_list, int radius, Location radius_location)`

This will perform a restore.

* **time:** Specify the amount of time to restore. "5" would return results from the last 5 seconds.
* **restrict_users:** Specify any usernames to perform the restore on. Can be set to "null" if both a radius and a location are specified.
* **exclude_users:** Specify any usernames to exclude from the restore. Can be set to "null".
* **restrict_blocks:** Specify a list of EntityType's or Material's to restrict the restore to. Can be set to "null".
* **exclude_blocks:** Specify a list of EntityType's or Material's to exclude from the restore. Can be set to "null".
* **action_list:** Specify a list of action types to restrict the restore to. Can be set to "null"
* **radius:** Specify a radius to restrict the restore to. A location must be specified if using this. Set to "0" to disable.
* **radius_location:** Specify a location to restore around. Can be set to "null" if no radius is specified, and a user is specified.

---

#### `List<String[]> blockLookup(Block block, long time)`

This will perform a full lookup on a single block.

* **block:** The block to perform the lookup on.
* **time:** Specify the amount of time to lookup. "5" would return results from the last 5 seconds.

---

#### `List<String[]> blockLookup(Block block)`

This will perform a full lookup on a single block.

* **block:** The block to perform the lookup on.
---

#### `List<String[]> containerLookup(Block block, long time)`

This will perform a full lookup on a single block with inventory (container).

* **block:** The block to perform the lookup on.
* **time:** Specify the amount of time to lookup. "5" would return results from the last 5 seconds.

---

#### `List<BlockLookupResults> blockLookupParsed(Block block)`

This will perform a full lookup on a single block.

* **block:** The block to perform the lookup on.
---

#### `List<BlockLookupResults> blockLookupParsed(Block block, long time)`

This will perform a full lookup on a single block.

* **block:** The block to perform the lookup on.
* **time:** Specify the amount of time to lookup. "5" would return results from the last 5 seconds.

---

#### `List<BlockLookupResults> blockLookupParsed(Block block)`

This will perform a full lookup on a single block.

* **block:** The block to perform the lookup on.
---

#### `List<ContainerLookupResults> containerLookupParsed(Block block, long time)`

This will perform a full lookup on a single block with inventory (container).

* **block:** The block to perform the lookup on.
* **time:** Specify the amount of time to lookup. "5" would return results from the last 5 seconds.

---

#### `List<ContainerLookupResults> containerLookupParsed(Block block)`

This will perform a full lookup on a single block with inventory (container).

* **block:** The block to perform the lookup on.

---

#### `BlockLookupResults parseBlockLookupResults(String[] result)`

This will parse results from a lookup. You'll then be able to view the [following](#blocklookupresults-extends-parseresult)

---

#### `ContainerLookupResults parseContainerLookupResult(String[] result)`

This will parse results from a lookup. You'll then be able to view the [following](#containerlookupresults-extends-parseresult)

---

#### `logPlacement(String user, Location location, Material type, BlockData blockData)`

This will log a block as being placed.

* **user:** Specify the username to log as having placed the block.
* **location:** Specify the location of the block you're logging.
* **type:** Specify the Material of the block you're logging.
* **blockData:** Specify the BlockData of the block you're logging. Can be set to "null".

---

#### `logRemoval(String user, Location location, Material type, BlockData blockData)`

This will log a block as being removed/broken, and will log the block's inventory (if applicable).

* **user:** Specify the username to log as having removed the block.
* **location:** Specify the location of the block you're logging.
* **type:** Specify the Material of the block you're logging.
* **blockData:** Specify the BlockData of the block you're logging. Can be set to "null".

---

#### `logContainerTransaction(String user, Location location)`

This will log any transactions made to a block's inventory immediately after calling the method.

* **user:** Specify the username to log as having added/removed the items.
* **location:** Specify the location of the block inventory you're logging.

---

#### `logInteraction(String user, Location location)`

This will log a block as having been interacted with.

* **user:** Specify the username to log as having caused the interaction.
* **location:** Specify the location of the interaction you're logging.

---

#### `hasPlaced(String user, Block block, long time, int offset)`

This will return true if a user has already placed a block at the location within the specified time limit.

* **user:** The username you're checking to see if they've placed a block already.
* **block:** The block you're checking.
* **time:** How far back to check. "5" would only check through the last 5 seconds of logged blocks.
* **offset:** A time offset. "2" would ignore the last 2 seconds of most recently ignored data. (0=no offset)

---

#### `hasPlaced(String user, Block block)`

This will return true if a user has already placed a block at the location.

* **user:** The username you're checking to see if they've placed a block already.
* **block:** The block you're checking.

---

#### `hasPlaced(String user, BlockLookupResults result)`

This will return true if the user has placed a block in the given result.

* **user:** The username you're checking to see if they've placed a block already.
* **result:** The lookup result.

---

#### `hasRemoved(String user, Block block, long time, int offset)`

This will return true if a user has already removed a block at the location within the specified time limit.

* **user:** The username you're checking to see if they've removed a block already.
* **block:** The block you're checking.
* **time:** How far back to check. "5" would only check through the last 5 seconds of logged blocks.
* **offset:** A time offset. "2" would ignore the last 2 seconds of most recently ignored data. (0=no offset)

---

#### `hasRemoved(String user, Block block)`

This will return true if a user has already removed a block at the location.

* **user:** The username you're checking to see if they've removed a block already.
* **block:** The block you're checking.

---

#### `hasRemoved(String user, BlockLookupResults result)`

This will return true if the user has removed a block in the given result.

* **user:** The username you're checking to see if they've removed a block already.
* **block:** The lookup result.

---

#### `hasPlaced(LivingEntity entity, Block block, long time, int offset)`

This will return true if an entity has already placed a block at the location within the specified time limit.

* **entity:** The entity you're checking to see if they've placed a block already.
* **block:** The block you're checking.
* **time:** How far back to check. "5" would only check through the last 5 seconds of logged blocks.
* **offset:** A time offset. "2" would ignore the last 2 seconds of most recently ignored data. (0=no offset)

---

#### `hasPlaced(LivingEntity entity, Block block)`

This will return true if an entity has already placed a block at the location.

* **entity:** The entity you're checking to see if they've placed a block already.
* **block:** The block you're checking.

---

#### `hasPlaced(LivingEntity entity, BlockLookupResults result)`

This will return true if the entity has placed a block in the given result.

* **entity:** The entity you're checking to see if they've placed a block already.
* **result:** The lookup result.

---

#### `hasRemoved(LivingEntity entity, Block block, long time, int offset)`

This will return true if an entity has already removed a block at the location within the specified time limit.

* **entity:** The entity you're checking to see if they've removed a block already.
* **block:** The block you're checking.
* **time:** How far back to check. "5" would only check through the last 5 seconds of logged blocks.
* **offset:** A time offset. "2" would ignore the last 2 seconds of most recently ignored data. (0=no offset)

---

#### `hasRemoved(LivingEntity entity, Block block)`

This will return true if an entity has already removed a block at the location.

* **entity:** The entity you're checking to see if they've removed a block already.
* **block:** The block you're checking.

---

#### `hasRemoved(LivingEntity entity, BlockLookupResults result)`

This will return true if the entity has removed a block in the given result.

* **entity:** The entity you're checking to see if they've removed a block already.
* **block:** The lookup result.

---

#### `performPurge(long time)`

This will perform a purge on the CoreProtect database.

* **time:** Purge any data earlier than this. "120" would purge any data older than 120 seconds (2 minutes).

---


### Examples

- Get the last 60 seconds of block data for the user "Notch".
```java
CoreProtectAPI co = getCoreProtect();
if (co != null) { // Ensure we have access to the API
  List<String[]> lookup = co.performLookup(60, Arrays.asList("Notch"), null, null, null, null, 0, null);
  if (lookup != null) {
    for (String[] result : lookup) {
      BlockLookupResults parseResult = co.parseBlockLookupResults(result);
      int x = parseResult.getX();
      int y = parseResult.getY();
      int z = parseResult.getZ();
      // ...
    }
  }
}
``` 

---

- Get the last 60 seconds of block data for the user "Notch", excluding dirt and grass blocks.
```java
CoreProtectAPI co = getCoreProtect();
if (co != null) { // Ensure we have access to the API
  List<Object> exclude = Arrays.asList(Material.DIRT, Material.GRASS);
  List<String[]> lookup = co.performLookup(60, Arrays.asList("Notch"), null, null, exclude, null, 0, null);
  if (lookup != null) {
    for (String[] value : lookup) {
      BlockLookupResults result = co.parseBlockLookupResults(value);
      int x = result.getX();
      int y = result.getY();
      int z = result.getZ();
      // ...
    }
  }
}
``` 

---

- Get the last 60 seconds of block data within 5 blocks of a location.
```java
CoreProtectAPI co = getCoreProtect();
if (co != null) { // Ensure we have access to the API
  List<String[]> lookup = co.performLookup(60, null, null, null, null, null, 5, location);
  if (lookup != null) {
    for (String[] value : lookup) {
      BlockLookupResults result = co.parseBlockLookupResults(value);
      int x = result.getX();
      int y = result.getY();
      int z = result.getZ();
      // ...
    }
  }
}
``` 

---

- Rollbacks / restores use the same code structure as the above examples. For example:
```java
class BasicThread implements Runnable {
  @Override
  public void run() {
    try {
      CoreProtectAPI co = getCoreProtect();
      if (co != null) { // Ensure we have access to the API
        List<String[]> lookup = co.performRollback(60, Arrays.asList("Notch"), null, null, null, null, 0, null);
        if (lookup != null) {
          for (String[] value : lookup) {
              BlockLookupResults result = co.parseBlockLookupResults(value);
            int x = result.getX();
            int y = result.getY();
            int z = result.getZ();
            // ...
          }
        }
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
``` 

---

- Check if the user "Notch" has already placed a block at a location within the last 60 seconds.
```java
CoreProtectAPI co = getCoreProtect();
if (co != null) { // Ensure we have access to the API
  boolean hasPlaced = co.HasPlaced("Notch", block, 60, 0);
}
``` 

---

- Get the last 60 seconds of block data for a specific block.
```java
CoreProtectAPI co = getCoreProtect();
if (co != null) { // Ensure we have access to the API
  List<String[]> lookup = co.blockLookup(block, 60);
  if (lookup != null) {
    for (String[] result : lookup) {
      BlockLookupResults parseResult = co.parseBlockLookupResults(value);
      int x = parseResult.getX();
      int y = parseResult.getY();
      int z = parseResult.getZ();
      // ...
    }
  }
}
``` 

---

- Log the placement of a block at a location by the user "Notch".
```java
CoreProtectAPI co = getCoreProtect();
if (co != null) { // Ensure we have access to the API
  boolean success = co.logPlacement("Notch", block.getLocation(), block.getType(), block.getData());
}
``` 

---

- Log adding/remove items in a chest (or some other block inventory).
```java
CoreProtectAPI co = getCoreProtect();
if (CoreProtect != null) { // Ensure we have access to the API
  boolean success = co.logContainerTransaction("Notch", inventory.getLocation());
  // modify your container contents immediately after (e.g. [i]inventory.addItem(itemStack);[/i])
}
``` 

---

- Perform a multi-threaded placement check to see if the user "Notch" has already placed a block at a location within the last 60 seconds. This ignores the most recent 1 second of logged data, to account for the fact that that new block data may have already been logged, depending on your code.
```java
final Block block = null; // Should be an actual block
class BasicThread implements Runnable {
  @Override
  public void run() {
    try {
      CoreProtectAPI co = getCoreProtect();
      if (co != null){ // Ensure we have access to the API
        boolean hasPlaced = co.hasPlaced("Notch", block, 60, 1);
      }
    }
    catch (Exception e){
      e.printStackTrace(); 
    }
  }
}
Runnable runnable = new BasicThread();
Thread thread = new Thread(runnable);
thread.start();
``` 

---