# Networking

CoreProtect Networking allows also clients to receive data using the Packets.

| Networking Details      |        |
|-------------------------|--------|
| **Networking Version:** | 1      |
| **Plugin Version:**     | v21.2+ |

##Packets

The server will not respond unless the player has the correct permissions, which is `coreprotect.register` and the relevant command permissions

## Server to Client

### Data Packet
Sends Data From The Database.

Channel: `coreprotect:data`

Buf Content:

Info: To read you need to read out correctly, you will need to get `written bytes` and put it inside an byteArrayInputStream

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

Channel: `coreprotect:handshake`

Buf Content:

Registered: `Boolean`

## Client to Server

### Handshake Packet
Sends handshake to register

Channel: `coreprotect:handshake`

Buf Content:

Mod Version: `UTF`
Mod Id: `UTF`
Coreprotect Protocol: `Int`

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