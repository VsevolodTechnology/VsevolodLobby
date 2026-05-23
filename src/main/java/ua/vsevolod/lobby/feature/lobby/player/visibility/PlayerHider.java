package ua.vsevolod.lobby.feature.lobby.player.visibility;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.tag.Tag;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.LobbyNpc;
import ua.vsevolod.lobby.feature.lobby.player.join.items.JoinItemsConfig;
import ua.vsevolod.lobby.feature.lobby.player.join.items.ToggleItemDefinition;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class PlayerHider {

    public static final int ITEM_SLOT = 7;

    private static final Tag<String> TOGGLE_ITEM_TAG = Tag.String("lobby-toggle-players");
    private static final String ITEM_SHOWING = "showing";
    private static final String ITEM_HIDDEN = "hidden";

    private static final long TOGGLE_COOLDOWN_MS = 400;

    private PlayerPreferencesService preferencesService;

    private final Set<UUID>        hiddenPlayers   = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long>  toggleCooldowns = new ConcurrentHashMap<>();

    public void setPreferencesService(PlayerPreferencesService service) {
        this.preferencesService = service;
    }

    public void applyVisibilityPreference(UUID uuid, boolean playersHidden) {
        if (playersHidden) {
            hiddenPlayers.add(uuid);
        } else {
            hiddenPlayers.remove(uuid);
        }
    }

    public void register(EventNode<Event> node) {
        node.addListener(PlayerSpawnEvent.class, this::onSpawn);
        node.addListener(PlayerUseItemEvent.class, this::onUseItem);
        node.addListener(PlayerDisconnectEvent.class, this::onDisconnect);
    }

    private void onSpawn(PlayerSpawnEvent event) {
        Player player = event.getPlayer();

        applyWorldVisibilityRule(player);

        var online = MinecraftServer.getConnectionManager().getOnlinePlayers();
        refreshTabFor(player, online);

        for (UUID hiddenUuid : hiddenPlayers) {
            if (hiddenUuid.equals(player.getUuid())) continue;
            Player other = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(hiddenUuid);
            if (other == null) continue;
            hideFromTab(other, player);
        }
        player.updateViewerRule();

        updateToggleItem(player);
    }

    private void onUseItem(PlayerUseItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemStack();
        String state = item.getTag(TOGGLE_ITEM_TAG);
        if (!ITEM_SHOWING.equals(state) && !ITEM_HIDDEN.equals(state)) return;
        event.setCancelled(true);
        toggle(player);
    }

    private void onDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayer().getUuid();
        hiddenPlayers.remove(uuid);
        toggleCooldowns.remove(uuid);
    }

    public void toggle(Player player) {
        long now = System.currentTimeMillis();
        Long last = toggleCooldowns.put(player.getUuid(), now);
        if (last != null && (now - last) < TOGGLE_COOLDOWN_MS) return;
        if (isHidden(player)) {
            hiddenPlayers.remove(player.getUuid());
            player.sendMessage(JoinItemsConfig.get().toggleItems.players().message(true));
        } else {
            hiddenPlayers.add(player.getUuid());
            player.sendMessage(JoinItemsConfig.get().toggleItems.players().message(false));
        }

        applyWorldVisibilityRule(player);
        refreshTabFor(player, MinecraftServer.getConnectionManager().getOnlinePlayers());
        updateToggleItem(player);
        if (preferencesService != null) {
            preferencesService.savePlayersHidden(player.getUuid(), isHidden(player));
        }
    }

    public boolean isHidden(Player player) {
        return hiddenPlayers.contains(player.getUuid());
    }

    private void applyWorldVisibilityRule(Player player) {
        if (isHidden(player)) {
            player.updateViewerRule(entity -> {
                if (entity == player) return true;
                if (entity instanceof LobbyNpc) return false;
                return !(entity instanceof Player);
            });
        } else {
            player.updateViewerRule(entity -> !(entity instanceof LobbyNpc));
        }
    }

    /**
     * Refresh the player's view of the tab list — show or hide every OTHER online player.
     *
     * <p>Previously this sent one {@code PlayerInfoRemovePacket} / {@code PlayerInfoUpdatePacket}
     * per target (so toggling visibility cost N packets at 100 online). Now we build a single
     * batched packet and send it once — N targets, 1 packet.</p>
     */
    private void refreshTabFor(Player player, java.util.Collection<Player> onlinePlayers) {
        boolean hidden = isHidden(player);
        if (hidden) {
            java.util.List<UUID> uuids = new java.util.ArrayList<>(onlinePlayers.size());
            for (Player target : onlinePlayers) {
                if (target == player) continue;
                uuids.add(target.getUuid());
            }
            if (!uuids.isEmpty()) {
                player.getPlayerConnection().sendPacket(new PlayerInfoRemovePacket(uuids));
            }
        } else {
            java.util.List<PlayerInfoUpdatePacket.Entry> entries = new java.util.ArrayList<>(onlinePlayers.size());
            for (Player target : onlinePlayers) {
                if (target == player) continue;
                entries.add(tabEntryFor(target));
            }
            if (!entries.isEmpty()) {
                player.getPlayerConnection().sendPacket(new PlayerInfoUpdatePacket(
                        EnumSet.of(PlayerInfoUpdatePacket.Action.ADD_PLAYER, PlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                        entries
                ));
            }
        }
    }

    /** Called from onSpawn — single-target reverse view (other → this player), batching is N/A. */
    private void hideFromTab(Player viewer, Player target) {
        viewer.getPlayerConnection().sendPacket(
                new PlayerInfoRemovePacket(List.of(target.getUuid()))
        );
    }

    private static PlayerInfoUpdatePacket.Entry tabEntryFor(Player target) {
        return new PlayerInfoUpdatePacket.Entry(
                target.getUuid(),
                target.getUsername(),
                List.of(),
                true,
                target.getLatency(),
                target.getGameMode(),
                target.getDisplayName(),
                null,
                0,
                true
        );
    }

    private void updateToggleItem(Player player) {
        player.getInventory().setItemStack(ITEM_SLOT, buildToggleItem(isHidden(player)));
    }

    /** Builds the player-visibility toggle item live from {@code config/toggle-items.yml}. */
    private static ItemStack buildToggleItem(boolean hidden) {
        ToggleItemDefinition def = JoinItemsConfig.get().toggleItems.players();
        boolean enabled = !hidden;
        return ItemStack.builder(def.material(enabled))
                .set(DataComponents.CUSTOM_NAME, def.displayName(enabled))
                .set(DataComponents.LORE, def.lore(enabled))
                .set(TOGGLE_ITEM_TAG, hidden ? ITEM_HIDDEN : ITEM_SHOWING)
                .build();
    }
}
