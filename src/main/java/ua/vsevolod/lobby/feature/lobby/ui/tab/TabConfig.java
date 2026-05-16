package ua.vsevolod.lobby.feature.lobby.ui.tab;

import java.util.List;

/**
 * Snapshot of {@code config/tab.yml}. Immutable, swapped via {@link java.util.concurrent.atomic.AtomicReference}.
 *
 * <p>Supported placeholders inside header/footer lines and {@link #msptBypassTemplate}:</p>
 * <ul>
 *   <li>{@code {ping}}   — current player's ping in ms</li>
 *   <li>{@code {online}} — total online player count</li>
 *   <li>{@code {time}}   — server local time formatted by {@link #timeFormat}</li>
 *   <li>{@code {mspt}}   — last tick duration; for BYPASS users this gets wrapped via
 *       {@link #msptBypassTemplate} and the result substitutes {@code {mspt}}.
 *       For regular players {@code {mspt}} resolves to empty string.</li>
 *   <li>{@code {player}} — receiving player's username</li>
 * </ul>
 */
public record TabConfig(
        long updateIntervalMs,
        String timeFormat,
        List<String> header,
        List<String> footer,
        String msptBypassTemplate
) {
}
