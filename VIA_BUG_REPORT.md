# Bug report — ViaBackwards

**File at:** https://github.com/ViaVersion/ViaBackwards/issues/new
**Labels:** `bug`

---

## Title

```
1.17.1 clients disconnect with "Invalid token" on first chunk packet — BlockItemPacketRewriter1_18 chunk split fails across all tested versions (5.8.0 → 5.9.2-SNAPSHOT)
```

---

## Body

### Summary

When a 1.17.1 client connects through Velocity to a backend running Minecraft 1.21.11
(protocol 775), `BlockItemPacketRewriter1_18.lambda$registerPackets$0` throws
`IllegalArgumentException: Invalid token` while sending the split secondary packet that the
1.18 → 1.17.1 chunk rewriter creates. The client is immediately kicked with
*"An internal error occurred in your connection."* The first chunk packet from the backend
never reaches the client.

This reproduces 100% of the time and is **not a regression in 5.9.2-SNAPSHOT** — every
stable version we tested (5.8.0, 5.9.0, 5.9.1) and the snapshot all fail at the same
function. The line number shifts (5.8.0 → `:243`, 5.9.0/5.9.1/5.9.2-SNAPSHOT → `:233`)
because of an inter-version refactor, but the failing call site is the same lambda.

### Environment

| Component | Version |
|-----------|---------|
| Proxy | Velocity `3.5.0-SNAPSHOT` (git `9c0c9b02-b595` and `747cc898-b578` — both fail identically) |
| ViaVersion | 5.8.0 ❌ / 5.9.0 ❌ / 5.9.1 ❌ / 5.9.2-SNAPSHOT ❌ |
| ViaBackwards | 5.8.0 ❌ / 5.9.0 ❌ / 5.9.1 ❌ / 5.9.2-SNAPSHOT ❌ |
| Backend | Minestom `2026.05.11-1.21.11` (also reproduced on `2026.03.25-1.21.11`) |
| Backend protocol | 775 (Minecraft 1.21.11) — ViaVersion reports *"Highest supported version by the proxy: 26.1-26.1.2 (775)"* at boot |
| Client | 1.17.1 (a vanilla 1.18+ client connects fine — only the 1.17.1 path through `v1_18to1_17_1` fails) |
| Java | OpenJDK 25 |

`plugins/viaversion/config.yml` mostly defaults. Tried both
`velocity-servers.lobby: 774` (the auto-saved value from previous backend) and `775` (the
real current backend protocol) — both produce the identical stack.

### Reproduction (minimal)

1. Velocity `3.5.0-SNAPSHOT` proxy with ViaVersion + ViaBackwards 5.9.1 in `plugins/`.
2. Backend: Minestom 1.21.11.
3. Client on 1.17.1 connects through the proxy. (LimboAuth pre-auth passes cleanly.)
4. The moment `BackendPlaySessionHandler` forwards the backend's first chunk packet, Via's
   pipeline throws and the client is dropped.

### Stack trace (ViaVersion / ViaBackwards 5.9.1, Velocity `git-9c0c9b02-b595`)

```
[17:26:41] [.../ERROR] LPVania (...): exception encountered in ClientPlaySessionHandler
io.netty.handler.codec.EncoderException: java.lang.IllegalArgumentException: Invalid token
    at io.netty.handler.codec.MessageToMessageEncoder.write(MessageToMessageEncoder.java:107)
    [... velocity netty pipeline ...]
    at com.velocitypowered.proxy.connection.backend.BackendPlaySessionHandler.handleUnknown(BackendPlaySessionHandler.java:471)
    at com.velocitypowered.proxy.connection.MinecraftConnection.channelRead(MinecraftConnection.java:163)
    [...]
    at com.viaversion.viaversion.connection.UserConnectionImpl.sendRawPacketNow(UserConnectionImpl.java:195)
    at com.viaversion.viaversion.connection.UserConnectionImpl.sendRawPacket(UserConnectionImpl.java:179)
    at com.viaversion.viaversion.connection.UserConnectionImpl.sendRawPacket(UserConnectionImpl.java:169)
    at com.viaversion.viaversion.protocol.packet.PacketWrapperImpl.sendNow(PacketWrapperImpl.java:318)
    at com.viaversion.viaversion.protocol.packet.PacketWrapperImpl.send0(PacketWrapperImpl.java:309)
    at com.viaversion.viaversion.protocol.packet.PacketWrapperImpl.send(PacketWrapperImpl.java:294)
    at com.viaversion.viaversion.api.protocol.packet.PacketWrapper.send(PacketWrapper.java:192)
    at com.viaversion.viabackwards.protocol.v1_18to1_17_1.rewriter.BlockItemPacketRewriter1_18.lambda$registerPackets$0(BlockItemPacketRewriter1_18.java:233)
    at com.viaversion.viaversion.api.protocol.AbstractProtocol.transform(AbstractProtocol.java:455)
    at com.viaversion.viaversion.protocol.packet.PacketWrapperImpl.apply(PacketWrapperImpl.java:452)
    at com.viaversion.viaversion.protocol.ProtocolPipelineImpl.transform(ProtocolPipelineImpl.java:110)
    at com.viaversion.viaversion.connection.UserConnectionImpl.transform(UserConnectionImpl.java:359)
    at com.viaversion.viaversion.connection.UserConnectionImpl.transformClientbound(UserConnectionImpl.java:335)
    at com.viaversion.viaversion.api.connection.UserConnection.transformIncoming(UserConnection.java:285)
    at com.viaversion.viaversion.platform.ViaDecodeHandler.decode(ViaDecodeHandler.java:54)
Caused by: java.lang.IllegalArgumentException: Invalid token
    at com.viaversion.viaversion.connection.UserConnectionImpl.transform(UserConnectionImpl.java:351)
    at com.viaversion.viaversion.connection.UserConnectionImpl.transformClientbound(UserConnectionImpl.java:335)
    at com.viaversion.viaversion.api.connection.UserConnection.transformOutgoing(UserConnection.java:273)
    at com.viaversion.viaversion.platform.ViaEncodeHandler.encode(ViaEncodeHandler.java:54)
    at com.viaversion.viaversion.platform.ViaEncodeHandler.encode(ViaEncodeHandler.java:31)
    at io.netty.handler.codec.MessageToMessageEncoder.write(MessageToMessageEncoder.java:90)
    ... 57 more
```

### Stack trace (ViaVersion / ViaBackwards 5.8.0)

Identical, line number shifted by the refactor:

```
at com.viaversion.viabackwards.protocol.v1_18to1_17_1.rewriter.BlockItemPacketRewriter1_18.lambda$registerPackets$0(BlockItemPacketRewriter1_18.java:243)
```

### Analysis

Looking at the call site:

* `lambda$registerPackets$0` is the **first** registered packet handler in
  `BlockItemPacketRewriter1_18`, which is the `LEVEL_CHUNK_WITH_LIGHT` handler.
* That handler splits the 1.18 chunk packet into a chunk + a secondary `LIGHT_UPDATE`
  packet and calls `secondaryWrapper.send(...)` (the line that fails: `:233` in 5.9.1).
* `PacketWrapper.send(...)` re-enters the pipeline, hits
  `UserConnectionImpl.transform(...)`, and that throws `IllegalArgumentException("Invalid token")`
  at line 351 (5.9.1).

The "Invalid token" is rejecting the synthetic secondary packet that the rewriter just
created. Either the validation in `UserConnectionImpl.transform` is wrong about a legitimate
state/type, or the rewriter is producing a wrapper without the field it expects.

Plain Vanilla 1.21.11 servers with the same proxy + Via versions translate to 1.17.1
clients fine in other reports we found — so this seems specific to **Minestom-shaped chunk
packets** for 1.21.11. The Minestom team would likely engage if you ask.

### What we tried (does not fix)

* All four ViaBackwards / ViaVersion combinations above.
* Two Velocity 3.5.0-SNAPSHOT builds (different git hashes from the host's auto-update).
* Two Minestom backend builds (`2026.03.25-1.21.11`, `2026.05.11-1.21.11`).
* `velocity-servers.lobby` = `774` and `775`.
* Restarting Velocity cleanly between every change.

### Expected behavior

1.17.1 client receives the chunk + the secondary `LIGHT_UPDATE` and continues into the
world, as it does for a vanilla 1.21.11 backend with the same proxy.

### Offers

* Full `latest.log` from any of the failing builds — can paste or DM.
* Full ViaVersion / ViaBackwards config files.
* Willing to test a debug build / snapshot patch on the same setup quickly.
* Can also stand up a minimal Minestom reproducer outside our project if it helps.

---

## (для тебя) кратко по-русски

1. Идёшь на https://github.com/ViaVersion/ViaBackwards/issues/new
2. В Title — строку из блока `Title` сверху.
3. В описание — весь блок `Body` (от Summary до Offers, включая таблицу с матрицей и оба стека). Markdown поддерживается, копи целиком.
4. К issue прикрепи файл `latest.log` (он у меня в `/tmp/lobby-proxy/latest-after-config.log` — скину если попросишь).
5. Когда maintainers ответят — у нас есть полный доступ к прокси и бэкенду, можем быстро тестить любые их предложения.

Этот репорт **нельзя отмахнуть как «ты на dev-сборке»** — там в матрице 3 STABLE релиза подряд, разные Velocity-билды, разные Minestom-билды, два варианта конфига. Maintainers либо признают баг и поправят, либо скажут конкретное место копать (может Minestom-баг с light data). Оба исхода — прогресс.
