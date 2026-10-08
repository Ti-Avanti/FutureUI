package gg.fotia.futureui.config;

import java.util.*;

/** 图表参数在加载时检查；动态数据由绘制器按实际数值处理。 */
final class ChartValidator {
    private ChartValidator() {}
    static void validate(String path, Node node) {
        if (!node.text("type", "").equals("chart")) return;
        String style = node.text("chart-type", "bars");
        if (!Set.of("bars", "ring", "stacked").contains(style)) fail(path, "Unknown chart-type");
        int stride = node.integer("row-height", 27);
        if (stride < 27 || stride > 144 || stride % 9 != 0) fail(path, "row-height must be a multiple of 9 in 27..144");
        int height = node.integer("height", 108);
        if (height < (style.equals("ring") ? 72 : style.equals("stacked") ? 45 : stride)) fail(path, "Chart height is too small");
        if (node.has("width") && node.integer("width", 150) < (style.equals("ring") ? 72 : 90)) fail(path, "Chart width is too small");
        Object series = node.get("series");
        if (!(series instanceof List<?>) && !(series instanceof String)) fail(path, "series must be a list or a variable reference");
        if (node.list("series").size() > 32) fail(path, "A chart supports at most 32 series entries");
        if (node.has("max") && !node.text("max", "").contains("{") && !node.text("max", "").contains("%")) {
            double maximum = node.number("max", 1);
            if (!Double.isFinite(maximum) || maximum <= 0) fail(path, "Chart max must be positive and finite");
        }
    }
    private static void fail(String path, String message) { throw new IllegalArgumentException("menus/" + path + ": " + message); }
}
