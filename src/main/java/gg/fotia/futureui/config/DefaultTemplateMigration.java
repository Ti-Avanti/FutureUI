package gg.fotia.futureui.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

/** 修复已发布默认模板的固定高度；只替换已知片段，保留其他配置与原换行。 */
public final class DefaultTemplateMigration {
    private static final String DIRECTORY = "templates/integrations/basictool/";
    private static final String LEGACY_SCOPE = String.join("\n",
            "        - id: scope_area",
            "          type: column",
            "          width: 171",
            "          height: 27",
            "          gap: 0",
            "          children:",
            "          - id: scope_controls",
            "            type: list",
            "            source: basictool:settings",
            "            view: controls");
    private record Fix(String file, String node, String marker, String height) {}
    private static final List<Fix> FIXES = List.of(
            new Fix("settings-panel.yml", "scope_area", LEGACY_SCOPE, "height: 27"),
            new Fix("settings-panel-row.yml", "panel_setting_row",
                    "padding: 0\nskin: readout\nheight: 36", "height: 36"));

    private DefaultTemplateMigration() {}

    public static void migrate(Path root) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        for (Fix fix : FIXES) migrate(base, fix);
    }

    private static void migrate(Path base, Fix fix) throws IOException {
        Path source = base.resolve(DIRECTORY + fix.file());
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) return;
        String content = Files.readString(source, StandardCharsets.UTF_8);
        String legacy = fix.marker().replace("\n", content.contains("\r\n") ? "\r\n" : "\n");
        int start = content.indexOf(legacy);
        if (start < 0 || start != content.lastIndexOf(legacy)) return;
        try {
            Node node = find(ConfigRepository.read(source), fix.node());
            if (node == null || node.has("min-height")) return;
        } catch (Exception error) {
            throw new IOException("Cannot inspect template migration: " + source, error);
        }

        int height = start + legacy.indexOf(fix.height());
        String updated = content.substring(0, height) + "min-" + content.substring(height);
        Path backup = base.resolve("generated/migrations/template-layout")
                .resolve(UUID.randomUUID().toString()).resolve(source.getFileName());
        Files.createDirectories(backup.getParent());
        Files.copy(source, backup);
        Path temporary = Files.createTempFile(source.getParent(), "futureui-template-", ".tmp");
        try {
            Files.writeString(temporary, updated, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, source, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, source, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Node find(Node node, String id) {
        if (node.text("id", "").equals(id)) return node;
        for (Object value : node.values().values()) {
            if (value instanceof Node child) {
                Node found = find(child, id);
                if (found != null) return found;
            } else if (value instanceof List<?> list) {
                for (Object entry : list) if (entry instanceof Node child) {
                    Node found = find(child, id);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }
}
