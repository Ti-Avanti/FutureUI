package gg.fotia.futureui.asset;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** 可选的 CraftEngine 合包适配器；外部分发模式不会调用它。 */
public final class CraftEnginePackPublisher {
    private static final String MANIFEST = "generated/craftengine-managed-files.json";
    private CraftEnginePackPublisher() {}

    public static void prepare(Path root) throws IOException {
        Path manifest = root.resolve(MANIFEST), legacy = root.resolve("generated/managed-files.json");
        // 旧版一份清单管理两处输出，先保留 CE 的旧清单以便清理过时资源。
        if (!Files.exists(manifest) && Files.isRegularFile(legacy)) ResourcePackFiles.copy(legacy, manifest);
    }

    public static Path publish(Path root, Path craftEngine, ResourcePackCompiler.Result built) throws IOException {
        Path packs = craftEngine.resolve("resources"), output = packs.resolve("futureui");
        Files.createDirectories(packs);
        checkConflicts(packs, output, built.path());
        var settings = built.manifest().profiles().source().child("craftengine");
        CraftEnginePackSettings.synchronize(root, craftEngine, settings);
        Path manifest = root.resolve(MANIFEST);
        var files = ResourcePackFiles.publish(built.path(), output.resolve("resourcepack"), manifest);
        ResourcePackFiles.copy(root.resolve("assets/pack.yml"), output.resolve("pack.yml"));
        Files.writeString(manifest, new GsonBuilder().setPrettyPrinting().create().toJson(files), StandardCharsets.UTF_8);
        return CraftEnginePackSettings.distributedZip(craftEngine, settings);
    }

    private static void checkConflicts(Path packs, Path owned, Path generated) throws IOException {
        try (var directories = Files.list(packs)) {
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
                if (directory.equals(owned) || directory.getFileName().toString().startsWith(".")) continue;
                try (var files = Files.walk(generated)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        Path relative = generated.relativize(file);
                        if (relative.toString().equals("pack.mcmeta")) continue;
                        Path other = directory.resolve("resourcepack").resolve(relative);
                        if (Files.isRegularFile(other) && !Arrays.equals(Files.readAllBytes(other), Files.readAllBytes(file)))
                            throw new IOException("Resource conflict: " + other);
                    }
                }
            }
        }
    }
}
