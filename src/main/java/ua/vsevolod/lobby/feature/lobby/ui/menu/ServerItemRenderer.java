package ua.vsevolod.lobby.feature.lobby.ui.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.config.server.ServerEntry;
import ua.vsevolod.lobby.config.server.ServerInfo;
import ua.vsevolod.lobby.config.server.ServersConfig;
import ua.vsevolod.lobby.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the menu icon for a server — name, lore, online bar, tags and live status.
 *
 * <p>The name and lore templates live in {@code config/servers.yml} ({@link ServersConfig#itemName},
 * {@link ServersConfig#itemLore}). Shared by the config-driven {@link MenuManager} and the
 * hardcoded {@code LobbyModeSelectorMenu} so the look is identical and defined in one place.</p>
 */
public final class ServerItemRenderer {

    private ServerItemRenderer() {
    }

    /** Full item: server material + rendered name + lore. */
    public static ItemStack render(ServerInfo server) {
        return ItemStack.builder(server.material())
                .set(DataComponents.CUSTOM_NAME, name(server))
                .set(DataComponents.LORE, lore(server))
                .hideExtraTooltip()
                .build();
    }

    /** Rendered display name (italic disabled) — per-server override or shared template. */
    public static Component name(ServerInfo server) {
        ServersConfig cfg = ServersConfig.get();
        ServerEntry entry = cfg.servers.get(server.id());
        String template = (entry != null && entry.hasNameOverride()) ? entry.itemName() : cfg.itemName;
        return Text.raw(fill(template, server, cfg))
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Rendered lore lines (italic disabled) — per-server override or shared template.
     * {@code {tags}} expands to wrapped tag lines.
     */
    public static List<Component> lore(ServerInfo server) {
        ServersConfig cfg = ServersConfig.get();
        ServerEntry entry = cfg.servers.get(server.id());
        List<String> loreTemplate =
                (entry != null && entry.hasLoreOverride()) ? entry.itemLore() : cfg.itemLore;

        List<Component> out = new ArrayList<>();
        List<String> tagLines = wrapTags(server.tagsServer(), cfg);

        for (String template : loreTemplate) {
            int tagAt = template.indexOf("{tags}");
            if (tagAt >= 0) {
                String prefix = template.substring(0, tagAt);
                if (tagLines.isEmpty()) continue;
                for (String tagLine : tagLines) {
                    out.add(line(prefix + tagLine));
                }
                continue;
            }
            out.add(line(fill(template, server, cfg)));
        }
        return out;
    }

    private static Component line(String raw) {
        return Text.raw(raw).decoration(TextDecoration.ITALIC, false);
    }

    private static String fill(String template, ServerInfo server, ServersConfig cfg) {
        LobbyConfig.Project.SocialLinks links = LobbyConfig.Project.SOCIAL_LINKS;
        return template
                .replace("{server}", server.id())
                .replace("{world}", server.worldName())
                .replace("{version}", server.versionCore())
                .replace("{status}", server.getStatusName())
                .replace("{online}", Integer.toString(server.online()))
                .replace("{max}", Integer.toString(server.maxOnline()))
                .replace("{bar}", bar(server.online(), server.maxOnline(), cfg))
                .replace("{website}", links.website())
                .replace("{telegram}", links.telegram())
                .replace("{discord}", links.discord());
    }

    /** Builds the {@code {bar}} online indicator as a MiniMessage string. */
    private static String bar(int online, int max, ServersConfig cfg) {
        int segments = Math.max(1, cfg.barSegments);
        double percent = max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) online / max));
        int filled = (int) Math.round(percent * segments);
        if (online > 0 && filled == 0) filled = 1;
        if (filled > segments) filled = segments;

        String fillColor = percent >= 0.75 ? cfg.barColorHigh
                : percent >= 0.35 ? cfg.barColorMedium
                : cfg.barColorLow;

        StringBuilder b = new StringBuilder();
        b.append(tag(cfg.barColorBorder)).append('[');
        if (filled > 0) {
            b.append(tag(fillColor)).append("■".repeat(filled));
        }
        if (filled < segments) {
            b.append(tag(cfg.barColorEmpty)).append("□".repeat(segments - filled));
        }
        b.append(tag(cfg.barColorBorder)).append(']');
        return b.toString();
    }

    /** Groups tags into lines of at most {@code tagsMaxPerLine}, each tag prefixed with {@code #}. */
    private static List<String> wrapTags(String[] tags, ServersConfig cfg) {
        List<String> lines = new ArrayList<>();
        if (tags == null || tags.length == 0) return lines;

        int perLine = Math.max(1, cfg.tagsMaxPerLine);
        String tagColor = tag(cfg.tagColor);
        String sepColor = tag(cfg.tagSeparatorColor);

        StringBuilder current = new StringBuilder();
        int count = 0;
        for (String raw : tags) {
            if (raw == null || raw.isBlank()) continue;
            String name = raw.trim();
            if (!name.startsWith("#")) name = "#" + name;
            if (count > 0 && count % perLine == 0) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append(sepColor).append(" • ");
            current.append(tagColor).append(name);
            count++;
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    /** Normalises a config colour ({@code #RRGGBB} or {@code RRGGBB}) into a MiniMessage tag. */
    private static String tag(String hex) {
        if (hex == null || hex.isBlank()) return "<white>";
        String h = hex.startsWith("#") ? hex : "#" + hex;
        return "<" + h + ">";
    }
}
