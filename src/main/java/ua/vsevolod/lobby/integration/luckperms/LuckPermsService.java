package ua.vsevolod.lobby.integration.luckperms;

import me.lucko.luckperms.minestom.LuckPermsMinestom;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.jetbrains.annotations.Nullable;
import ua.vsevolod.lobby.config.LuckPermsConfig;
import ua.vsevolod.lobby.util.ServerLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Thin wrapper around the LuckPerms-Minestom bootstrap. All call sites go through here so
 * the "is LP enabled?" gate is checked in exactly one place — keeping the rest of the
 * codebase free of {@code import net.luckperms.*} when the integration is off.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #init(LuckPermsConfig)} — called once at server bootstrap. Reads the toggle
 *       from {@code luckperms.yml}; if disabled, leaves {@link #instance} {@code null} and
 *       every check below falls back to the legacy behaviour.</li>
 *   <li>{@link #hasPermission(UUID, String)} — null-safe check. Returns {@code false} if LP
 *       isn't running, the user hasn't been loaded yet, or the perm is unset/denied.</li>
 *   <li>{@link #isEnabled()} — for places that want to behave differently when LP is on
 *       (e.g. {@code AdminCommand.isAdmin} composing LP + BYPASS_USERS).</li>
 * </ol>
 */
public final class LuckPermsService {

    private static volatile @Nullable LuckPerms instance;
    private static volatile @Nullable LuckPermsConfig config;

    private LuckPermsService() {}

    /** Start LP if {@code cfg.enabled}; otherwise no-op. Safe to call once at boot. */
    public static synchronized void init(LuckPermsConfig cfg) {
        config = cfg;
        if (!cfg.enabled) {
            ServerLogger.get().info("LuckPerms integration: disabled (set enabled=true in config/system/luckperms.yml to turn on)");
            return;
        }
        try {
            Path data = Paths.get(cfg.dataDirectory);
            Files.createDirectories(data);
            instance = LuckPermsMinestom.builder(data)
                    .commands(cfg.registerCommands)
                    .enable();
            ServerLogger.get().info("LuckPerms integration: enabled (dataDirectory=" + data + ")");
        } catch (Throwable t) {
            // Don't take the whole server down if LP fails to start — log loudly and continue
            // with permissions resolved purely via BYPASS_USERS.
            ServerLogger.get().error("Failed to start LuckPerms: " + t.getMessage()
                    + " — falling back to BYPASS_USERS only");
            t.printStackTrace();
            instance = null;
        }
    }

    /** {@code true} if LP started successfully and is ready for permission queries. */
    public static boolean isEnabled() {
        return instance != null;
    }

    /** Returns the resolved {@code adminPermission} from config (or {@code "orjus.admin"} as fallback). */
    public static String adminPermission() {
        LuckPermsConfig c = config;
        return (c == null || c.adminPermission == null || c.adminPermission.isBlank())
                ? "orjus.admin"
                : c.adminPermission;
    }

    /**
     * Null-safe permission check. Returns {@code false} when LP isn't running, the user is
     * not yet cached (e.g. checked during async pre-login before LP loaded them) or the
     * permission is undefined / denied.
     */
    public static boolean hasPermission(UUID uuid, String permission) {
        LuckPerms lp = instance;
        if (lp == null || uuid == null || permission == null) return false;
        try {
            User user = lp.getUserManager().getUser(uuid);
            if (user == null) return false;
            CachedPermissionData data = user.getCachedData()
                    .getPermissionData(QueryOptions.defaultContextualOptions());
            return data.checkPermission(permission).asBoolean();
        } catch (Throwable t) {
            // Defensive — never let a runtime LP error reject a command silently
            ServerLogger.get().warn("LuckPerms perm check failed for " + uuid + " / " + permission + ": " + t.getMessage());
            return false;
        }
    }

    /** Shuts LP down — wire into the server shutdown hook. Idempotent. */
    public static synchronized void shutdown() {
        if (instance == null) return;
        try {
            LuckPermsMinestom.disable();
        } catch (Throwable t) {
            ServerLogger.get().warn("LuckPerms shutdown failed: " + t.getMessage());
        } finally {
            instance = null;
        }
    }
}
