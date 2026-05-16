package ua.vsevolod.lobby.feature.lobby.player.visibility;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.tag.Tag;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.util.Text;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static net.kyori.adventure.text.format.NamedTextColor.GRAY;

public final class PlayerHider {

    private static final int ITEM_SLOT = 7;

    private static final Tag<String> TOGGLE_ITEM_TAG = Tag.String("lobby-toggle-players");
    private static final String ITEM_SHOWING = "showing";
    private static final String ITEM_HIDDEN = "hidden";

    // Игроки, у которых включено "не видеть других"
    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();

    public void register(EventNode<Event> node) {
        node.addListener(PlayerSpawnEvent.class, this::onSpawn);
        node.addListener(PlayerUseItemEvent.class, this::onUseItem);
        node.addListener(PlayerDisconnectEvent.class, this::onDisconnect);
    }

    private void onSpawn(PlayerSpawnEvent event) {
        Player player = event.getPlayer();

        // applyWorldVisibilityRule already calls updateViewerRule internally.
        applyWorldVisibilityRule(player);

        // Cache the player list once — was iterated twice (refreshTabFor + the loop below).
        var online = MinecraftServer.getConnectionManager().getOnlinePlayers();
        refreshTabFor(player, online);

        for (Player other : online) {
            if (other == player) continue;
            if (isHidden(other)) {
                hideFromTab(other, player);
                other.updateViewerRule();
            }
        }

        updateToggleItem(player);
    }

    private void onUseItem(PlayerUseItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemStack();

        if (!isToggleItem(item)) {
            return;
        }

        event.setCancelled(true);
        toggle(player);
    }

    private void onDisconnect(PlayerDisconnectEvent event) {
        hiddenPlayers.remove(event.getPlayer().getUuid());
    }

    public void toggle(Player player) {
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

        // applyWorldVisibilityRule already calls updateViewerRule — don't double-recalc.
        applyWorldVisibilityRule(player);
        refreshTabFor(player, MinecraftServer.getConnectionManager().getOnlinePlayers());
        updateToggleItem(player);
    }

    public boolean isHidden(Player player) {
        return hiddenPlayers.contains(player.getUuid());
    }

    /**
     * Скрывает других игроков в мире, но не трогает TAB.
     */
    private void applyWorldVisibilityRule(Player player) {
        if (isHidden(player)) {
            player.updateViewerRule(entity -> {
                if (entity == player) {
                    return true;
                }
                return !(entity instanceof Player);
            });
        } else {
            player.updateViewerRule((Entity entity) -> true);
        }
    }

    /**
     * Полностью пересобирает TAB именно для одного игрока.
     *
     * Если у player включено скрытие:
     * - убираем всех остальных игроков из TAB
     *
     * Если выключено:
     * - возвращаем всех остальных игроков в TAB
     */
    private void refreshTabFor(Player player, java.util.Collection<Player> onlinePlayers) {
        boolean hidden = isHidden(player);
        for (Player target : onlinePlayers) {
            if (target == player) continue;
            if (hidden) hideFromTab(player, target);
            else        showInTab(player, target);
        }
    }

    /**
     * Убрать target из TAB у viewer.
     *
     * В Minestom для этого есть отдельный packet:
     * PlayerInfoRemovePacket(List<UUID>).
     */
    private void hideFromTab(Player viewer, Player target) {
        viewer.getPlayerConnection().sendPacket(
                new PlayerInfoRemovePacket(List.of(target.getUuid()))
        );
    }

    /**
     * Вернуть target в TAB у viewer.
     *
     * Для этого шлём PlayerInfoUpdatePacket c ADD_PLAYER.
     * У Entry есть поле listed, и оно как раз относится к player-list/TAB.
     */
    private void showInTab(Player viewer, Player target) {
        PlayerInfoUpdatePacket.Entry entry = new PlayerInfoUpdatePacket.Entry(
                target.getUuid(),
                target.getUsername(),
                List.of(), // если ты не используешь кастомные skin properties здесь
                true,      // listed = есть в TAB
                target.getLatency(),
                target.getGameMode(),
                target.getDisplayName(),
                null,      // chatSession
                0,         // listOrder
                true       // displayHat
        );

        viewer.getPlayerConnection().sendPacket(
                new PlayerInfoUpdatePacket(
                        EnumSet.of(PlayerInfoUpdatePacket.Action.ADD_PLAYER, PlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                        List.of(entry)
                )
        );
    }

    private void updateToggleItem(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setItemStack(ITEM_SLOT, createToggleItem(isHidden(player)));
    }

    private final static Component HIDER_TEXT = Text.c("&#F1BB58&lИ&#F1B958&lг&#F1B658&lр&#F1B458&lо&#F1B158&lк&#F1AF58&lи");
    private final static Component HIDER_TEXT_ON = HIDER_TEXT.append(Component.space())
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("Не скрыты", TextColor.color(0x8EB126)))
            .append(Component.text("]", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false);
    private final static Component HIDER_TEXT_OFF = HIDER_TEXT.append(Component.space())
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("Скрыты", TextColor.color(0xFA3B3B)))
            .append(Component.text("]", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false);

    private ItemStack createToggleItem(boolean hidden) {
        var space = Component.text(" - ", GRAY);

        List<Component> lore = Stream.<Component>of(
            Component.space(),
            Component.text(" «Информация»", TextColor.color(0x65D1FC)),
            Component.empty().append(space).append(Component.text("Статус: ", TextColor.color(0xFFF2E0)))
                    .append(hidden ? Component.text("Скрыты ", NamedTextColor.RED).append(Component.text("(FPS+)", GRAY)) : Component.text("Отображаются", NamedTextColor.GREEN)),
            Component.empty().append(space).append(Component.text("Позволяет убрать лишних", TextColor.color(0xFFF2E0))),
            Component.empty().append(space).append(Component.text("игроков в лобби.", TextColor.color(0xFFF2E0))),
            Component.space(),
            Component.text("➥ Нажмите, чтобы переключиться", NamedTextColor.YELLOW)

        )
                .map(component -> component.decoration(TextDecoration.ITALIC, false))
                .toList();

        return ItemStack.builder(hidden ? Material.GRAY_DYE : Material.LIME_DYE)
                .set(DataComponents.CUSTOM_NAME, hidden ? HIDER_TEXT_OFF
                        : HIDER_TEXT_ON)
                .set(DataComponents.LORE,
                        lore
                )
                .set(TOGGLE_ITEM_TAG, hidden ? ITEM_HIDDEN : ITEM_SHOWING)
                .build();
    }

    private boolean isToggleItem(ItemStack item) {
        String state = item.getTag(TOGGLE_ITEM_TAG);
        return ITEM_SHOWING.equals(state) || ITEM_HIDDEN.equals(state);
    }
}
