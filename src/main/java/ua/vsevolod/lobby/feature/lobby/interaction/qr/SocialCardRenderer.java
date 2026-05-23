package ua.vsevolod.lobby.feature.lobby.interaction.qr;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import ua.vsevolod.lobby.config.SocialsConfig;
import ua.vsevolod.lobby.util.Text;

/**
 * Builds the multi-link "social card" chat message that appears when a player clicks the
 * off-hand QR map, or anywhere else (e.g. a menu item) that wants to show the same list of
 * socials. Centralising it here means the QR card and the {@code [socials]} action prefix
 * stay visually in sync — change the layout once.
 *
 * <p>Link styling (icon glyph, format, fallback hover) comes from {@link QrCardConfig} so it
 * remains hot-reloadable from {@code config/ui/qr-card.yml}. Each social's URL / label /
 * colour / hint / hover description comes from {@link SocialsConfig} (single source of truth).</p>
 */
public final class SocialCardRenderer {

    private SocialCardRenderer() {}

    /**
     * Render the standard socials card, optionally with a custom header overriding
     * {@link QrCardConfig#header}. Pass {@code null} or blank to use the configured one.
     */
    public static Component render(String customHeader) {
        QrCardConfig cfg = QrCardConfig.get();
        SocialsConfig socials = SocialsConfig.get();

        String headerSource = (customHeader != null && !customHeader.isBlank())
                ? customHeader
                : cfg.header;

        Component message = Component.newline()
                .append(Text.raw(ua.vsevolod.lobby.util.Placeholders.apply(headerSource)).decoration(TextDecoration.ITALIC, false))
                .append(Component.newline())
                .append(Component.newline());

        for (SocialsConfig.Social social : socials.socials) {
            message = message.append(socialLine(social, cfg)).append(Component.newline());
        }

        if (cfg.footer != null && !cfg.footer.isBlank()) {
            message = message.append(Component.newline())
                    .append(Text.raw(ua.vsevolod.lobby.util.Placeholders.apply(cfg.footer)).decoration(TextDecoration.ITALIC, false));
        }
        return message;
    }

    /** One clickable line: icon, label, hint — friendly hover, full URL only at click time. */
    private static Component socialLine(SocialsConfig.Social social, QrCardConfig cfg) {
        String rendered = cfg.linkFormat
                .replace("{color}", social.color())
                .replace("{label}", social.label())
                .replace("{hint}",  social.hint())
                .replace("{url}",   social.url());

        String url = social.url();
        String clickUrl = url.startsWith("http") ? url : "https://" + url;
        String hover = (social.hover() != null && !social.hover().isBlank())
                ? social.hover()
                : cfg.hoverText;

        return Text.raw(rendered)
                .clickEvent(ClickEvent.openUrl(clickUrl))
                .hoverEvent(HoverEvent.showText(Text.raw(hover)))
                .decoration(TextDecoration.ITALIC, false);
    }
}
