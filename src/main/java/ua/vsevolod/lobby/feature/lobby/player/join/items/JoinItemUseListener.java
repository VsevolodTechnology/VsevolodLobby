package ua.vsevolod.lobby.feature.lobby.player.join.items;

import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.NpcActionExecutor;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispatches click actions for items tagged with {@link JoinItemManager#JOIN_ITEM_ID}.
 * Right-click ({@link PlayerUseItemEvent}) fires the right action; left-click (drop)
 * fires the left action.
 */
public final class JoinItemUseListener implements LobbyEventRegistration {

    private static final long COOLDOWN_MS = 300;

    private final NpcActionExecutor executor;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public JoinItemUseListener(NpcActionExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerUseItemEvent.class, event -> {
            if (event.getHand() != PlayerHand.MAIN) return;
            ItemStack item = event.getItemStack();
            String id = item.getTag(JoinItemManager.JOIN_ITEM_ID);
            if (id == null) return;

            event.setCancelled(true);

            long now = System.currentTimeMillis();
            Long last = lastUse.put(event.getPlayer().getUuid(), now);
            if (last != null && (now - last) < COOLDOWN_MS) return;

            findById(id).map(JoinItemDefinition::rightAction)
                    .ifPresent(action -> executor.execute(event.getPlayer(), action));
        });

        // Left-click on hotbar item = ItemDropEvent (Q drop). Catch it to fire left action.
        handler.addListener(ItemDropEvent.class, event -> {
            String id = event.getItemStack().getTag(JoinItemManager.JOIN_ITEM_ID);
            if (id == null) return;
            event.setCancelled(true);
            findById(id).map(JoinItemDefinition::leftAction)
                    .ifPresent(action -> {
                        if (!action.isNone()) executor.execute(event.getPlayer(), action);
                    });
        });

        // Swap-hands (F) on tagged item — also block since it's a UI control item.
        handler.addListener(PlayerSwapItemEvent.class, event -> {
            String main = event.getMainHandItem().getTag(JoinItemManager.JOIN_ITEM_ID);
            String off  = event.getOffHandItem().getTag(JoinItemManager.JOIN_ITEM_ID);
            if (main != null || off != null) event.setCancelled(true);
        });
    }

    private Optional<JoinItemDefinition> findById(String id) {
        for (JoinItemDefinition def : JoinItemsConfigSection.INSTANCE.current().items()) {
            if (def.id().equals(id)) return Optional.of(def);
        }
        return Optional.empty();
    }
}
