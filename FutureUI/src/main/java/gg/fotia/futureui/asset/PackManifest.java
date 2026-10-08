package gg.fotia.futureui.asset;

import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** 构建收据绑定整包内容，同时记录必须原样保留的字体及着色器。 */
public record PackManifest(String json, PackProfiles profiles, Map<String, String> files) {
    public static final String ENTRY = "assets/futureui/build-receipt.json";
    public PackManifest { files = Map.copyOf(files); }

    static PackManifest write(Path staging, String hash, PackProfiles profiles) throws Exception {
        Map<String, String> files = new TreeMap<>();
        try (var paths = Files.walk(staging)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String relative = staging.relativize(file).toString().replace('\\', '/');
                if (relative.equals("assets/futureui/font/images.json") || relative.contains("/shaders/"))
                    files.put(relative, digest(relative, Files.readAllBytes(file)));
            }
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (var profile : profiles.profiles()) entries.add(Map.of("directory", profile.directory(),
                "min_format", profile.min().parts(), "max_format", profile.max().parts(), "protocols", new TreeSet<>(profile.protocols())));
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "schema", 1, "bundle", hash, "profiles", entries, "sha256", files));
        Files.writeString(staging.resolve(ENTRY), json, StandardCharsets.UTF_8);
        return new PackManifest(json, profiles, files);
    }

    static String digest(String path, byte[] data) throws Exception {
        if (path.endsWith(".json")) data = com.google.gson.JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).toString().getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
