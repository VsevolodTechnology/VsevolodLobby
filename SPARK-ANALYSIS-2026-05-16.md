# Spark Profile Analysis — 2026-05-16

Source: <https://spark.lucko.me/Gvl6xqQOlB> · payload `application/x-spark-sampler` 505 KB

Decoded locally from the protobuf payload (no proper viewer access on this machine) — all numbers below come from walking the binary; the Spark UI may format them differently. **Read the "Caveat" section first** — this profile captured a near-idle server.

---

## Caveat — what this profile is and is not

| Metric | Value | Reading |
|---|---|---|
| Profile span | **268.36 s** (≈4 min 28 s) | sample window |
| Ticks counted | **5 328** | 19.86 ticks/s avg |
| TPS samples | **19.9999 · 20.0002 · 20.0001 · 20.0000** | no drops, ever |
| MSPT | **empty** in the payload | server too idle to register |
| Process CPU avg | **25.8 %** | mostly idle |
| Process CPU max | **14.3 % recent**, p99 0.40 % | one workload burst, otherwise nothing |
| `Ms-TickScheduler.waitUntilNextTick` | **237.52 s** of 240 s | tick thread asleep 98.7 % of the time |
| Young-gen GC | **6 collections**, avg pause 20.5 ms | within budget, healthy |
| Concurrent GC | **4 collections**, avg 2.75 ms | healthy |
| Old-gen GC | **0** | no full GCs in the window — no signs of leak pressure |

**This profile does not show the lobby under real player load.** Tick work is barely visible because there is essentially none happening. You should:

1. Take a second profile of ≥3 minutes with a representative player count and at least one active parkour run, then compare.
2. Use `/spark profiler start --thread *` so non-tick worker pools (FJ pool, virtual threads, network) show up — this one is single-threaded.

That said, the bootstrap path and the background chunk-loader work *did* run during the capture, so several real issues are visible.

---

## What we can see — ranked by impact

### 1. NpcSkinResolver blocked the entire boot on Mojang HTTP — **CONFIRMED**
> *Already flagged as HIGH-08 in the previous audit; spark proves it.*

```
0.73 s  MojangUtils.retrieve          (HTTP GET to api.mojang.com)
0.73 s  PlayerSkin.fromUsername
0.73 s  NpcSkinResolver.resolveSync
0.98 s  NpcManager.spawn              (parent — includes the HTTP wait)
0.98 s  NpcManager.onConfigApplied    (parent)
1.15 s  LobbyModule.load              (parent — bootstrap blocked here)
1.27 s  ServerBootstrap.bootstrap     (parent — total bootstrap)
```

**The Mojang call accounts for 57 % of bootstrap wall time** (0.73 s of 1.27 s). With more username-based NPCs this scales linearly. On a cold cache or a slow Mojang day this is the single biggest startup risk.

**Fix:** route plain-username skins through the same async path as `url:` specs. Spawn the NPC immediately with no custom skin; call `LobbyNpc.updateSkin` when the resolver returns. Already drafted in `AUDIT-2026-05-16.md` as HIGH-08.

```java
// NpcSkinResolver.resolveSync — treat plain-username the same as "url:" specs
if (looksLikeUsername(s)) return null; // → caller takes the async path

// Add resolveAsync username branch:
Thread.startVirtualThread(() -> {
    PlayerSkin skin = PlayerSkin.fromUsername(s);   // off the tick / boot thread
    if (skin != null) { CACHE.put(spec, skin); onResolved.accept(skin); }
});
```

### 2. AnvilLoader is running on parkour instances — **NEW**
```
0.30 s  AnvilLoader.loadMCA           (looking for non-existent .mca files)
0.27 s  AnvilLoader.loadChunk
0.18 s  RegionFile.readChunkData
0.10 s  AnvilLoader.loadSections
0.02 s  AnvilLoader.getMCAFile
```

`ParkourService.start` creates the instance with `instanceManager.createInstanceContainer(DimensionType.OVERWORLD)` — no `ChunkLoader` argument, so Minestom defaults to `AnvilLoader`. Even though the parkour world is purely generator-driven, every chunk request first asks AnvilLoader to look for a saved `.mca` file. We waste 0.5+ s of FJ-pool CPU per parkour run probing for files that will never exist, plus extra filesystem syscalls.

**Fix:** pass an explicit no-op `ChunkLoader` when creating the parkour instance, so chunk retrieval goes straight to the generator.

```java
// ParkourService.start
InstanceContainer instance = instanceManager.createInstanceContainer(
        DimensionType.OVERWORLD,
        NoopChunkLoader.INSTANCE          // ← skip the disk probe entirely
);
instance.setGenerator(unit -> { ... });
```

```java
public final class NoopChunkLoader implements IChunkLoader {
    public static final NoopChunkLoader INSTANCE = new NoopChunkLoader();
    private NoopChunkLoader() {}
    @Override public CompletableFuture<Chunk> loadChunk(Instance i, int cx, int cz) {
        return CompletableFuture.completedFuture(null); // null => fall through to generator
    }
    @Override public CompletableFuture<Void> saveChunk(Chunk c) {
        return CompletableFuture.completedFuture(null);
    }
    @Override public CompletableFuture<Void> saveChunks(Collection<Chunk> chunks) {
        return CompletableFuture.completedFuture(null);
    }
}
```

> Verify the exact `IChunkLoader` interface name in your Minestom version — the precise method names changed across recent releases.

### 3. QR map renderer pre-init cost — **MITIGATED**
```
0.10 s  LobbyQrMapRenderer.renderPrettyQr
0.10 s  LobbyQrMapService.<clinit>
```

0.1 s of CPU at class load. Previously this fired on the first join (inside the join chain); the earlier audit added `LobbyQrMapService.preinit()` from `LobbyModule.load()`, which is what the spark trace is now showing — out of the hot path, but still 100 ms.

**Action:** none required; verify by re-profiling — confirm `renderPrettyQr` no longer appears in any post-bootstrap stack.

### 4. ByteArrayWorldLoader on FJ pool — **NORMAL**
```
0.35 s  ByteArrayWorldLoader.loadChunk
0.08 s  ByteArrayWorldLoader.unzip / getOrCreateDelegate
```

Lobby world being unpacked into chunks on demand on the FJ pool. Expected behaviour for an embedded base64 world; runs once per chunk request then is cached. Nothing to fix.

### 5. Tab list scheduled task — **NORMAL (already optimised)**
```
0.03 s  LobbyTabListManager.updateAll              (whole 268 s window)
0.03 s  SparkService.getMsptFormatted              (same call site)
```

30 ms of CPU for the *entire* 4-minute profile = ~0.011 % CPU. The recent skip-if-unchanged + per-bypass-bucket render is doing its job. No action.

### 6. Tick scheduler self-time — **NORMAL (idle)**
```
237.97 s  TickThread.run
237.85 s  TickSchedulerThread.run
237.52 s  TickSchedulerThread.waitUntilNextTick        ← 98.7 % asleep
237.44 s  TickSchedulerThread.sleepThread
  0.33 s  ServerProcessImpl$TickerImpl.tick            ← actual tick work
  0.34 s  SchedulerImpl.processTick
```

The 0.33 s of actual tick work over 268 s is consistent with an idle server. To see real lobby behaviour, profile while a wave of players is joining or while several parkour runs are active.

### 7. GC behaviour — **HEALTHY**
| Collector | Count | Avg pause | Notes |
|---|---|---|---|
| G1 Young | 6 | 20.5 ms | Within `MaxGCPauseMillis=130` target by a wide margin |
| G1 Concurrent | 4 | 2.75 ms | Fine |
| G1 Old | 0 | — | **No full GCs** — promising for the "no leaks under normal load" picture |

Re-profile after a few hours of uptime with players cycling to validate the Old-gen count stays at 0 — that's the real leak indicator (combined with the previous audit's CRIT-03 parkour instance leak fix).

### 8. Memory pools (snapshot from `after-collect` reading)
- **G1 Old Gen** ≈ 65 MiB used / 68 MiB committed. Tiny.
- `MaxHeap` reported as MAX_LONG (no upper bound encoded) — actual limit comes from `-XX:MaxRAMPercentage=80.0` against host RAM.
- No "watermark close to max" warning; well within budget for an idle lobby.

### 9. JVM flags inventory (worth reviewing)

```text
-Xms128M -XX:MaxRAMPercentage=80.0
-XX:+UseG1GC -XX:MaxGCPauseMillis=130
-XX:G1NewSizePercent=28 -XX:G1HeapRegionSize=16M -XX:G1ReservePercent=20
-XX:G1MixedGCCountTarget=3 -XX:InitiatingHeapOccupancyPercent=10
-XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=0
-XX:SurvivorRatio=32 -XX:MaxTenuringThreshold=1
-XX:G1SATBBufferEnqueueingThresholdPercent=30 -XX:G1ConcMarkStepDurationMillis=5
-XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:+UseNUMA
-XX:+PerfDisableSharedMem -XX:+UseLargePages -XX:+UseTransparentHugePages
-XX:LargePageSizeInBytes=2M -XX:+UseCriticalJavaThreadPriority
-XX:AllocatePrefetchStyle=3 -XX:+UseFastUnorderedTimeStamps
-XX:+EagerJVMCI -XX:-DontCompileHugeMethods -XX:MaxNodeLimit=240000
-XX:NodeLimitFudgeFactor=8000 -XX:ReservedCodeCacheSize=400M
-XX:NonNMethodCodeHeapSize=12M -XX:ProfiledCodeHeapSize=194M
-XX:NonProfiledCodeHeapSize=194M -XX:NmethodSweepActivity=1
--add-modules=jdk.incubator.vector
```

Mostly fine — this is a hand-tuned low-latency profile (an Aikar's-style variant). Two things worth questioning:

- **`MaxGCPauseMillis=130`** is generous when a tick is 50 ms. A 130 ms pause can drop 2–3 ticks back-to-back if it lands wrong. Try **`50`** — pair with `G1NewSizePercent=30` if young-gen pressure builds. The profile only saw 20.5 ms pauses, so the target was never the binding constraint — but it sets the *worst-case* you'll tolerate.
- **`InitiatingHeapOccupancyPercent=10`** is aggressive — concurrent cycles will start at 10 % old-gen occupancy. That's fine for low-allocation workloads (which this looks like) but burns CPU on a busy server. If lobby allocation ever rises (large hologram refreshes, big parkour load) bump to **`30`**.

### 10. Things NOT visible in this profile (need a load-time profile)

- TAB/sidebar render cost at high player count (header/footer is per-player after the recent fix — confirm the per-player cost stays under budget).
- Parkour HUD bandwidth — was a packet-per-move before the recent fix; the new score+second guard should drop the rate ~10×. Confirm by profiling during a 2-minute parkour run.
- MongoDB calls — the auto-refresh task wasn't triggered during this window (the path `MongoParkourLeaderboardStore.loadEntries` does not appear in the profile at all). Re-profile around a sync interval boundary or while submitting parkour results.
- `LobbyJoinInitializer.enterLobby` self-cost at scale — visible at 0.11 s for a single test join; multiply by your expected join rate.

---

## Action plan

### Now (low risk, immediate wins)

1. **Make username NPC skins async** — drop the `PlayerSkin.fromUsername` call out of `resolveSync`. Patch in [NpcSkinResolver.java](src/main/java/ua/vsevolod/lobby/feature/lobby/interaction/npc/NpcSkinResolver.java). Bootstrap goes from ~1.3 s → ~0.6 s.
2. **Plug AnvilLoader on parkour instances** — pass an `IChunkLoader` no-op to `createInstanceContainer`. Saves the per-run FS probing and removes a recurring background-CPU spike.
3. **Tighten `MaxGCPauseMillis`** to 50 (or 80 for safety) so a single bad pause can't eat multiple ticks. Test on a staging server first; revert if young-gen GC frequency spikes.

### Next (after a load-time profile)

4. **Re-profile under load** — `/spark profiler start --thread *` for ≥3 min with ≥50 players online and at least one active parkour run. Specifically watch:
   - `LobbyTabListManager.updateAll` and `LobbySidebar.refreshAll` self-time
   - `LobbyJoinInitializer.enterLobby` peaks during join waves
   - Network thread paths (`PlayerSocketConnection.writePacketSync`, `PacketWriting.writeFramedPacket`)
   - The `MongoParkourLeaderboardStore.loadEntries` path if leaderboard refresh fires
5. **Verify the previous audit's parkour instance leak fix** — after running ~20 parkour cycles, check `MinecraftServer.getInstanceManager().getInstances().size()` (or look at `G1 Old Gen` growth). Expect it stable.
6. **Compare with this profile** to see what changed — especially Mojang HTTP (should be gone from bootstrap stack) and Anvil (should be gone from FJ pool).

### Later (hygiene)

7. **Use the recommendations in `AUDIT-2026-05-16.md`** — the spark profile reinforced HIGH-08 (Mojang sync) and added a fresh case for revisiting it. Items HIGH-07 (parkour move listener scope), CRIT-06/07 (Mongo read-after-write + non-transactional updateEntries), and MED-03 (LobbyMusicManager state consolidation) remain pending.

---

## Methodology note

The Spark viewer at <https://spark.lucko.me/> renders JS-side; the raw payload at `bytebin.lucko.me/<code>` is the protobuf described in [spark_sampler.proto](https://github.com/lucko/spark/blob/master/spark-common/src/main/proto/spark/spark_sampler.proto). To reproduce the numbers above without browsing the UI, decode the protobuf directly — every `StackTraceNode` has a `times[]` array (samples per time-window in ms), and self-time per class = sum(node.times) − sum(child.times) for every node referencing that class. Group by `class_name + '.' + method_name` and sort to get the hot frames.
