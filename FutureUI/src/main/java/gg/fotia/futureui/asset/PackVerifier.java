package gg.fotia.futureui.asset;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;

/** 校验独立或合并 ZIP 的覆盖层选择及有效文件，防止必要资源被其它包覆盖。 */
final class PackVerifier {
    private PackVerifier() {}
    private record Overlay(String directory, PackFormat min, PackFormat max) {
        boolean includes(PackFormat version) { return min.compareTo(version) <= 0 && max.compareTo(version) >= 0; }
    }

    static void verify(ZipFile zip, PackManifest expected) throws Exception {
        if (!json(read(zip, PackManifest.ENTRY)).equals(JsonParser.parseString(expected.json())))
            throw new IOException("Distributed ZIP contains a different FutureUI build");
        JsonObject metadata = json(read(zip, "pack.mcmeta")).getAsJsonObject(), pack = metadata.getAsJsonObject("pack");
        if (pack == null) throw new IOException("Missing pack metadata");
        PackFormat min = boundary(pack, "min_format", true), max = boundary(pack, "max_format", false);
        if (min.compareTo(expected.profiles().min()) > 0 || max.compareTo(expected.profiles().max()) < 0)
            throw new IOException("Resource pack range " + min + "-" + max + " excludes FutureUI clients; check pack metadata");
        List<Overlay> overlays = new ArrayList<>();
        if (metadata.has("overlays")) for (JsonElement element : metadata.getAsJsonObject("overlays").getAsJsonArray("entries")) {
            JsonObject entry = element.getAsJsonObject();
            String directory = entry.get("directory").getAsString();
            if (!directory.matches("[a-zA-Z0-9_.-]+")) throw new IOException("Invalid ZIP overlay directory");
            overlays.add(new Overlay(directory, boundary(entry, "min_format", true), boundary(entry, "max_format", false)));
        }
        Map<String, String> actualHashes = new HashMap<>();
        for (var file : expected.files().entrySet()) check(zip, file.getKey(), file.getValue(), actualHashes);
        for (var profile : expected.profiles().profiles()) {
            // 每个区间端点和其中的其它覆盖层边界都要检查，不能只确认目录存在。
            Set<PackFormat> samples = new TreeSet<>(List.of(profile.min(), profile.max()));
            for (Overlay overlay : overlays) {
                if (profile.includes(overlay.min())) samples.add(overlay.min());
                if (profile.includes(overlay.max())) samples.add(overlay.max());
            }
            for (PackFormat version : samples) {
                if (overlays.stream().noneMatch(o -> o.directory().equals(profile.directory()) && o.includes(version)))
                    throw new IOException("Missing active overlay " + profile.directory() + " for format " + version);
                for (var file : expected.files().entrySet()) {
                    String relative = file.getKey();
                    if (relative.startsWith(profile.directory() + "/")) relative = relative.substring(profile.directory().length() + 1);
                    else if (!relative.startsWith("assets/")) continue;
                    String effective = zip.getEntry(relative) == null ? null : relative;
                    for (Overlay overlay : overlays) if (overlay.includes(version) && zip.getEntry(overlay.directory() + "/" + relative) != null)
                        effective = overlay.directory() + "/" + relative;
                    if (effective == null) throw new IOException("Missing effective resource: " + relative);
                    check(zip, effective, file.getValue(), actualHashes);
                }
            }
        }
    }

    private static void check(ZipFile zip, String path, String expected, Map<String, String> hashes) throws Exception {
        String hash = hashes.get(path);
        if (hash == null) { hash = PackManifest.digest(path, read(zip, path)); hashes.put(path, hash); }
        if (!expected.equals(hash)) throw new IOException("Resource changed or shadowed during merging: " + path);
    }

    private static JsonElement json(byte[] bytes) { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)); }
    private static byte[] read(ZipFile zip, String path) throws IOException {
        var entry = zip.getEntry(path);
        if (entry == null) throw new IOException("Missing ZIP entry: " + path);
        try (var stream = zip.getInputStream(entry)) { return stream.readAllBytes(); }
    }

    private static PackFormat boundary(JsonObject node, String key, boolean lower) throws IOException {
        if (node.has(key)) return PackFormat.json(node.get(key), !lower);
        String legacyKey = node.has("formats") ? "formats" : "supported_formats";
        if (node.has(legacyKey)) {
            JsonElement legacy = node.get(legacyKey);
            if (legacy.isJsonArray()) return new PackFormat(legacy.getAsJsonArray().get(lower ? 0 : 1).getAsInt(), 0);
            if (legacy.isJsonObject()) return new PackFormat(legacy.getAsJsonObject().get(lower ? "min_inclusive" : "max_inclusive").getAsInt(), 0);
            return new PackFormat(legacy.getAsInt(), 0);
        }
        if (node.has("pack_format")) return new PackFormat(node.get("pack_format").getAsInt(), 0);
        throw new IOException("Missing resource format boundary: " + key);
    }
}
