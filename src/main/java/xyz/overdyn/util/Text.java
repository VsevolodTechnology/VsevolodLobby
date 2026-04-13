package xyz.overdyn.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minestom.server.network.packet.server.common.ShowDialogPacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
//https://github.com/allycraftmc/minestom-perms/blob/main/build.gradle.kts
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final Map<String, Component> CACHE = new ConcurrentHashMap<>();

    private Text() {
    }

    public static Component c(String text) {
        return CACHE.computeIfAbsent(text, LEGACY::deserialize);
    }

    /**
     * Для динамических строк (ping, online и т.д.)
     */
    public static Component raw(String text) {
        return LEGACY.deserialize(text);
    }

    public static void clearCache() {
        CACHE.clear();
    }
}