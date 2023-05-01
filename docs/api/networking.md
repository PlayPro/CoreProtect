# Networking API

The CoreProtect Networking API allows clients to receive data using packets.

| Networking Details      |        |
|-------------------------|--------|
| **Networking Version:** | 1      |
| **Plugin Version:**     | v21.3+ |

---

## Packets

The server will not respond unless the player has the correct permission, which is `coreprotect.networking`.

---

## Server to Client

### Data Packet
Sends data from the database.

* Channel: `coreprotect:data`

| Type: `Int` | 1                      | 2                      | 3                  | 4                  |
|-------------|------------------------|------------------------|--------------------|--------------------|
|             | Time: `long`           | Time: `long`           | Time: `long`       | Time: `long`       |
|             | Phrase selector: `UTF` | Phrase selector: `UTF` | Result User: `UTF` | Result User: `UTF` |
|             | Result User: `UTF`     | Result User: `UTF`     | Message: `UTF`     | Target: `UTF`      |
|             | Target: `UTF`          | Amount: `Int`          | Sign: `Boolean`    |                    |
|             | Amount: `Int`          | X: `Int`               | X: `Int`           |                    |
|             | X: `Int`               | Y: `Int`               | Y: `Int`           |                    |
|             | Y: `Int`               | Z: `Int`               | Z: `Int`           |                    |
|             | Z: `Int`               | World name: `UTF`      | World name: `UTF`  |                    |
|             | World name: `UTF`      |                        |                    |                    |
|             | Rolledback: `Boolean`  |                        |                    |                    |
|             | isContainer: `Boolean` |                        |                    |                    |
|             | Added: `Boolean`       |                        |                    |                    |
|             | Tooltip: `UTF`         |                        |                    |                    |

Example (Fabric):
```
ByteArrayInputStream in = new ByteArrayInputStream(buf.getWrittenBytes());
DataInputStream dis = new DataInputStream(in);
int type = dis.readInt();
long time = dis.readLong();
String selector = dis.readUTF();
String  resultUser = dis.readUTF();
String target = dis.readUTF();
int amount = dis.readInt();
int x = dis.readInt();
int y = dis.readInt();
int z = dis.readInt();
String worldName = dis.readUTF();
boolean rolledback = dis.readBoolean();
boolean isContainer = dis.readBoolean();
boolean added = dis.readBoolean();
String tooltip = dis.readUTF();
```

### Handshake Packet
Sends handshake if player is registered.

* Channel: `coreprotect:handshake`
* Registered: `Boolean`
* Actions:
  * Total results: `Int`
  * Item: `UTF`
* Worlds:
  * Total results: `Int`
  * Item: `UTF`
* Version: `UTF`

Example (Fabric):
```
ByteArrayInputStream in = new ByteArrayInputStream(buf.getWrittenBytes());
DataInputStream dis = new DataInputStream(in);
boolean coreprotectRegistered = dis.readBoolean();
int total = dis.readInt();
List<String> list = new ArrayList<>();
for (int i = 0; i < total; i++)
{
    list.add(dis.readUTF());
}
List<String> actions = list;
total = dis.readInt();
list = new ArrayList<>();
for (int i = 0; i < total; i++)
{
    list.add(dis.readUTF());
}
List<String> worlds = list;
String version = dis.readUTF();
```

### Response Packet
Sends Responses.

When the type is coreprotect:lookupPage, the message will contain "<nextPageNumber>/<totalPages>,<isNetworkCommand>"
When the type is coreprotect:lookupBusy, the message will contain the command that got sent previously that you can sent to try again.
Other messages will be feedback messages.

* Channel: `coreprotect:response`
* Type: `UTF`
* Message: `UTF`

Example (Fabric):
```
ByteArrayInputStream in = new ByteArrayInputStream(buf.getWrittenBytes());
DataInputStream dis = new DataInputStream(in);
String type = dis.readUTF();
String message = dis.readUTF();
```

---

## Client to Server

### Handshake Packet
Sends handshake to register

* Channel: `coreprotect:handshake`  
* Mod Version: `UTF`  
* Mod Id: `UTF`   
* CoreProtect Protocol: `Int`

Example (Fabric):
```
PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
DataOutputStream msgOut = new DataOutputStream(msgBytes);
msgOut.writeUTF(modVersion);
msgOut.writeUTF(modId);
msgOut.writeInt(coreprotectProtocol);
packetByteBuf.writeBytes(msgBytes.toByteArray());
```

### Input Packet
Sends input to execute commands on server

* Channel: `coreprotect:input`
* Command Arguments: `UTF`
* Total pages to send for lookup: `Int`
* Amount of Rows for lookup: `Int`

Example of Command Arguments:
```
lookup r:world r:15 t:33d rows:20
```

Example (Fabric):
```
PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
DataOutputStream msgOut = new DataOutputStream(msgBytes);
msgOut.writeUTF(coreProtectSearch.getSearchData());
msgOut.writeInt(pages);
msgOut.writeInt(amountRows);
packetByteBuf.writeBytes(msgBytes.toByteArray());
```

---

## Command Connected Users

### /co networking
Allows you to view who is connected using the networking API if you have the correct permissions.

**Example**  
`/co networking`

---

## Debugging

### /co network-debug
Allows you to debug the networking API if you are registered and have correct permissions.  
To utilize the command, `network-debug: true` must be set in the CoreProtect `config.yml`.

**Example**  
`/co network-debug <type>`

___