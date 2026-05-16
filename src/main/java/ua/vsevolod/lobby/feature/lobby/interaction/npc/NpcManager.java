package ua.vsevolod.lobby.feature.lobby.interaction.npc;

import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
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
            NpcDefinition prev = idIn(lastApplied, id);
            boolean shouldRespawn = next == null || !next.visible() || !equivalent(prev, next);
            if (shouldRespawn) {
                detachFromTeam(entry.getValue());
                entry.getValue().remove();
                live.remove(id);
            }
        }

        // Spawn anything new or freshly-visible.
        for (NpcDefinition def : config.npcs()) {
            if (!def.visible()) continue;
            if (!live.containsKey(def.id())) {
                LobbyNpc npc = spawn(def);
                live.put(def.id(), npc);
                applyGlowTeam(npc, def);
            }
        }

        lastApplied = config;
    }

    private LobbyNpc spawn(NpcDefinition def) {
        return new LobbyNpc(
                instance,
                def.position().toPos(),
                def.name() == null ? null : Text.c(def.name()),
                def.description() == null ? null : Text.c(def.description()),
                def.glowing(),
                def.skin()
        );
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
            team.removeMember(uuid);
        }
    }

    private static NpcDefinition idIn(NpcsConfig cfg, String id) {
        for (NpcDefinition d : cfg.npcs()) if (d.id().equals(id)) return d;
        return null;
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

    public synchronized void showTo(Player player) {
        for (LobbyNpc npc : live.values()) npc.addViewer(player);
    }

    public synchronized void hideFrom(Player player) {
        for (LobbyNpc npc : live.values()) npc.removeViewer(player);
    }

    public synchronized Optional<NpcDefinition> findById(String id) {
        for (NpcDefinition def : lastApplied.npcs()) {
            if (def.id().equalsIgnoreCase(id)) return Optional.of(def);
        }
        return Optional.empty();
    }

    public synchronized Optional<String> findIdByEntity(net.minestom.server.entity.Entity entity) {
        for (Map.Entry<String, LobbyNpc> e : live.entrySet()) {
            if (e.getValue().equals(entity)) return Optional.of(e.getKey());
        }
        return Optional.empty();
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
