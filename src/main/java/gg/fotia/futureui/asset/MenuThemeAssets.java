package gg.fotia.futureui.asset;

import gg.fotia.futureui.config.Node;
import java.nio.file.Path;
import java.util.*;

/** 独立菜单主题只增加自己的字形和底板，不替换默认主题的资源。 */
public final class MenuThemeAssets {
    private MenuThemeAssets() {}

    public static void validate(Node themes) {
        for (var entry : themes.values().entrySet()) {
            if (!entry.getKey().matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid menu theme: " + entry.getKey());
            Node theme = Node.of(entry.getValue());
            for (var alias : theme.child("skin-aliases").values().entrySet())
                if (!theme.child("canvas").child("styles").has(alias.getValue().toString()))
                    throw new IllegalArgumentException("Unknown theme skin: " + entry.getKey() + "/" + alias.getValue());
        }
    }

    static List<Integer> imageSizes(Node assets) {
        Set<Integer> result = new LinkedHashSet<>();
        List<?> configured = assets.child("canvas").list("image-sizes");
        for (Object value : configured.isEmpty() ? List.of(36, 72, 144) : configured) result.add(Integer.parseInt(value.toString()));
        for (Object value : assets.child("themes").values().values())
            for (Object size : Node.of(value).child("canvas").list("image-sizes")) result.add(Integer.parseInt(size.toString()));
        return List.copyOf(result);
    }

    static void generate(Path staging, Node themes, Map<String, Integer> allocation,
                         Map<String, GlyphRegistry.Glyph> glyphs, List<Map<String, Object>> providers) throws Exception {
        for (var entry : themes.values().entrySet()) {
            String id = entry.getKey();
            Node theme = Node.of(entry.getValue()), canvas = theme.child("canvas");
            Map<String, Object> styles = new LinkedHashMap<>();
            canvas.child("styles").values().forEach((skin, style) -> styles.put(id + "_" + skin, style));
            Map<String, Object> settings = new LinkedHashMap<>(canvas.values()); settings.put("styles", Node.of(styles));
            Node scoped = Node.of(settings);
            CanvasAtlasCompiler.generate(staging, scoped, allocation, glyphs, providers);
            CanvasInteractionCompiler.generate(staging, scoped, allocation, glyphs, providers, id + "/");
            DialogBackgroundCompiler.generate(staging, theme.child("native-widgets"), allocation, glyphs, providers, "/" + id);
        }
    }
}
