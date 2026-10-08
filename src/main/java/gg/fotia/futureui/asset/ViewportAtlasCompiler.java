package gg.fotia.futureui.asset;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** 字形只携带裁切区域编号；真实文字颜色、UV、阴影仍使用原版管线。 */
final class ViewportAtlasCompiler {
    private ViewportAtlasCompiler() {}
    static void markers(Path root, Map<String, ViewportRegions.Region> regions, Map<String, Integer> allocation,
                        Map<String, GlyphRegistry.Glyph> glyphs, List<Map<String, Object>> providers) throws Exception {
        Map<String, Integer> advances = new LinkedHashMap<>();
        for (var region : regions.values()) {
            marker(region.start(), region.shift(), allocation, glyphs, advances);
            marker(region.end(), -region.shift(), allocation, glyphs, advances);
        }
        if (!advances.isEmpty()) providers.add(Map.of("type", "space", "advances", advances));
        if (!regions.isEmpty()) {
            // 保留原生文本行的可见边界；低 alpha 在片元阶段丢弃，不产生可见底板。
            var bitmap = new java.awt.image.BufferedImage(1, 9, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            bitmap.setRGB(0, 0, 0x01ffffff);
            Path path = root.resolve("assets/futureui/textures/font/viewport_anchor.png");
            Files.createDirectories(path.getParent()); javax.imageio.ImageIO.write(bitmap, "PNG", path.toFile());
            String character = Character.toString(GlyphAllocation.code(allocation, "viewport/anchor"));
            providers.add(Map.of("type", "bitmap", "file", "futureui:font/viewport_anchor.png", "height", 9, "ascent", 7, "chars", List.of(character)));
            glyphs.put("viewport/anchor", new GlyphRegistry.Glyph(character, 2, 9));
        }
    }
    private static void marker(String name, int advance, Map<String, Integer> allocation,
                               Map<String, GlyphRegistry.Glyph> glyphs, Map<String, Integer> advances) {
        String character = Character.toString(GlyphAllocation.code(allocation, name));
        advances.put(character, advance);
        glyphs.put(name, new GlyphRegistry.Glyph(character, advance, 0));
    }
    static void shaders(Path root, Map<String, ViewportRegions.Region> regions) throws Exception {
        if (regions.isEmpty()) return;
        Path includes = root.resolve("assets/futureui/shaders/include");
        Path vertex = includes.resolve("canvas_vertex.glsl"), fragment = includes.resolve("canvas_fragment.glsl");
        StringBuilder table = new StringBuilder("vec3 bounds = vec3(0.0);\n");
        for (var r : regions.values()) table.append("if (region == ").append(r.index()).append(") bounds = vec3(")
                .append(r.canvasWidth()).append(".0, ").append(r.left()).append(".0, ").append(r.width()).append(".0);\n");
        String code = """
                // FutureUI viewport: restore the original glyph position and pass a horizontal scissor.
                bool fui_viewport() {
                    if (Position.x > -1000000.0 || Position.x < -3150000.0) return false;
                    int block = int(ceil(-Position.x / 32768.0));
                    int region = block - 32;
                    %s
                    if (bounds.x < 1.0) return false;
                    float viewport = ceil(abs(2.0 / (ProjMat[0][0] * ModelViewMat[0][0])) - 0.001);
                    float anchor = max(26.0, floor((viewport - bounds.x) * 0.5));
                    float x = Position.x + float(block) * 32768.0 - 8192.0;
                    fuiOrigin = ivec2(int(anchor + bounds.y), int(anchor + bounds.y + bounds.z));
                    fuiLocal = vec2(x, 0.0);
                    fuiHover = 4;
                    gl_Position = ProjMat * ModelViewMat * vec4(x, Position.yz, 1.0);
                    return true;
                }
                """.formatted(table);
        String source = Files.readString(vertex, StandardCharsets.UTF_8);
        if (source.contains("bool fui_viewport(")) throw new IllegalArgumentException("Viewport shader hook is already present in source overrides");
        source = inject(source, "void\\s+fui_position\\s*\\(\\s*\\)\\s*\\{",
                "\n    if (fui_viewport()) return;\n", vertex);
        Files.writeString(vertex, code + source, StandardCharsets.UTF_8);
        source = inject(Files.readString(fragment, StandardCharsets.UTF_8), "vec4\\s+fui_fragment\\s*\\(\\s*\\)\\s*\\{", """

                    if (fuiHover == 4) {
                        if (fuiLocal.x < float(fuiOrigin.x) || fuiLocal.x >= float(fuiOrigin.y)) discard;
                        vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
                        if (color.a < 0.1) discard;
                        return color;
                    }
                """, fragment);
        Files.writeString(fragment, source, StandardCharsets.UTF_8);
    }
    private static String inject(String source, String pattern, String extra, Path file) {
        var matcher = Pattern.compile(pattern).matcher(source);
        if (!matcher.find()) throw new IllegalArgumentException("Missing FutureUI viewport shader hook: " + file.getFileName());
        return source.substring(0, matcher.end()) + extra + source.substring(matcher.end());
    }
}
