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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ByteArrayWorldLoader implements ChunkLoader, AutoCloseable {

    private final byte[] worldBytes;
    private final String tempPrefix;

    private volatile Path tempDir;
    private volatile AnvilLoader delegate;

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
                return this.delegate;
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create temp world from bytes", e);
            }
        }
    }

    @Override
    public void loadInstance(Instance instance) {
        getOrCreateDelegate().loadInstance(instance);
    }

    @Override
    public @Nullable Chunk loadChunk(Instance instance, int chunkX, int chunkZ) {
        return getOrCreateDelegate().loadChunk(instance, chunkX, chunkZ);
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