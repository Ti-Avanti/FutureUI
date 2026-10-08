package gg.fotia.futureui.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** 将旧资源配置迁入可编辑文件；迁移前备份，重复启动不覆盖用户修改。 */
final class AssetConfiguration {
    private AssetConfiguration() {}

    static void migrate(JavaPlugin plugin, Path root, Path craftEngine) throws IOException {
        migrateShaders(root);
        Path metadata = root.resolve("assets/pack.yml");
        Path previous = craftEngine == null ? null : craftEngine.resolve("resources/futureui/pack.yml");
        if (!Files.exists(metadata) && previous != null && Files.isRegularFile(previous)) {
            Files.createDirectories(metadata.getParent());
            Files.copy(previous, metadata);
        }
        Path imagesFile = root.resolve("assets/images.yml");
        if (!Files.isRegularFile(imagesFile)) return;
        YamlConfiguration images = read(imagesFile);
        boolean themeMove = images.contains("canvas") || images.contains("native-widgets");
        boolean iconMove = images.getString("images.shop-emblem.builtin", "").equals("pixel-shop");
        if (!themeMove && !iconMove) return;
        Path backup = root.resolve("generated/migrations/assets-external/images.yml");
        Files.createDirectories(backup.getParent());
        if (!Files.exists(backup)) Files.copy(imagesFile, backup);
        if (themeMove) {
            Path themeFile = root.resolve("assets/theme.yml");
            boolean existing = Files.isRegularFile(themeFile);
            YamlConfiguration theme = existing ? read(themeFile) : bundled(plugin, "assets/theme.yml");
            for (String key : List.of("canvas", "native-widgets")) {
                ConfigurationSection section = images.getConfigurationSection(key);
                if (section == null) continue;
                if (!existing) theme.set(key, null);
                for (String child : section.getKeys(true)) {
                    if (!section.isConfigurationSection(child) && (!existing || !theme.contains(key + "." + child))) {
                        theme.set(key + "." + child, section.get(child));
                        theme.setComments(key + "." + child, section.getComments(child));
                    }
                }
                images.set(key, null);
            }
            write(themeFile, theme);
        }
        if (iconMove) {
            images.set("images.shop-emblem.builtin", null);
            if (!images.contains("images.shop-emblem.file")) images.set("images.shop-emblem.file", "shop-emblem-pixel.png");
            images.setComments("images.shop-emblem.file", List.of("图片源文件位于 assets/textures，可直接替换；执行 /fui reload 后生效。"));
        }
        images.options().setHeader(List.of("图片注册配置；主题颜色和控件外观请修改 assets/theme.yml。", "file 相对于 assets/textures；vanilla 引用客户端已有的原版材质。"));
        write(imagesFile, images);
        plugin.getLogger().info("资源配置已迁移到 FutureUI/assets，旧配置已备份到 generated/migrations/assets-external。");
    }

    static Node load(Path root) throws IOException {
        Node images = Node.of(java.util.Map.of());
        Path imageDirectory = root.resolve("assets/images");
        if (Files.isDirectory(imageDirectory)) try (var paths = Files.list(imageDirectory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".yml")).sorted().toList())
                images = images.merge(ConfigRepository.read(path));
        }
        // 主文件优先，管理员可以覆盖插件随附的图片增量配置。
        images = images.merge(ConfigRepository.read(root.resolve("assets/images.yml")));
        Node versions = ConfigRepository.readLiteralKeys(root.resolve("assets/versions.yml"));
        gg.fotia.futureui.asset.PackProfiles.parse(versions);
        java.util.Map<String,Object> themes = new java.util.LinkedHashMap<>();
        Path directory = root.resolve("assets/themes");
        if (Files.isDirectory(directory)) try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".yml")).sorted().toList())
                themes.put(path.getFileName().toString().replaceFirst("\\.yml$", ""), ConfigRepository.read(path));
        }
        gg.fotia.futureui.asset.MenuThemeAssets.validate(Node.of(themes));
        Path viewportFile=root.resolve("assets/viewports.yml");
        Node viewports=Files.isRegularFile(viewportFile)?ConfigRepository.read(viewportFile):Node.of(java.util.Map.of());
        gg.fotia.futureui.asset.ViewportRegions.parse(viewports);
        return images.merge(ConfigRepository.read(root.resolve("assets/theme.yml")))
                .merge(Node.of(java.util.Map.of("versions", versions, "themes", themes,"viewports",viewports)));
    }

    private static void migrateShaders(Path root) throws IOException {
        // 旧版自定义着色器只属于 1.21.11，原样迁入该版本；其它版本安装各自的默认适配。
        for (String name : List.of("rendertype_text.vsh", "rendertype_text.fsh")) {
            Path source = root.resolve("assets/shaders/" + name), target = root.resolve("assets/shaders/1_21_11/" + name);
            if (!Files.isRegularFile(source) || Files.exists(target)) continue;
            Path backup = root.resolve("generated/migrations/multiversion-shaders/" + name);
            Files.createDirectories(backup.getParent());
            if (!Files.exists(backup)) Files.copy(source, backup);
            Files.createDirectories(target.getParent());
            Files.move(source, target);
        }
    }

    private static YamlConfiguration read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { return read(reader); }
    }

    private static YamlConfiguration bundled(JavaPlugin plugin, String name) throws IOException {
        try (InputStream stream = plugin.getResource(name)) {
            if (stream == null) throw new IOException("Missing bundled file: " + name);
            return read(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static YamlConfiguration read(Reader reader) throws IOException {
        YamlConfiguration result = new YamlConfiguration();
        try { result.load(reader); return result; }
        catch (Exception error) { throw new IOException("Invalid asset configuration", error); }
    }

    private static void write(Path file, YamlConfiguration value) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "asset-", ".tmp");
        try {
            Files.writeString(temporary, value.saveToString(), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
