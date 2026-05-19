package ua.vsevolod.lobby.feature.lobby.ui.menu;

import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.NpcActionExecutor;
import ua.vsevolod.lobby.feature.lobby.ui.menu.config.MenuDefinition;
import ua.vsevolod.lobby.feature.lobby.ui.menu.config.MenuItem;
import ua.vsevolod.lobby.feature.lobby.ui.menu.config.MenusConfigSection;
import ua.vsevolod.lobby.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and opens config-driven chest menus. Items are keyed by name, and
 * each item can occupy multiple slots (for decoration panes). Left/right click
 * commands are dispatched separately via {@link NpcActionExecutor#executeCommands}.
 */
public final class MenuManager {

    /** Tag on each ItemStack storing the item key (name in the items map). */
    private static final Tag<String> MENU_ITEM_KEY = Tag.String("lobby-menu-item-key");

    private final NpcActionExecutor actionExecutor;
    private final Map<UUID, Open> open = new ConcurrentHashMap<>();

    public MenuManager(NpcActionExecutor actionExecutor) {
        this.actionExecutor = actionExecutor;
    }

    public void register(GlobalEventHandler events) {
        events.addListener(InventoryPreClickEvent.class, this::onClick);
        events.addListener(PlayerDisconnectEvent.class, e -> open.remove(e.getPlayer().getUuid()));
    }

    /**
     * Opens menu {@code id} for {@code player}, honouring visibility rules.
     * Returns false if not found or player lacks access.
     */
    public boolean openFor(Player player, String id) {
        MenuDefinition def = MenusConfigSection.INSTANCE.current().menus().get(id);
        if (def == null) return false;

        boolean bypass = LobbyConfig.Settings.BYPASS_USERS.contains(player.getUsername());
        if (def.visibility() == MenuDefinition.Visibility.BYPASS_ONLY && !bypass) {
            player.sendMessage("§cУ тебя нет доступа к этому меню.");
            return false;
        }

        Inventory inventory = buildInventory(def, player);
        open.put(player.getUuid(), new Open(def.id(), inventory));
        player.openInventory(inventory);
        return true;
    }

    private Inventory buildInventory(MenuDefinition def, Player player) {
        InventoryType type = inventoryTypeFor(def.rows());
        Inventory inventory = new Inventory(type, Text.raw(def.menuTitle()));

        for (Map.Entry<String, MenuItem> entry : def.items().entrySet()) {
            String itemKey = entry.getKey();
            MenuItem item  = entry.getValue();

            Material material = Material.fromKey(item.material());
            if (material == null) material = Material.BARRIER;

            ItemStack.Builder builder = ItemStack.builder(material)
                    .set(MENU_ITEM_KEY, itemKey);

            if (item.displayName() != null) {
                builder.set(DataComponents.CUSTOM_NAME,
                        Text.raw(substitute(item.displayName(), player))
                                .decoration(TextDecoration.ITALIC, false));
            }
            if (!item.lore().isEmpty()) {
                List<net.kyori.adventure.text.Component> loreLines = new ArrayList<>(item.lore().size());
                for (String line : item.lore()) {
                    loreLines.add(Text.raw(substitute(line, player))
                            .decoration(TextDecoration.ITALIC, false));
                }
                builder.set(DataComponents.LORE, loreLines);
            }
            if (item.glint()) builder.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

            ItemStack built = builder.hideExtraTooltip().build();

            for (int slot : item.slots()) {
                inventory.setItemStack(slot, built);
            }
        }
        return inventory;
    }

    private static InventoryType inventoryTypeFor(int rows) {
        return switch (rows) {
            case 1 -> InventoryType.CHEST_1_ROW;
            case 2 -> InventoryType.CHEST_2_ROW;
            case 3 -> InventoryType.CHEST_3_ROW;
            case 4 -> InventoryType.CHEST_4_ROW;
            case 5 -> InventoryType.CHEST_5_ROW;
            default -> InventoryType.CHEST_6_ROW;
        };
    }

    private static String substitute(String s, Player player) {
        if (s.indexOf('{') < 0) return s;
        return s.replace("{player}", player.getUsername())
                .replace("{online}", Integer.toString(
                        MinecraftServer.getConnectionManager().getOnlinePlayerCount()));
    }

    private void onClick(InventoryPreClickEvent event) {
        Player player = event.getPlayer();
        Open opened = open.get(player.getUuid());
        if (opened == null || event.getInventory() != opened.inventory()) return;

        event.setCancelled(true);
        String itemKey = event.getClickedItem().getTag(MENU_ITEM_KEY);
        if (itemKey == null) return;

        MenuDefinition def = MenusConfigSection.INSTANCE.current().menus().get(opened.menuId());
        if (def == null) return;

        MenuItem item = def.items().get(itemKey);
        if (item == null) return;

        // True left/right dispatch via Click sealed interface.
        Click click = event.getClick();
        boolean isRight = click instanceof Click.Right || click instanceof Click.RightShift;

        List<String> commands;
        if (!isRight && !item.leftClickCommands().isEmpty()) {
            commands = item.leftClickCommands();
        } else if (isRight && !item.rightClickCommands().isEmpty()) {
            commands = item.rightClickCommands();
        } else if (!item.leftClickCommands().isEmpty()) {
            // Right-click with no right_click_commands — fall back to left_click_commands.
            commands = item.leftClickCommands();
        } else {
            return; // no commands configured
        }

        actionExecutor.executeCommands(player, commands);
    }

    /** Close all open menus — called on /reload to avoid stale item references. */
    public void closeAll() {
        for (UUID id : new ArrayList<>(open.keySet())) {
            Player p = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(id);
            if (p != null) p.closeInventory();
        }
        open.clear();
    }

    public List<String> menuIds() {
        return new ArrayList<>(MenusConfigSection.INSTANCE.current().menus().keySet());
    }

    private record Open(String menuId, Inventory inventory) {}
}
