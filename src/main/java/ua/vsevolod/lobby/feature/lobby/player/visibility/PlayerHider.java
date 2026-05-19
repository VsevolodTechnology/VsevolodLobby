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
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.LobbyNpc;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService;
import ua.vsevolod.lobby.util.Text;

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
            player.sendMessage(
                    Component.text("[", NamedTextColor.DARK_GRAY).append(HIDER_TEXT).append(Component.text("]", NamedTextColor.DARK_GRAY))
                            .append(Component.space())
                            .append(Component.text("Вы", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)).append(Component.space())
                            .append(Component.text("включили", TextColor.color(0x81E366))).append(Component.space())
                            .append(Component.text("видимость игроков", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
            );
        } else {
            hiddenPlayers.add(player.getUuid());
            player.sendMessage(
                    Component.text("[", NamedTextColor.DARK_GRAY).append(HIDER_TEXT).append(Component.text("]", NamedTextColor.DARK_GRAY))
                            .append(Component.space())
                            .append(Component.text("Вы", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)).append(Component.space())
                            .append(Component.text("выключили", TextColor.color(0xE36666))).append(Component.space())
                            .append(Component.text("видимость игроков", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
            );
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

    private void refreshTabFor(Player player, java.util.Collection<Player> onlinePlayers) {
        boolean hidden = isHidden(player);
        for (Player target : onlinePlayers) {
            if (target == player) continue;
            if (hidden) hideFromTab(player, target);
            else        showInTab(player, target);
        }
    }

    private void hideFromTab(Player viewer, Player target) {
        viewer.getPlayerConnection().sendPacket(
                new PlayerInfoRemovePacket(List.of(target.getUuid()))
        );
    }

    private void showInTab(Player viewer, Player target) {
        PlayerInfoUpdatePacket.Entry entry = new PlayerInfoUpdatePacket.Entry(
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

        viewer.getPlayerConnection().sendPacket(
                new PlayerInfoUpdatePacket(
                        EnumSet.of(PlayerInfoUpdatePacket.Action.ADD_PLAYER, PlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                        List.of(entry)
                )
        );
    }

    private final static Component HIDER_TEXT = Text.c("&#F1BB58&lИ&#F1B958&lг&#F1B658&lр&#F1B458&lо&#F1B158&lк&#F1AF58&lи");
    private final static Component HIDER_TEXT_ON = HIDER_TEXT.append(Component.space())
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("Видны", TextColor.color(0x8EB126)))
            .append(Component.text("]", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false);
    private final static Component HIDER_TEXT_OFF = HIDER_TEXT.append(Component.space())
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("Скрыты", TextColor.color(0xFA3B3B)))
            .append(Component.text("]", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false);

    private static final ItemStack TOGGLE_ITEM_SHOWING = buildToggleItem(false);
    private static final ItemStack TOGGLE_ITEM_HIDDEN = buildToggleItem(true);

    private void updateToggleItem(Player player) {
        player.getInventory().setItemStack(ITEM_SLOT, isHidden(player) ? TOGGLE_ITEM_HIDDEN : TOGGLE_ITEM_SHOWING);
    }

    private static ItemStack buildToggleItem(boolean hidden) {
        List<Component> lore = Stream.<Component>of(
                Component.space(),
                Component.text(" «Информация»", TextColor.color(0x65D1FC)),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("Статус: ", TextColor.color(0xFFF2E0)))
                        .append(hidden
                                ? Component.text("Скрыты ", NamedTextColor.RED).append(Component.text("(FPS+)", NamedTextColor.GRAY))
                                : Component.text("Отображаются", NamedTextColor.GREEN)),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("Позволяет убрать лишних", TextColor.color(0xFFF2E0))),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("игроков в лобби.", TextColor.color(0xFFF2E0))),
                Component.space(),
                Component.text("➥ ПКМ — переключить", NamedTextColor.YELLOW)
        ).map(c -> c.decoration(TextDecoration.ITALIC, false)).toList();

        return ItemStack.builder(hidden ? Material.GRAY_DYE : Material.LIME_DYE)
                .set(DataComponents.CUSTOM_NAME, hidden ? HIDER_TEXT_OFF : HIDER_TEXT_ON)
                .set(DataComponents.LORE, lore)
                .set(TOGGLE_ITEM_TAG, hidden ? ITEM_HIDDEN : ITEM_SHOWING)
                .build();
    }
}
