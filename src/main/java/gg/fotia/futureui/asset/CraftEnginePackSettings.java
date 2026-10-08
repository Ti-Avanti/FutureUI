package gg.fotia.futureui.asset;

import gg.fotia.futureui.config.Node;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** 仅同步合包所需的客户端范围，不接管 CE 的服务器配置或其它内容包。 */
final class CraftEnginePackSettings {
    private CraftEnginePackSettings() {}

    static Path distributedZip(Path craftEngine, Node settings) throws IOException {
        String explicit = settings.text("verify-pack-path", "");
        if (!explicit.isBlank()) return craftEngine.resolve(explicit).normalize();
        Node config = gg.fotia.futureui.config.ConfigRepository.read(craftEngine.resolve("config.yml")).child("resource-pack");
        String id = settings.text("pack-id", "default");
        if (!id.matches("[a-z0-9_-]+")) throw new IOException("Invalid CraftEngine pack-id: " + id);
        Node host = config.child("packs").child(id);
        if (!host.empty() && host.text("type", "").equals("self"))
            return craftEngine.resolve(host.text("storage_path", "./cache/hosted/" + id + "/resource_pack.zip")).normalize();
        // 旧版 CE 以及外部托管使用本地构建 ZIP；自定义工作流可显式指定。
        return craftEngine.resolve(config.text("path", "./generated/resource_pack.zip")).normalize();
    }

    static void synchronize(Path pluginRoot, Path craftEngine, Node settings) throws IOException {
        if (!settings.bool("sync-supported-versions", true)) return;
        String min = settings.text("min-version", ""), max = settings.text("max-version", "");
        if (!min.matches("\\d+\\.\\d+(\\.\\d+)?") || !max.matches("\\d+\\.\\d+(\\.\\d+)?"))
            throw new IOException("assets/versions.yml: craftengine min/max-version must be explicit Minecraft releases");
        Path file = craftEngine.resolve("config.yml");
        YamlConfiguration config = new YamlConfiguration();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { config.load(reader); }
        catch (Exception error) { throw new IOException("Cannot read CraftEngine/config.yml", error); }
        if (config.getBoolean("resource-pack.exclude-core-shaders", false))
            throw new IOException("CraftEngine resource-pack.exclude-core-shaders must be false for FutureUI shaders");
        String prefix = "resource-pack.supported-version.";
        if (min.equals(config.getString(prefix + "min")) && max.equals(config.getString(prefix + "max"))) return;
        Path backup = pluginRoot.resolve("generated/migrations/multiversion-shaders/craftengine-config.yml");
        Files.createDirectories(backup.getParent());
        if (!Files.exists(backup)) Files.copy(file, backup);
        config.set(prefix + "min", min);
        config.set(prefix + "max", max);
        Path temporary = Files.createTempFile(file.getParent(), "futureui-version-", ".yml");
        try {
            Files.writeString(temporary, config.saveToString(), StandardCharsets.UTF_8);
            ResourcePackFiles.copy(temporary, file);
        } finally { Files.deleteIfExists(temporary); }
    }
}
