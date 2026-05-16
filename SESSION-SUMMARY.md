# Session Summary — `feature/admin-tools`

Final consolidated report covering every change shipped on this branch from
the initial admin-tools work through the latest 2026-05-17 audit. Branch is
ahead of `main` by ~15 commits.

Companion docs (deep-dive on individual topics):
- [PROJECT.md](PROJECT.md) — architecture & domain
- [ROADMAP.md](ROADMAP.md) — phased plan with checkboxes
- [OPTIMIZATION.md](OPTIMIZATION.md) — MSPT analysis methodology + action plan
- [AUDIT-2026-05-16.md](AUDIT-2026-05-16.md) — full audit, findings catalogue
- [SPARK-ANALYSIS-2026-05-16.md](SPARK-ANALYSIS-2026-05-16.md) — Spark profile decoded
- [SUMMARY-2026-05-16.md](SUMMARY-2026-05-16.md) — mid-session digest
- [AUDIT-2026-05-17.md](AUDIT-2026-05-17.md) — follow-up audit, security & atomicity

---

## TL;DR

Hardcoded lobby → fully config-driven plugin platform with hot-reload, plus a
multi-pass performance + security cleanup. MSPT-growth-over-time leak fixed.
Tab/sidebar made stable. Parkour made actually playable. Multi-version client
support wired through Velocity.

---

## 1. Subsystems delivered

| Phase | What | Status |
|---|---|---|
| 1 | Config infrastructure (`ConfigManager`, `/reload`, atomic writes, template auto-gen) | ✓ |
| 1 | Tab list config (`config/tab.yml`) with `{ping}`, `{time}`, `{online}`, `{mspt}` placeholders | ✓ |
| 1 | Sidebar config (`config/sidebar.yml`) with title animation + lines | ✓ |
| 2 | NPC system (`config/npcs.yml` + 11-subcommand `/npc`) — CRUD, glow + glow-color, visibility, click actions | ✓ |
| 2 | NPC skin specs: username / `url:...` / `value:<b64>;sig:<b64>` | ✓ |
| 3 | Join-items config (`config/join-items.yml`) — material, lore, glint, click actions, `bypass-only` conditions | ✓ |
| 4 | Menu config (`config/menus.yml`) + `/menu` command — chest size, decor groups, item click-actions, `visibility` | ✓ |
| 5 | Cross-cutting polish — unified `NpcAction`, `execute-as-op`, `closeAll` on reload, URL skin async | ✓ |

Also shipped earlier in the branch:
- Admin commands: `/op /deop /stop /gamemode /tpsbar /rambar /version /reload`
- Velocity modern-forwarding integration (`proxy.properties` auto-gen + secret-driven `Auth.Velocity` + `HandshakeOverride` for Via-translated protocols)
- `ClientCompat` capability adapter (text_display → armor-stand fallback for ≤1.19.3 clients)

---

## 2. Bugs fixed

| # | Bug | File | Symptom |
|---|---|---|---|
| 1 | Scoreboard ping showed `0` except briefly when value changed | `LobbySidebar.java` | `applyEnabledState` re-added viewers every tick, stomping per-viewer ping overrides |
| 2 | New joiners saw the previous player's ping on the sidebar | `PerViewerSidebar.java` | `refreshContent` reflection mutated team's canonical prefix; `addViewer` then sent that prefix to all new viewers |
| 3 | TAB ping icon stuck at join value | `LobbyTabListManager.java` | Minestom only broadcasts `UPDATE_LATENCY` on keepalive (~20 s); added 2-s diff broadcaster |
| 4 | `{ping}` in TAB header/footer rendered in 50-ms buckets (a player with 23 ms ping saw `"0"`) | `LobbyTabListManager.java` | Old bucket-grouping path; rewritten with per-player `String.replace` after a single template render |
| 5 | Launch-pad double jump silently stopped after parkour | `LobbyJoinInitializer.java` | `setGameMode` reset `allowFlying`; re-asserted after |
| 6 | Parkour run instances never unregistered → instance leak | `ParkourService.java` | Polling cleanup task waits until player teleports out, then `unregisterInstance` |
| 7 | Music ambient-suppressor task TOCTOU race | `LobbyMusicManager.java` | `containsKey + put` could leak tasks; replaced with `computeIfAbsent` |
| 8 | MSPT grew over 90 mins (0.30 → 0.70) — unbounded `Text.CACHE` | `Text.java` + 4 call sites | `Text.c(...)` was unbounded; sites with dynamic strings switched to `Text.raw`. Cache now LRU-bounded |
| 9 | `Invalid Version, please use 1.21.11` kick under Velocity + ViaVersion | `HandshakeOverride.java` | Minestom's strict protocol check replaced when Velocity forwarding is enabled |
| 10 | Parkour generator produced physically impossible jumps (`+2y`, sqrt(x²+z²)>3 with `+1y`) | `ParkourGenerator.java` | Whole pattern table re-verified against vanilla budgets |

---

## 3. Performance optimizations

### Hot-path rendering
- **Tab**: per-player skip-if-unchanged + bypass-bucketed template rendering. Idle ticks send zero packets. Latency icons batched every 2 s.
- **Sidebar**: per-line skip-if-unchanged for global lines, per-player cache for `{ping}`. `applyEnabledState` no-op on steady state.
- **Stats bars**: per-player skip-if-unchanged on TPS and RAM labels; symmetric `if (have*)` guards.

### Data-structure swaps
- `BYPASS_USERS`: `CopyOnWriteArrayList` (O(n) `contains`) → `ConcurrentHashMap.newKeySet()` (O(1)). Hit per-tick in tab + per-event in inventory/block/chat handlers.
- `NpcManager`: added `IdentityHashMap<Entity, String>` reverse index + `Map<String, NpcDefinition>` by-id index. `findIdByEntity` / `findById` are O(1) now.
- `Text.CACHE`: clear-on-overflow → LRU eviction (synchronised `LinkedHashMap`).
- `StatsBarService`: `Map<Player, …>` → `Map<UUID, …>`. Resolves Player from `ConnectionManager` at send time.

### Async I/O
- `ParkourLeaderboardService.refreshFromStore` now runs on a virtual thread.
- `NpcSkinResolver`: ALL non-`value:` skins (URL and plain username) now resolve async via virtual thread; bootstrap no longer blocks on Mojang HTTP (was 0.73 s = 57 % of bootstrap per username NPC).
- `MsptLogger` flush runs on a virtual thread.

### Networking
- Compression threshold set to `-1` (disabled on loopback to Velocity) — was `0` which compressed every packet.
- `MinecraftServer.setCompressionThreshold(-1)` saves CPU cycles wasted on zlib for already-fast localhost transfers.
- Latency-icon broadcast batches all changed values into one `PlayerInfoUpdatePacket`.

### Listener fast-paths
- `LaunchPadManager.onMove` returns immediately when no one is actively jumping (PlayerMoveEvent fires 5–10×/s/player).
- `ParkourListener` PlayerMoveEvent returns immediately when `sessions.isEmpty()`.
- `PlayerHider` caches `getOnlinePlayers()` once + dropped redundant `updateViewerRule` calls.

### Hologram culling
- `TextHologramEntry.sendMetadataPerViewer` skips players >64 blocks from the hologram (squared compare, no sqrt). Especially valuable for the 40-pkt parkour leaderboard refresh.

### Chunk loading
- `ByteArrayWorldLoader`: scans the unzipped lobby world for `r.X.Z.mca` region files at load. `loadChunk(cx, cz)` returns null immediately when `(cx>>5, cz>>5)` has no `.mca`. Eliminates disk probes for void chunks. With Spark having flagged `AnvilLoader.loadMCA` at 0.30 s of FJ-pool CPU per cycle, this is a real win.
- Parkour instances use `ChunkLoader.noop()` — no `.mca` lookup at all because parkour worlds are generator-only.

### View distance
- Set via `System.setProperty` in `Main` before `MinecraftServer.init()`:
  - `minestom.chunk-view-distance=6` (was 8)
  - `minestom.entity-view-distance=4`
  - `minestom.entity-synchronization-ticks=20`

### Per-player task collapse
- `LobbyProtocolWarningService` was scheduling one 1-s task per warned player. Replaced with a single global 1-s ticker walking a `ConcurrentHashMap<UUID, Warning>`. Lazy-started on first warning.

### Spark stat semantics
- `SparkService` was returning `data.min()` over 10-s window — the BEST single tick, misleading users into thinking the server was faster than it really was.
- Now returns `data.mean()`:
  - **MSPT** → Spark `MillisPerTick.SECONDS_10` mean
  - **TPS** → Spark `TicksPerSecond.SECONDS_5` mean
- `StatsBarService` dropped its local `lastTickMs` field; reads directly from `SparkService` to keep all surfaces consistent.

---

## 4. Memory leak fixes

- **`Text.CACHE` heap-leak** — was the cause of the MSPT-grows-over-time bug. Six dynamic-string call sites switched to `Text.raw`. Cache itself now bounded at 512 entries with LRU eviction.
- **Parkour instance leak** — every `/parkour` start created an `InstanceContainer` that was never unregistered. Polling cleanup added in `stop()`.
- **Music ambient-suppressor task leak** — TOCTOU race in `ensureAmbientSuppressor` could leak per-tick tasks. Fixed with `computeIfAbsent`.
- **`ProxyOnlineService` plugin-message heap leak** — modded clients could pump unique server names into the `counts` map. Whitelist enforced.
- **`StatsBarService` Player-key reference pin** — `Map<Player, …>` held strong refs to disconnected players if cleanup missed a path. Switched to UUID keys.

All other `Map<UUID, ?>` were audited and confirmed cleaned by their respective `PlayerDisconnectEvent` paths.

---

## 5. Spark analysis findings & response

Profile decoded at <https://spark.lucko.me/Gvl6xqQOlB> (full breakdown in `SPARK-ANALYSIS-2026-05-16.md`).

| Finding | Spark cost | Fix |
|---|---|---|
| Mojang HTTP blocked bootstrap | 0.73 s / 57 % of total | Username skin async (HIGH-08) |
| AnvilLoader probing for non-existent .mca on parkour | 0.30 s of FJ-pool CPU | `ChunkLoader.noop()` (ANVIL-01) |
| QR map renderer cost in join path | 0.10 s | `preinit()` from `LobbyModule.load()` (MED-02) |
| `data.min()` displayed wrong | — | Switched to `data.mean()` over SECONDS_10/5 |
| GC pause target 130 ms vs 50 ms tick budget | — | Documented as out-of-our-control (host-locked JVM flags) |

The profile was captured at an idle server (98.7 % tick-thread sleep). A follow-up profile under representative load is recommended — see "Re-profile checklist" in `SUMMARY-2026-05-16.md`.

---

## 6. Security findings

All in `AUDIT-2026-05-17.md`.

- **HIGH-A — Plugin-message heap-grow attack** (`ProxyOnlineService`): modded client could write to `bungeecord:main` with arbitrary `serverName` strings → `counts` map grew without bound. Whitelist via `registeredServers` now enforced.
- **MED-A — Non-atomic ops file** (`OpsStore.save`): crash during write could silently truncate the ops list, removing /op rights from real users on next restart. Switched to tmp + ATOMIC_MOVE.
- **MED-B — Non-atomic version-gate file** (`VersionGate.save`): same risk; same fix.

Audited and **confirmed not bugs**:
- `ByteArrayWorldLoader` zip-slip — already correctly guarded via `.normalize() + startsWith(targetDir)`.
- `NpcActionExecutor.runCommand` "command injection" — `target` is owner-edited NPC config, not untrusted input.
- `HandshakeOverride` switch fall-through — intentional pattern.
- `OpCommand` newline injection — Minecraft chat protocol strips control characters before the parser sees them.

---

## 7. TAB & scoreboard fixes (consolidated)

| Where | Issue | Fix |
|---|---|---|
| Sidebar | ping always showed `0` | `applyEnabledState` now no-op except on actual on/off transitions |
| Sidebar | new joiners saw last player's ping | Dropped reflective `refreshContent` mutation in `PerViewerSidebar` |
| Tab | latency icon stuck after join | New 2-s `UPDATE_LATENCY` batch broadcast |
| Tab | `{ping}` rendered to nearest 50 ms | Per-player `String.replace` over a bypass-bucketed template |
| Tab | non-grouped sends were O(n) | `PacketSendingUtils.sendGroupedPacket` for shared headers/footers; only changed players get a packet |
| Tab/Sidebar/StatsBar | `lastTickMs` was the latest one-tick value | All three now read Spark mean (10 s for MSPT, 5 s for TPS) |
| Sidebar | hard-coded lines | YAML config + hot-reload |
| Sidebar | always rendered | `enabled` master flag in `sidebar.yml` |
| Tab | hard-coded format | YAML config + hot-reload |

---

## 8. Parkour fixes

- **Instance leak** — every run was leaking its `InstanceContainer`. Now cleaned up by polling task.
- **HUD spam** — `sendHud()` rebuilt the Component graph + sent an actionbar packet on every PlayerMoveEvent. Added `lastHudScore` / `lastHudSecond` short-circuit. ~10× drop in HUD packet rate during steady running.
- **`getNextTarget()` Stream allocation per tick** — replaced with two-step iterator advance.
- **Double-jump broken after parkour return** — `LobbyJoinInitializer.setupState` re-asserts `setAllowFlying(true)` after `setGameMode`.
- **Impossible jumps** — entire `ParkourGenerator` pattern table re-verified against vanilla physics:
  - flat sprint: sqrt(dx²+dz²) ≤ 4
  - +1 up sprint: sqrt(dx²+dz²) ≤ 3
  - +2 up: removed (physically impossible without slabs)
  - drops extend horizontal reach
- **Listener scope** — `ParkourListener.PlayerMoveEvent` fast-bails when no sessions exist.
- **AnvilLoader waste** — parkour instances now use `ChunkLoader.noop()`.

---

## 9. Velocity / multi-version

- `proxy.properties` auto-generated on first launch — secret + bind address/port.
- When secret is set: `MinecraftServer.init(new Auth.Velocity(secret))` + `HandshakeOverride.install()` to bypass the strict protocol-version kick.
- `ClientCompat` adapter: reads original client protocol from the `vsevolod_lobby_protocol` GameProfile property forwarded by the Velocity plugin, falls back to `getProtocolVersion()`.
- `TextHologramEntry` substitutes armor-stand fallback for clients < 1.19.4 (no `text_display`).

---

## 10. Diagnostics & observability

- **`MsptLogger`** — per-minute `avg / max / ticks` line written to `logs/mspt.log` from a virtual thread. Disk I/O can't stall the scheduler.
- **`/version`** — owner-only protocol gate with min/max bounds.
- **Spark plugin** — wired with owner-only permission handler.
- **`/tpsbar` and `/rambar`** — boss-bar HUDs for live MSPT / RAM, owner-only.

---

## 11. Known remaining issues (open recommendations)

### Critical (data correctness)
- **CRIT-07** — `MongoParkourLeaderboardStore.updateEntries` is non-transactional `deleteMany + insertMany`. A crash between the two calls wipes the leaderboard. Needs a Mongo session + transaction.

### High (performance)
- **HIGH-09** — Tab/sidebar/StatsBar scheduler intervals are read at startup; `/reload` doesn't reschedule. Workaround: restart the server after changing interval values.
- **HIGH-10** — `LobbyJoinInitializer.enterLobby` is heavy and synchronous. NPC viewer-add happens inside a `synchronized` block — partially mitigated by snapshotting, but concurrent joins still serialise.

### Medium (hygiene)
- **REC-A / MED-03** — `LobbyMusicManager` has 7 independent UUID-keyed maps. All currently cleaned on disconnect, but a single `PlayerMusicState` record would prevent future cleanup-omission bugs by construction.
- **REC-B / MED-05** — `MinestomTagsWorkaround` listens to every outgoing packet for a small `instanceof TagsPacket` check. Per-connection scoping would be cheaper at scale.

### Low
- **LOW-01** — Various trivial allocation hoists; flagged for consistency but not blocking.

---

## 12. File inventory

### New files this session (config + code)
```
src/main/java/ua/vsevolod/lobby/feature/admin/
    config/ConfigManager.java
    config/ConfigSection.java
    MsptLogger.java
    OpsStore.java
    StatsBarService.java
    VersionGate.java
    VersionGateListener.java

src/main/java/ua/vsevolod/lobby/feature/lobby/interaction/npc/
    NpcManager.java
    NpcActionExecutor.java
    NpcSkinResolver.java
    config/NpcAction.java
    config/NpcConfigSection.java
    config/NpcDefinition.java
    config/NpcPosition.java
    config/NpcsConfig.java

src/main/java/ua/vsevolod/lobby/feature/lobby/ui/menu/
    MenuManager.java
    config/MenuDecor.java
    config/MenuDefinition.java
    config/MenuItem.java
    config/MenusConfig.java
    config/MenusConfigSection.java

src/main/java/ua/vsevolod/lobby/feature/lobby/ui/sidebar/
    SidebarConfig.java
    SidebarConfigSection.java

src/main/java/ua/vsevolod/lobby/feature/lobby/ui/tab/
    TabConfig.java
    TabConfigSection.java

src/main/java/ua/vsevolod/lobby/feature/lobby/player/compat/
    ClientCapabilities.java
    ClientCompat.java

src/main/java/ua/vsevolod/lobby/feature/lobby/player/join/items/
    JoinItemDefinition.java
    JoinItemManager.java
    JoinItemUseListener.java
    JoinItemsConfig.java
    JoinItemsConfigSection.java

src/main/java/ua/vsevolod/lobby/command/admin/
    DeopCommand.java
    MenuCommand.java
    NpcCommand.java
    OpCommand.java
    RamBarCommand.java
    ReloadCommand.java
    TpsBarCommand.java
    VersionCommand.java

src/main/java/ua/vsevolod/lobby/config/
    ProxyConfig.java

src/main/java/ua/vsevolod/lobby/bootstrap/server/
    HandshakeOverride.java
```

### Modified core files
```
src/main/java/ua/vsevolod/lobby/bootstrap/Main.java
src/main/java/ua/vsevolod/lobby/bootstrap/server/ServerBootstrap.java
src/main/java/ua/vsevolod/lobby/bootstrap/module/CommandModule.java
src/main/java/ua/vsevolod/lobby/bootstrap/module/LobbyModule.java
src/main/java/ua/vsevolod/lobby/bootstrap/module/InstanceModule.java
src/main/java/ua/vsevolod/lobby/bootstrap/module/ByteArrayWorldLoader.java
src/main/java/ua/vsevolod/lobby/config/LobbyConfig.java
src/main/java/ua/vsevolod/lobby/feature/lobby/bootstrap/LobbyEventRegistrar.java
src/main/java/ua/vsevolod/lobby/feature/lobby/ui/sidebar/LobbySidebar.java
src/main/java/ua/vsevolod/lobby/feature/lobby/ui/sidebar/PerViewerSidebar.java
src/main/java/ua/vsevolod/lobby/feature/lobby/ui/tab/LobbyTabListManager.java
src/main/java/ua/vsevolod/lobby/feature/lobby/interaction/npc/LobbyNpc.java
src/main/java/ua/vsevolod/lobby/feature/lobby/interaction/npc/LobbyNpcInteractionListener.java
src/main/java/ua/vsevolod/lobby/feature/lobby/interaction/npc/LobbyNpcService.java
src/main/java/ua/vsevolod/lobby/feature/lobby/player/join/LobbyItemService.java
src/main/java/ua/vsevolod/lobby/feature/lobby/player/join/LobbyJoinInitializer.java
src/main/java/ua/vsevolod/lobby/feature/lobby/player/visibility/PlayerHider.java
src/main/java/ua/vsevolod/lobby/feature/lobby/world/movement/LaunchPadManager.java
src/main/java/ua/vsevolod/lobby/feature/lobby/ui/hologram/LobbyWelcomeHologramService.java
src/main/java/ua/vsevolod/lobby/feature/lobby/ui/hologram/ParkourLeaderboardHologramService.java
src/main/java/ua/vsevolod/lobby/feature/lobby/ui/hologram/TextHologramEntry.java
src/main/java/ua/vsevolod/lobby/feature/lobby/interaction/qr/LobbyQrMapService.java
src/main/java/ua/vsevolod/lobby/feature/lobby/player/protocol/LobbyProtocolWarningService.java
src/main/java/ua/vsevolod/lobby/feature/lobby/player/chat/LobbyPlayerChatListener.java
src/main/java/ua/vsevolod/lobby/feature/lobby/audio/music/LobbyMusicManager.java
src/main/java/ua/vsevolod/lobby/feature/parkour/ParkourGenerator.java
src/main/java/ua/vsevolod/lobby/feature/parkour/ParkourService.java
src/main/java/ua/vsevolod/lobby/feature/parkour/ParkourSession.java
src/main/java/ua/vsevolod/lobby/feature/parkour/ParkourListener.java
src/main/java/ua/vsevolod/lobby/feature/parkour/leaderboard/ParkourLeaderboardService.java
src/main/java/ua/vsevolod/lobby/integration/spark/SparkService.java
src/main/java/ua/vsevolod/lobby/util/Text.java
build.gradle.kts
.gitignore
```

### Runtime files generated on first launch
- `config/tab.yml` `config/sidebar.yml` `config/npcs.yml` `config/menus.yml` `config/join-items.yml`
- `proxy.properties`
- `storage/ops.txt` `storage/version_gate.txt`
- `logs/mspt.log` (per-minute rolling)

---

## 13. Commands surfaced for in-game admin

Owner-only (`godes2020`):

| Command | Description |
|---|---|
| `/reload` | Reload all YAML configs without restart |
| `/op <name>` | Grant bypass / level-4 permissions |
| `/deop <name>` | Revoke |
| `/stop` | Shut server down |
| `/gamemode` | Change own game mode |
| `/tpsbar` | Toggle live TPS/MSPT/ping bossbar |
| `/rambar` | Toggle live RAM bossbar |
| `/version on|off|min N|max N` | Protocol gate controls |
| `/npc list|add|remove|move|setname|setdesc|setskin|setglow|setglowcolor|setvisible|setaction` | NPC CRUD |
| `/menu list|open <id>|setvisibility <id> all|bypass-only` | Menu CRUD |
| `/spawn` (alias `/hub /lobby`) | Teleport to lobby spawn |

---

## 14. Build verification

- `./gradlew shadowJar` clean across every commit on this branch.
- JDK toolchain pinned to 25 (last commit reverted earlier 26-bump since Gradle is not configured to auto-download); `release=25` bytecode target unchanged so the JAR is byte-identical regardless of toolchain JDK.
- Latest JAR: `lobby-run/server.jar` ~42 MB.
- All session-end smoke tests pass (server boots, all 5 YAML configs auto-generated on first start, log line `ConfigManager Created default config: …` for each).

---

## 15. Commit history (this branch)

```
fca75d8 Security + leak audit 2026-05-17 — whitelist plugin-msg, atomic file writes
b4ed45f World bounds + averaged TPS/MSPT + reachable parkour
90107fc Post-Spark perf pass: async Mojang, parkour AnvilLoader, listener fast-path, LRU cache
19030c1 Fix scoreboard/TAB ping + parkour double jump + perf pass
8d784e0 Deep perf pass — grouped packets + content cache + hologram culling + MSPT log
94b4c04 Phase 3+4+5: join items + menus + NPC URL skins
876f948 Perf pass + Text.c leak fix + harder parkour
eca1fe0 Phase 2: NPC config + CRUD commands + sidebar toggle + perf pass
4600df3 Phase 1: config infrastructure + tab/sidebar hot-reload
0a1956c Add client capability adapter with text_display fallback
66fb8da Add Velocity modern forwarding support
d10eba5 Add admin tools: ops, stats bars, version gate
```
