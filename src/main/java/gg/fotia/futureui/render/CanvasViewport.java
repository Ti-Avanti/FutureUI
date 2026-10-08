package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.layout.LayoutEngine;
import gg.fotia.futureui.menu.MenuController;
import java.util.*;
import java.util.function.Consumer;

/** 有界横向展示带；父布局保持固定尺寸，子项按小数位置共同平移。 */
final class CanvasViewport {
    private CanvasViewport() {}
    static void draw(MenuController menus, PixelCanvas canvas, MenuContext context, LayoutEngine.Box box,
                     int canvasWidth, Consumer<List<LayoutEngine.Box>> painter) {
        Node node = box.component();
        var region = menus.glyphs.viewport(node.text("region", ""));
        if (region == null || region.canvasWidth() != canvasWidth || region.left() != box.x() || region.width() != box.width())
            throw new IllegalArgumentException("Viewport does not match assets/viewports.yml: " + box.id());
        double visible = number(menus, context, node, "visible-items", "7");
        double position = ViewportMotion.position(context, node, menus.values);
        if (visible < 1 || visible > 32 || visible != Math.floor(visible) || Math.abs(position) > 65536)
            throw new IllegalArgumentException("Invalid viewport visible-items/position: " + box.id());
        String direction = menus.values.resolve(context, node.text("direction", "left"));
        if (!Set.of("left", "right").contains(direction)) throw new IllegalArgumentException("Viewport direction must be left/right");
        int count = (int)visible, gap = node.integer("gap", 9);
        int cell = (box.width() - gap * (count - 1)) / count;
        if (cell < 1) throw new IllegalArgumentException("Viewport has no room for its items: " + box.id());
        double margin = (box.width() - count * cell - (count - 1) * gap) / 2.0;
        List<Node> children = node.nodes("children");
        if (children.size() > 256) throw new IllegalArgumentException("Viewport supports at most 256 display items");
        List<LayoutEngine.Box> boxes = new ArrayList<>();
        var engine = new LayoutEngine();
        for (int i = 0; i < children.size(); i++) {
            double distance = margin + (i - position) * (cell + gap);
            int x = box.x() + (int)Math.round(direction.equals("left") ? distance : box.width() - cell - distance);
            if (x + cell <= box.x() || x >= box.x() + box.width()) continue;
            var measured = engine.measure(List.of(children.get(i)), Node.of(Map.of("width", cell, "type", "column", "gap", 0)));
            if (measured.height() > box.height()) throw new IllegalArgumentException("Viewport item exceeds its height: " + box.id());
            for (var item : measured.boxes()) boxes.add(new LayoutEngine.Box(item.id(), x + item.x(), box.y() + item.y(),
                    item.width(), item.height(), item.component()));
        }
        canvas.viewport(region.id());
        try { painter.accept(boxes); } finally { canvas.viewport(null); }
    }
    private static double number(MenuController menus, MenuContext context, Node node, String key, String fallback) {
        double value = Double.parseDouble(menus.values.resolve(context, node.text(key, fallback)));
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Viewport " + key + " must be finite");
        return value;
    }
}
