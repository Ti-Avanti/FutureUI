package gg.fotia.futureui.asset;

import gg.fotia.futureui.config.Node;
import java.util.*;

/** 固定裁切几何由资源包共享；菜单中的位置和内容可以逐帧变化。 */
public final class ViewportRegions {
    public static final int MAX_REGIONS = 64, STEP = 32768, BASE = 32, PADDING = 8192;
    public record Region(String id, int index, int canvasWidth, int left, int width) {
        public int shift() { return -(BASE + index) * STEP + PADDING; }
        public String start() { return "viewport/" + id + "/start"; }
        public String end() { return "viewport/" + id + "/end"; }
    }
    private ViewportRegions() {}
    public static Map<String, Region> parse(Node source) {
        if (source.values().size() > MAX_REGIONS) throw new IllegalArgumentException("assets/viewports.yml: at most 64 regions");
        Map<String, Region> result = new LinkedHashMap<>();
        for (String id : new TreeSet<>(source.values().keySet())) {
            if (!id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid viewport region: " + id);
            Node node = source.child(id);
            for (String field : node.values().keySet()) if (!Set.of("canvas-width", "left", "width").contains(field))
                throw new IllegalArgumentException("Unknown viewport region field: " + id + "." + field);
            int canvas = node.integer("canvas-width", 540), left = node.integer("left", 0), width = node.integer("width", canvas);
            if (canvas < 1 || canvas > 1024 || left < 0 || width < 1 || left + width > canvas)
                throw new IllegalArgumentException("Invalid viewport bounds: " + id);
            result.put(id, new Region(id, result.size(), canvas, left, width));
        }
        return Collections.unmodifiableMap(result);
    }
}
