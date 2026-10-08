package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.layout.LayoutEngine;
import gg.fotia.futureui.menu.MenuController;

/** 图片按真实宽高比对齐，容器背景可与普通子组件组合成效果预览。 */
final class CanvasImages {
    private CanvasImages() {}
    static void draw(MenuController menus, PixelCanvas canvas, MenuContext context, Node node,
                     LayoutEngine.Box box, String field, int size) {
        String image = menus.values.resolve(context, node.text(field, ""));
        String key = "large/" + size + '/' + image;
        if (menus.glyphs.get(key + "_0") == null && node.has("fallback-image")) {
            image = menus.values.resolve(context, node.text("fallback-image", "")); key = "large/" + size + '/' + image;
        }
        if (menus.glyphs.get(key + "_0") == null) throw new IllegalArgumentException("Canvas image or size is not compiled: " + key);
        var original = menus.glyphs.get(image);
        int width = original == null ? size : (int)Math.round((double)original.width() * size / original.height());
        if (width > box.width() || size > box.height()) throw new IllegalArgumentException("Image exceeds its container: " + box.id());
        canvas.picture(key, box.x() + (box.width() - width) / 2, box.y() + ((box.height() - size) / 18) * 9, size);
    }
}
