package gg.fotia.futureui.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** 默认菜单归档到独立目录；保留旧配置、菜单 ID 与用户修改。 */
final class DefaultMenuMigration {
    private static final String PREFIX = "menus/standard/";
    private record Move(Path source, Path target) {}

    private DefaultMenuMigration() {}

    static void migrate(Path root, List<String> defaults) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        Path menus = base.resolve("menus");
        List<Move> moves = new ArrayList<>();
        for (String resource : defaults) {
            if (!resource.startsWith(PREFIX)) continue;
            String name = resource.substring(PREFIX.length());
            if (name.contains("/") || name.contains("\\") || !name.endsWith(".yml")) continue;
            Path source = menus.resolve(name).normalize();
            Path target = base.resolve(resource).normalize();
            if (!source.startsWith(menus) || !target.startsWith(menus))
                throw new IOException("Menu migration path escapes menus: " + resource);
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) continue;
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Legacy menu is not a regular file: " + source);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    || Files.mismatch(source, target) != -1))
                throw new IOException("Conflicting menu files; preserve and reconcile both: " + source + " and " + target);
            moves.add(new Move(source, target));
        }
        // 所有冲突预检查完成后再移动，避免遇到冲突时只迁移了部分目录。
        for (Move move : moves) {
            if (Files.exists(move.target())) {
                preserveImplicitId(base, move.target());
                Files.move(move.source(), backupPath(base, move.source()));
                continue;
            }
            preserveImplicitId(base, move.source());
            Files.createDirectories(move.target().getParent());
            Files.move(move.source(), move.target());
        }
    }

    private static void preserveImplicitId(Path root, Path source) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            yaml.load(reader);
        } catch (InvalidConfigurationException error) {
            throw new IOException("Cannot migrate menu: " + source, error);
        }
        if (yaml.get("id") != null) return;
        String name = source.getFileName().toString();
        yaml.set("id", name.substring(0, name.length() - ".yml".length()));
        Files.copy(source, backupPath(root, source));
        Path temporary = Files.createTempFile(source.getParent(), "futureui-menu-", ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, source, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, source, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path backupPath(Path root, Path source) throws IOException {
        Path directory = root.resolve("generated/migrations/menu-folders").resolve(UUID.randomUUID().toString());
        Files.createDirectories(directory);
        return directory.resolve(source.getFileName());
    }
}
