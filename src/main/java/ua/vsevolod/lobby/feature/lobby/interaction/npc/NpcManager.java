package ua.vsevolod.lobby.feature.lobby.interaction.npc;

import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.instance.Instance;
import net.minestom.server.scoreboard.Team;
import net.minestom.server.scoreboard.TeamBuilder;
import net.minestom.server.scoreboard.TeamManager;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcConfigSection;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcDefinition;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcsConfig;
import ua.vsevolod.lobby.util.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Owns the live {@link LobbyNpc} entities. On {@link #onConfigApplied(NpcsConfig)} computes a diff
 * against the previous snapshot and adds/removes/respawns NPCs accordingly. Glow colour is
 * implemented by adding the NPC's UUID string to a Minecraft team whose colour matches.
 */
public final class NpcManager {

    private final Instance instance;
    private final Map<String, LobbyNpc> live = new LinkedHashMap<>();
    private final Map<String, Team> glowTeams = new LinkedHashMap<>();
    /** Reverse lookup live entity -> NPC id. Avoids the O(N) entry-walk in {@link #findIdByEntity}. */
    private final Map<net.minestom.server.entity.Entity, String> entityToId = new java.util.IdentityHashMap<>();
    /** Index of the last applied config by id. Avoids the O(N) list-scan in {@link #findById}. */
    private Map<String, NpcDefinition> lastAppliedById = Map.of();
    private NpcsConfig lastApplied = new NpcsConfig(List.of());

    public NpcManager(Instance instance) {
        this.instance = instance;
    }

    public synchronized void onConfigApplied(NpcsConfig config) {
        Map<String, NpcDefinition> incoming = new LinkedHashMap<>();
        for (NpcDefinition def : config.npcs()) incoming.put(def.id(), def);

        // Despawn anything that disappeared, became invisible, or whose visual identity changed.
        for (Map.Entry<String, LobbyNpc> entry : new ArrayList<>(live.entrySet())) {
            String id = entry.getKey();
            NpcDefinition next = incoming.get(id);
            NpcDefinition prev = lastAppliedById.get(id);
            boolean shouldRespawn = next == null || !next.visible() || !equivalent(prev, next);
            if (shouldRespawn) {
                LobbyNpc npc = entry.getValue();
                detachFromTeam(npc);
                entityToId.remove(npc);
                npc.remove();
                live.remove(id);
            }
        }

        // Spawn anything new or freshly-visible.
        List<LobbyNpc> newlySpawned = new ArrayList<>();
        for (NpcDefinition def : config.npcs()) {
            if (!def.visible()) continue;
            if (!live.containsKey(def.id())) {
                LobbyNpc npc = spawn(def);
                live.put(def.id(), npc);
                entityToId.put(npc, def.id());
                applyGlowTeam(npc, def);
                newlySpawned.add(npc);
            }
        }

        // Show newly spawned NPCs to all players currently in the instance.
        if (!newlySpawned.isEmpty()) {
            for (var player : instance.getPlayers()) {
                for (LobbyNpc npc : newlySpawned) {
                    try {
                        npc.addViewer(player);
                    } catch (ArrayIndexOutOfBoundsException ignored) {
                    }
                }
            }
        }

        lastApplied = config;
        // Rebuild the by-id index from the new config so subsequent findById is O(1).
        Map<String, NpcDefinition> rebuilt = new LinkedHashMap<>(incoming.size());
        for (NpcDefinition def : config.npcs()) rebuilt.put(def.id(), def);
        lastAppliedById = rebuilt;
    }

    private LobbyNpc spawn(NpcDefinition def) {
        PlayerSkin syncSkin = NpcSkinResolver.resolveSync(def.skin());

        LobbyNpc npc = new LobbyNpc(
                instance,
                def.position().toPos(),
                def.name() == null ? null : Text.c(def.name()),
                def.description() == null ? null : Text.c(def.description()),
                def.glowing(),
                syncSkin
        );

        // If the skin spec was a URL, resolveSync returned null and the resolver is now
        // fetching it in the background. Update the live entity once the texture arrives.
        if (syncSkin == null && def.skin() != null && !def.skin().isBlank()) {
            String npcId = def.id();
            NpcSkinResolver.resolveAsync(def.skin(), playerSkin -> {
                synchronized (NpcManager.this) {
                    LobbyNpc current = live.get(npcId);
                    if (current != null) current.updateSkin(playerSkin);
                }
            });
        }

        return npc;
    }

    private void applyGlowTeam(LobbyNpc npc, NpcDefinition def) {
        if (!def.glowing()) return;
        if (def.glowColor() == null || def.glowColor().isBlank()) return;
        NamedTextColor color = NamedTextColor.NAMES.value(def.glowColor().trim().toLowerCase());
        if (color == null) {
            System.err.println("[NpcManager] Unknown glow-color '" + def.glowColor() + "' for npc " + def.id());
            return;
        }
        Team team = teamFor(color);
        team.addMember(npc.getUuid().toString());
    }

    private Team teamFor(NamedTextColor color) {
        String key = color.toString();
        Team cached = glowTeams.get(key);
        if (cached != null) return cached;
        TeamManager teamManager = MinecraftServer.getTeamManager();
        String teamName = "npc-glow-" + key;
        Team existing = teamManager.getTeam(teamName);
        if (existing != null) {
            glowTeams.put(key, existing);
            return existing;
        }
        TeamBuilder builder = teamManager.createBuilder(teamName);
        Team team = builder.teamColor(color).build();
        glowTeams.put(key, team);
        return team;
    }

    private void detachFromTeam(LobbyNpc npc) {
        String uuid = npc.getUuid().toString();
        for (Team team : glowTeams.values()) {
            if (team.getMembers().contains(uuid)) {
                team.removeMember(uuid);
            }
        }
    }

    /** «Same visual entity» — if false the NPC is despawned and respawned. */
    private static boolean equivalent(NpcDefinition a, NpcDefinition b) {
        if (a == null || b == null) return false;
        return a.position().equals(b.position())
                && java.util.Objects.equals(a.name(), b.name())
                && java.util.Objects.equals(a.description(), b.description())
                && java.util.Objects.equals(a.skin(), b.skin())
                && a.glowing() == b.glowing()
                && java.util.Objects.equals(a.glowColor(), b.glowColor())
                && a.visible() == b.visible();
    }

    public void showTo(Player player) {
        // Snapshot the live list under the lock — the actual addViewer calls can race with
        // each other safely (Minestom's Entity handles its own viewer-set thread safety),
        // and we don't want concurrent joins to serialize on this monitor. Without the
        // snapshot+release split, every joiner waited for the previous joiner's full NPC
        // viewer-add loop to complete. Audit HIGH-10 fix.
        LobbyNpc[] snapshot;
        synchronized (this) {
            snapshot = live.values().toArray(new LobbyNpc[0]);
        }
        for (LobbyNpc npc : snapshot) {
            try {
                npc.addViewer(player);
            } catch (ArrayIndexOutOfBoundsException ignored) {
            }
        }
    }

    public void hideFrom(Player player) {
        LobbyNpc[] snapshot;
        synchronized (this) {
            snapshot = live.values().toArray(new LobbyNpc[0]);
        }
        for (LobbyNpc npc : snapshot) {
            try {
                npc.removeViewer(player);
            } catch (ArrayIndexOutOfBoundsException ignored) {
            }
        }
    }

    public synchronized Optional<NpcDefinition> findById(String id) {
        NpcDefinition def = lastAppliedById.get(id);
        if (def != null) return Optional.of(def);
        // Case-insensitive fallback only when the fast O(1) path missed — rare, called from
        // admin commands typed by humans rather than the hot interaction path.
        for (Map.Entry<String, NpcDefinition> e : lastAppliedById.entrySet()) {
            if (e.getKey().equalsIgnoreCase(id)) return Optional.of(e.getValue());
        }
        return Optional.empty();
    }

    public synchronized Optional<String> findIdByEntity(net.minestom.server.entity.Entity entity) {
        // Hot path: every NPC right-click / attack runs this. Previous impl walked every live
        // NPC (O(N)). IdentityHashMap lookup is O(1) and there is exactly one entity per NPC.
        String id = entityToId.get(entity);
        return id == null ? Optional.empty() : Optional.of(id);
    }

    public synchronized List<String> allIds() {
        List<String> ids = new ArrayList<>();
        for (NpcDefinition def : lastApplied.npcs()) ids.add(def.id());
        return ids;
    }

    public synchronized List<NpcDefinition> all() {
        return new ArrayList<>(lastApplied.npcs());
    }

    public synchronized void updateAndSave(List<NpcDefinition> newList) throws IOException {
        NpcConfigSection.INSTANCE.saveAndApply(new NpcsConfig(newList));
    }
}
