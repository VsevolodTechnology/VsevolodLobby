package ua.vsevolod.lobby.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for the project's social-media entries.
 *
 * <p>Backed by {@code config/socials.yml}. Each {@link Social} carries everything needed to
 * render a clickable card line: the URL, label, accent colour, short hint and a per-link hover
 * description (so hover never has to show the raw URL).</p>
 *
 * <p>For backwards-compat with display templates, the loader also extracts top-level URL
 * placeholders ({@code {discord}}, {@code {telegram}}, {@code {website}}, …) and their short
 * forms ({@code {*-short}}) — see {@link #resolveAll(String)}.</p>
 */
@Configuration
public final class SocialsConfig {

    private static final Path FILE = Paths.get("config", "socials.yml");
    private static volatile SocialsConfig instance;

    @Comment({
            "Соцсети проекта — единая точка правды. Список расширяемый:",
            "добавь сюда сколько угодно записей с любыми ключами — youtube, vk, tiktok, что угодно.",
            "Каждая запись автоматически:",
            "  • даёт плейсхолдеры везде в чате/welcome/holograms/tab/menus:",
            "      {ключ}         — полный URL",
            "      {ключ-url}     — то же что {ключ}",
            "      {ключ-short}   — без https:// и хвостового /",
            "      {ключ-label}   — отображаемое имя",
            "      {ключ-hint}    — короткое описание",
            "      {ключ-color}   — MiniMessage-цвет иконки",
            "  • появляется как клик-кнопка в карте соцсетей (QR-карта + действие [socials]).",
            "",
            "Поля:",
            "  key   — идентификатор для плейсхолдеров (регистр сохраняется).",
            "  url   — полная ссылка (со схемой https://).",
            "  label — отображаемое имя на кнопке.",
            "  color — MiniMessage-тег цвета (например, <#5865F2>).",
            "  hint  — короткое описание справа от названия.",
            "  hover — что показать при наведении (НЕ URL — описание/призыв)."
    })
    public List<Social> socials = List.of(
            new Social(
                    "discord",
                    "https://discord.com/invite/BNCXWbHRsC",
                    "Discord",
                    "<#5865F2>",
                    "сообщество · поддержка · ивенты",
                    "<#5865F2>◆ <#FFF2E0>Открыть наш <#5865F2>Discord<#FFF2E0>-сервер"
            ),
            new Social(
                    "telegram",
                    "https://t.me/OrjusTg",
                    "Telegram",
                    "<#3FA7E0>",
                    "новости · анонсы · обновления",
                    "<#3FA7E0>◆ <#FFF2E0>Открыть <#3FA7E0>Telegram<#FFF2E0>-канал"
            ),
            new Social(
                    "website",
                    "https://studio.orjus.ru/",
                    "Сайт",
                    "<#C58AF0>",
                    "официальный сайт студии",
                    "<#C58AF0>◆ <#FFF2E0>Открыть сайт <#C58AF0>orjus.ru"
            )
    );

    /** One social entry — URL plus all display metadata. */
    public record Social(
            String key,
            String url,
            String label,
            String color,
            String hint,
            String hover
    ) {
        public Social {
            if (color == null || color.isBlank()) color = "<#C58AF0>";
            if (hint  == null) hint  = "";
            if (hover == null || hover.isBlank()) hover = "<#C58AF0>▶ <#FFF2E0>Кликни, чтобы открыть";
        }
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    /** Lazy key → entry index. Rebuilt on each {@link #load()} via the volatile swap. */
    private volatile transient Map<String, Social> index;

    private Map<String, Social> index() {
        Map<String, Social> i = index;
        if (i != null) return i;
        synchronized (this) {
            i = index;
            if (i != null) return i;
            // Preserve original case so user-defined keys like "loxPizdaTrid" work as
            // {loxPizdaTrid} placeholders verbatim. byKey() still supports case-insensitive
            // lookup as a fallback for friendlier matching.
            Map<String, Social> m = new LinkedHashMap<>();
            for (Social s : socials) {
                if (s.key() == null || s.key().isBlank()) continue;
                m.put(s.key(), s);
            }
            // Legacy aliases for display templates ({studio}/{site} → website).
            Social website = null;
            for (Social s : socials) {
                if ("website".equalsIgnoreCase(s.key())) { website = s; break; }
            }
            if (website != null) {
                m.putIfAbsent("studio", website);
                m.putIfAbsent("site",   website);
            }
            this.index = m;
            return m;
        }
    }

    public Social byKey(String key) {
        if (key == null) return null;
        Map<String, Social> idx = index();
        Social s = idx.get(key);
        if (s != null) return s;
        for (Map.Entry<String, Social> e : idx.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }

    public String urlOf(String key) {
        Social s = byKey(key);
        return s == null ? null : s.url();
    }

    // ── Placeholder helpers (used by every text-rendering surface) ────────────

    /** Resolve a single {@code {key}} placeholder to its URL. Unknown placeholders pass through. */
    public String resolve(String urlOrPlaceholder) {
        if (urlOrPlaceholder == null) return null;
        String s = urlOrPlaceholder.trim();
        if (s.length() >= 3 && s.charAt(0) == '{' && s.charAt(s.length() - 1) == '}') {
            String key = s.substring(1, s.length() - 1);
            Social social = byKey(key);
            if (social != null) return social.url();
        }
        return urlOrPlaceholder;
    }

    /**
     * Substitute every social placeholder in arbitrary text. For every {@link Social} entry
     * the following placeholders are recognised (case-preserved against {@code key}):
     * <ul>
     *   <li>{@code {key}} / {@code {key-url}} — full URL</li>
     *   <li>{@code {key-short}} — URL without scheme/trailing slash</li>
     *   <li>{@code {key-label}} / {@code {key-hint}} / {@code {key-color}} — display fields</li>
     * </ul>
     */
    public String resolveAll(String text) {
        if (text == null || text.indexOf('{') < 0) return text;
        for (Social s : socials) {
            if (s.key() == null || s.key().isBlank()) continue;
            String k = s.key();
            String url = s.url() == null ? "" : s.url();
            text = text
                    .replace("{" + k + "}",         url)
                    .replace("{" + k + "-url}",     url)
                    .replace("{" + k + "-short}",   shorten(url))
                    .replace("{" + k + "-label}",   s.label() == null ? k : s.label())
                    .replace("{" + k + "-hint}",    s.hint()  == null ? ""  : s.hint())
                    .replace("{" + k + "-color}",   s.color() == null ? ""  : s.color());
        }
        // Legacy aliases for display templates ({studio}/{site} → website URL).
        Social website = byKey("website");
        if (website != null) {
            String url = website.url() == null ? "" : website.url();
            text = text
                    .replace("{studio}",       url)
                    .replace("{studio-short}", shorten(url))
                    .replace("{site}",         url)
                    .replace("{site-short}",   shorten(url));
        }
        return text;
    }

    /** {@code https://t.me/OrjusTg/} → {@code t.me/OrjusTg}. */
    public static String shorten(String url) {
        if (url == null) return "";
        String s = url.replaceFirst("^https?://", "");
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static SocialsConfig get() {
        SocialsConfig c = instance;
        return c != null ? c : load();
    }

    public static synchronized SocialsConfig load() {
        SocialsConfig c;
        try {
            c = YamlConfigurations.update(FILE, SocialsConfig.class);
        } catch (Exception e) {
            System.err.println("[SocialsConfig] Failed to load " + FILE + ": " + e.getMessage() + " — using defaults");
            c = new SocialsConfig();
        }
        instance = c;
        return c;
    }

    public static void init() {
        load();
        ConfigReload.register("socials", SocialsConfig::load);
    }
}
