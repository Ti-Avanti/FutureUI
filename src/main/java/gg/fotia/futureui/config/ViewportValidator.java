package gg.fotia.futureui.config;

import gg.fotia.futureui.asset.ViewportRegions;
import gg.fotia.futureui.model.MenuDefinition;
import java.util.*;

/** 展示带只容纳展示组件，操作按钮留在固定区域，避免移动中的命中歧义。 */
public final class ViewportValidator {
    private static final Set<String> DISPLAY = Set.of("text", "image", "raster", "progress", "chart", "spacer", "row", "column", "grid", "list");
    private ViewportValidator() {}
    public static void validate(Map<String, MenuDefinition> menus, Node assets) {
        var regions = ViewportRegions.parse(assets.child("viewports"));
        for (var menu : menus.values()) walk(menu, menu.components(), regions, false);
    }
    private static void walk(MenuDefinition menu, List<Node> nodes, Map<String, ViewportRegions.Region> regions, boolean inside) {
        for (Node node : nodes) {
            String type = node.text("type", "text"), path = menu.id() + "/" + node.text("id", "");
            if (inside && (!DISPLAY.contains(type) || !node.nodes("actions").isEmpty()))
                throw new IllegalArgumentException("Viewport children must be display-only: " + path);
            boolean viewport = type.equals("viewport");
            if (viewport) {
                var region = regions.get(node.text("region", ""));
                if (!menu.renderer().equals("canvas") || region == null || region.canvasWidth() != menu.layout().integer("width", 576))
                    throw new IllegalArgumentException("Viewport requires a matching standalone Canvas region: " + path);
                if (!node.has("height") || node.integer("height", 0) < 9)
                    throw new IllegalArgumentException("Viewport requires a positive height: " + path);
                number(node, "visible-items", 7, 1, 32, true, path);
                number(node, "position", 0, -65536, 65536, false, path);
                choice(node, "direction", Set.of("left", "right"), path);
                Node motion = node.child("motion");
                if (!motion.empty()) {
                    if (node.has("position")) throw new IllegalArgumentException("Use position or motion, not both: " + path);
                    for (String field : motion.values().keySet()) if (!Set.of("from", "to", "duration-ticks", "delay-ticks", "easing", "loop", "interval-ticks").contains(field))
                        throw new IllegalArgumentException("Unknown viewport motion field: " + path + "/" + field);
                    number(motion, "from", 0, -65536, 65536, false, path);
                    number(motion, "to", 1, -65536, 65536, false, path);
                    number(motion, "duration-ticks", 80, 1, 12000, true, path);
                    number(motion, "delay-ticks", 0, 0, 12000, true, path);
                    number(motion, "interval-ticks", 1, 1, 20, true, path);
                    if (dynamic(motion.text("interval-ticks", "1"))) throw new IllegalArgumentException("Viewport motion interval-ticks must be static: " + path);
                    choice(motion, "easing", Set.of("linear", "ease-in", "ease-out", "ease-in-out"), path);
                }
            }
            for (String key : List.of("children", "after-items")) walk(menu, node.nodes(key), regions, inside || viewport);
            if (type.equals("list")) walk(menu, List.of(node.child("item")), regions, inside);
        }
    }
    private static void choice(Node node, String key, Set<String> values, String path) {
        if (!node.has(key)) return;
        String value = node.text(key, "");
        if (!dynamic(value) && !values.contains(value)) throw new IllegalArgumentException("Invalid " + key + ": " + path);
    }
    private static void number(Node node, String key, double fallback, double min, double max, boolean integer, String path) {
        String raw = node.text(key, Double.toString(fallback));
        if (dynamic(raw)) return;
        double value = Double.parseDouble(raw);
        if (!Double.isFinite(value) || value < min || value > max || integer && value != Math.floor(value))
            throw new IllegalArgumentException("Invalid viewport " + key + ": " + path);
    }
    private static boolean dynamic(String value) { return value.contains("{") || value.contains("%"); }
}
