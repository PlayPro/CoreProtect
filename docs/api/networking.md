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
```

### Handshake Packet
Sends handshake if player is registered.

* Channel: `coreprotect:handshake`
* Registered: `Boolean`

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

---

## Debugging

### /co network-debug
Allows you to debug the networking API if you are registered and have correct permissions.  
To utilize the command, `network-debug: true` must be set in the CoreProtect `config.yml`.

**Example**  
`/co network-debug <type>`

___