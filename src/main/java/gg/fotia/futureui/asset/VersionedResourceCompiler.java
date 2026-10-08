package gg.fotia.futureui.asset;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 公共资源只输出一次，版本着色器只放入互不重叠的覆盖层。 */
final class VersionedResourceCompiler {
    private VersionedResourceCompiler() {}

    static void compile(Path assets, Path staging, PackProfiles versions, boolean shaders) throws IOException {
        if (shaders) {
            for (var entry : versions.source().child("includes").values().entrySet()) {
                if (!entry.getKey().matches("[a-z0-9_]+\\.glsl")) throw new IOException("Invalid shader include: " + entry.getKey());
                copy(assets, entry.getValue().toString(), staging.resolve("assets/futureui/shaders/include/" + entry.getKey()));
            }
            for (var profile : versions.profiles()) {
                for (var entry : profile.shaders().entrySet())
                    copy(assets, entry.getValue(), staging.resolve(profile.directory() + "/assets/minecraft/shaders/core/" + entry.getKey()));
            }
        }
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("description", versions.source().text("description", "FutureUI"));
        boolean legacy = versions.min().major() < 65;
        if (legacy) {
            pack.put("pack_format", versions.min().major());
            pack.put("supported_formats", Map.of("min_inclusive", versions.min().major(), "max_inclusive", versions.max().major()));
        }
        pack.put("min_format", versions.min().parts());
        pack.put("max_format", versions.max().parts());
        List<Map<String, Object>> overlays = new ArrayList<>();
        for (var profile : versions.profiles()) {
            Map<String, Object> overlay = new LinkedHashMap<>();
            overlay.put("directory", profile.directory());
            if (legacy) overlay.put("formats", List.of(profile.min().major(), profile.max().major()));
            overlay.put("min_format", profile.min().parts());
            overlay.put("max_format", profile.max().parts());
            overlays.add(overlay);
        }
        Files.writeString(staging.resolve("pack.mcmeta"), new GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("pack", pack, "overlays", Map.of("entries", overlays))), StandardCharsets.UTF_8);
    }

    private static void copy(Path assets, String relative, Path target) throws IOException {
        Path root = assets.toAbsolutePath().normalize(), source = root.resolve(relative).normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source) || Files.size(source) == 0)
            throw new IOException("Missing or invalid shader: " + relative);
        for (Path parent = source; !parent.equals(root); parent = parent.getParent())
            if (Files.isSymbolicLink(parent)) throw new IOException("Shader cannot be a symbolic link: " + relative);
        ResourcePackFiles.copy(source, target);
    }
}
