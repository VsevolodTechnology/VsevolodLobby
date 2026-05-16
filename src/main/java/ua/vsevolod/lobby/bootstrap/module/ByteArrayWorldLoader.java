package ua.vsevolod.lobby.bootstrap.module;

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.ChunkLoader;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.anvil.AnvilLoader;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ByteArrayWorldLoader implements ChunkLoader, AutoCloseable {

    /** {@code r.<rx>.<rz>.mca} — one region = 32×32 chunks. */
    private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private final byte[] worldBytes;
    private final String tempPrefix;

    private volatile Path tempDir;
    private volatile AnvilLoader delegate;
    /**
     * Set of {@code (rx,rz)} keys for region files that physically exist in the saved world.
     * Any chunk whose region is NOT in here is rejected immediately by {@link #loadChunk}
     * with {@code null} — Minestom treats null as "no chunk, fall through to generator".
     * Since the lobby has no generator set, the chunk simply stays unloaded and the client
     * sees void. This avoids AnvilLoader probing the filesystem for non-existent .mca files
     * (the Spark profile caught 0.30 s of FJ-pool CPU per chunk-load cycle on this path).
     */
    private volatile Set<Long> presentRegions;

    public ByteArrayWorldLoader(byte[] worldBytes) {
        this(worldBytes, "minestom-world-");
    }

    public ByteArrayWorldLoader(byte[] worldBytes, String tempPrefix) {
        this.worldBytes = worldBytes;
        this.tempPrefix = tempPrefix;
    }

    private AnvilLoader getOrCreateDelegate() {
        AnvilLoader loader = delegate;
        if (loader != null) {
            return loader;
        }

        synchronized (this) {
            if (delegate != null) {
                return delegate;
            }

            try {
                Path dir = Files.createTempDirectory(tempPrefix);
                unzip(worldBytes, dir);

                this.tempDir = dir;
                this.delegate = new AnvilLoader(dir);
                this.presentRegions = scanRegions(dir);
                System.out.println("[ByteArrayWorldLoader] Lobby loaded — "
                        + presentRegions.size() + " region file(s) (chunks outside are skipped).");
                return this.delegate;
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create temp world from bytes", e);
            }
        }
    }

    /**
     * Walks the unzipped world dir for {@code r.X.Z.mca} files and returns the (rx, rz)
     * coordinates as packed-long keys. Cheap one-time cost at world load.
     */
    private static Set<Long> scanRegions(Path worldDir) {
        Set<Long> out = new HashSet<>();
        try (Stream<Path> walk = Files.walk(worldDir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                Matcher m = REGION_NAME.matcher(p.getFileName().toString());
                if (m.matches()) {
                    int rx = Integer.parseInt(m.group(1));
                    int rz = Integer.parseInt(m.group(2));
                    out.add(regionKey(rx, rz));
                }
            });
        } catch (IOException e) {
            System.err.println("[ByteArrayWorldLoader] Region scan failed: " + e.getMessage());
        }
        return out;
    }

    private static long regionKey(int rx, int rz) {
        return (((long) rx) << 32) | (rz & 0xFFFFFFFFL);
    }

    @Override
    public void loadInstance(Instance instance) {
        getOrCreateDelegate().loadInstance(instance);
    }

    @Override
    public @Nullable Chunk loadChunk(Instance instance, int chunkX, int chunkZ) {
        AnvilLoader loader = getOrCreateDelegate();
        // Bail before touching AnvilLoader if this chunk is in a region that doesn't exist
        // in the saved world. AnvilLoader would otherwise try to open the missing .mca file
        // (Spark profile flagged this as 0.30 s of FJ-pool CPU per parkour cycle); the void
        // outside the lobby map should simply stay unloaded for the client.
        Set<Long> regions = presentRegions;
        if (regions != null) {
            int rx = chunkX >> 5;
            int rz = chunkZ >> 5;
            if (!regions.contains(regionKey(rx, rz))) {
                return null;
            }
        }
        return loader.loadChunk(instance, chunkX, chunkZ);
    }

    @Override
    public void saveInstance(Instance instance) {
        getOrCreateDelegate().saveInstance(instance);
    }

    @Override
    public void saveChunk(Chunk chunk) {
        getOrCreateDelegate().saveChunk(chunk);
    }

    @Override
    public void unloadChunk(Chunk chunk) {
        AnvilLoader loader = delegate;
        if (loader != null) {
            loader.unloadChunk(chunk);
        }
    }

    @Override
    public boolean supportsParallelLoading() {
        return true;
    }

    @Override
    public boolean supportsParallelSaving() {
        return true;
    }

    @Override
    public void close() {
        Path dir = tempDir;
        if (dir == null) {
            return;
        }

        try {
            deleteRecursively(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete temp world dir: " + dir, e);
        } finally {
            tempDir = null;
            delegate = null;
        }
    }

    private static void unzip(byte[] zipBytes, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = targetDir.resolve(entry.getName()).normalize();

                if (!resolved.startsWith(targetDir)) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Path parent = resolved.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zis, resolved, StandardCopyOption.REPLACE_EXISTING);
                }

                zis.closeEntry();
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }
}