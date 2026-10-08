package gg.fotia.futureui.asset;

import com.google.gson.JsonParser;
import gg.fotia.futureui.config.ConfigRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 外置资源读取及受清单管理的发布；每个输出目录独立清理旧文件。 */
final class ResourcePackFiles {
    private ResourcePackFiles() {}

    static void overrides(Path assets, Path staging, Set<String> overlays) throws IOException {
        Path root = assets.resolve("overrides");
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IOException("Resource overrides cannot be a symbolic link: " + root);
        try (var paths = Files.walk(root)) {
            for (Path source : paths.toList()) {
                if (Files.isSymbolicLink(source)) throw new IOException("Resource override cannot be a symbolic link: " + source);
                if (!Files.isRegularFile(source)) continue;
                Path relative = root.relativize(source);
                boolean common = relative.getNameCount() >= 3 && relative.getName(0).toString().equals("assets");
                boolean versioned = relative.getNameCount() >= 4 && overlays.contains(relative.getName(0).toString()) && relative.getName(1).toString().equals("assets");
                if (!common && !versioned) throw new IOException("Override must use assets/<namespace>/... or <configured-overlay>/assets/<namespace>/...: " + relative);
                if (relative.toString().replace('\\', '/').equals(PackManifest.ENTRY)) throw new IOException("Build receipt is reserved");
                copy(source, staging.resolve(relative));
            }
        }
    }

    static Path metadata(Path assets) throws IOException {
        Path file = assets.resolve("pack.yml");
        if (!ConfigRepository.read(file).text("namespace", "").equals("futureui"))
            throw new IOException("assets/pack.yml: namespace must remain futureui for generated fonts");
        return file;
    }

    static List<String> publish(Path staging, Path local, Path manifest) throws IOException {
        Set<String> managed = new LinkedHashSet<>();
        try (var paths = Files.walk(staging)) {
            for (Path source : paths.filter(Files::isRegularFile).sorted().toList()) {
                Path relative = staging.relativize(source);
                copy(source, local.resolve(relative));
                managed.add(relative.toString());
            }
        }
        if (Files.isRegularFile(manifest)) {
            for (var entry : JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonArray()) {
                String previous = entry.getAsString();
                if (managed.contains(previous)) continue;
                // 仅清理上次生成清单内、且仍位于输出目录中的文件。
                Path base = local.toAbsolutePath().normalize(), target = base.resolve(previous).normalize();
                if (target.startsWith(base) && !target.equals(base)) Files.deleteIfExists(target);
            }
        }
        return List.copyOf(managed);
    }

    static void copy(Path source, Path target) throws IOException {
        if (Files.isRegularFile(target) && Files.mismatch(source, target) == -1) return;
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "futureui-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
