package ua.vsevolod.lobby.feature.admin.config;

import java.util.Map;

/**
 * One reloadable subsystem config (tab, sidebar, npcs, …).
 *
 * <p>Lifecycle:</p>
 * <ol>
 *   <li>{@link #name()} — file name (without extension). Stored at {@code config/<name>.yml}.</li>
 *   <li>{@link #templateYaml()} — human-readable YAML text written to disk the FIRST time the
 *       file is missing. Should include comments explaining each key.</li>
 *   <li>{@link #parse(Map)} — convert parsed YAML map into a typed snapshot object.
 *       Must throw on malformed input — {@link ConfigManager} catches it and keeps the previous
 *       snapshot.</li>
 *   <li>{@link #apply(Object)} — atomically swap the snapshot the subsystem reads from.
 *       Implementations typically store it in an {@code AtomicReference} so live consumers see
 *       the new value on their next tick.</li>
 * </ol>
 */
public interface ConfigSection<T> {

    String name();

    String templateYaml();

    T parse(Map<String, Object> yaml) throws Exception;

    void apply(T snapshot);
}
