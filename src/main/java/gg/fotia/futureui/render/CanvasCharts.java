package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.layout.LayoutEngine;
import gg.fotia.futureui.menu.MenuController;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import net.kyori.adventure.text.Component;

/** 配置驱动的只读图表；保持真实数值，缺失值不补零，不执行数据查询。 */
final class CanvasCharts {
    private record Datum(Component label, double value, String display, String skin) {}
    private static final int[][] RING = {{0,3},{0,4},{0,5},{1,6},{2,7},{3,7},{4,7},{5,7},{6,6},{7,5},
            {7,4},{7,3},{7,2},{6,1},{5,0},{4,0},{3,0},{2,0},{1,1},{0,2}};
    private CanvasCharts() {}

    static void draw(MenuController menus, PixelCanvas canvas, MenuContext ctx, Node node, LayoutEngine.Box box, MenuTheme theme) {
        node = menus.values.bind(ctx, node, Set.of("text", "series", "empty-text"));
        List<Datum> rows = data(menus, ctx, node);
        double total = rows.stream().mapToDouble(Datum::value).sum();
        if (rows.isEmpty() || total <= 0 || !Double.isFinite(total)) {
            text(canvas, menus.text.render(ctx.player(), ctx.session().locale, node.text("empty-text", "@messages.chart-empty"), ctx.variables()),
                    box.x(), box.y(), box.width());
            return;
        }
        switch (node.text("chart-type", "bars")) {
            case "ring" -> ring(menus, canvas, ctx, node, box, theme, rows);
            case "stacked" -> stacked(canvas, node, box, theme, rows);
            default -> bars(canvas, node, box, theme, rows);
        }
    }

    private static List<Datum> data(MenuController menus, MenuContext ctx, Node node) {
        Object input = menus.values.object(ctx, node.values().get("series"));
        if (!(input instanceof List<?> list)) return List.of();
        if (list.size() > 32) throw new IllegalArgumentException("A chart supports at most 32 series entries");
        var result = new ArrayList<Datum>();
        for (Object value : list) {
            Node item = Node.of(value);
            try {
                String raw = menus.values.resolve(ctx, item.text("value", ""));
                BigDecimal number = new BigDecimal(raw);
                double numeric = number.doubleValue();
                if (!Double.isFinite(numeric) || numeric < 0) continue;
                Component label = menus.text.render(ctx.player(), ctx.session().locale, item.text("label", ""), ctx.variables());
                String display = menus.values.resolve(ctx, item.text("display", number.stripTrailingZeros().toPlainString()));
                result.add(new Datum(label, numeric, display, menus.values.resolve(ctx, item.text("skin", node.text("fill-skin", "selected")))));
            } catch (NumberFormatException ignored) { /* 未提供或不可用的字段不产生一个虚假的零值。 */ }
        }
        return result;
    }

    private static void bars(PixelCanvas canvas, Node node, LayoutEngine.Box box, MenuTheme theme, List<Datum> rows) {
        int stride = node.integer("row-height", 27);
        int capacity = box.height() / stride;
        double max = node.number("max", rows.stream().mapToDouble(Datum::value).max().orElse(1));
        if (!Double.isFinite(max) || max <= 0) throw new IllegalArgumentException("Chart max must be finite and positive");
        for (int i = 0; i < Math.min(rows.size(), capacity); i++) {
            Datum row = rows.get(i); int y = box.y() + i * stride;
            int headerHeight = Math.min(27, stride - 9), valueWidth = valueWidth(row.display, box.width());
            text(canvas, row.label, box.x(), y + textOffset(headerHeight), box.width() - valueWidth - 9);
            readout(canvas, theme, node, row.display, box.x() + box.width() - valueWidth, y, valueWidth, headerHeight);
            int barY = y + Math.max(headerHeight, stride - 18);
            canvas.shape(theme.skin(node.text("track-skin", "readout")), box.x(), barY, box.width(), 9);
            int filled = (int)Math.round(box.width() * Math.min(1, row.value / Math.max(1e-12, max)));
            if (filled >= 4) canvas.shape(theme.skin(row.skin), box.x(), barY, filled, 9);
        }
    }

    private static void ring(MenuController menus, PixelCanvas canvas, MenuContext ctx, Node node, LayoutEngine.Box box, MenuTheme theme, List<Datum> rows) {
        double total = rows.stream().mapToDouble(Datum::value).sum();
        int left = box.x(), top = box.y() + ((box.height() - 72) / 18) * 9;
        for (int i = 0; i < RING.length; i++) {
            double position = (i + .5) / RING.length * total, end = 0; Datum chosen = rows.getLast();
            for (Datum row : rows) { end += row.value; if (position <= end) { chosen = row; break; } }
            canvas.shape(theme.skin(chosen.skin), left + RING[i][1] * 9, top + RING[i][0] * 9, 9, 9);
        }
        String center = node.has("text") ? menus.text.plain(ctx.player(), ctx.session().locale, node.text("text", ""), ctx.variables())
                : BigDecimal.valueOf(rows.getFirst().value / total * 100).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
        readout(canvas, theme, node, center, left + 9, top + 27, 54, 27);
        int legendX = left + 81, available = box.width() - 81;
        int stride = legendStride(box.height(), rows.size()), count = Math.min(rows.size(), box.height() / stride);
        int legendTop = box.y() + ((box.height() - count * stride) / 18) * 9;
        if (available > 20) for (int i = 0; i < count; i++) {
            legend(canvas, theme, node, rows.get(i), legendX, legendTop + i * stride, available, stride);
        }
    }

    private static void stacked(PixelCanvas canvas, Node node, LayoutEngine.Box box, MenuTheme theme, List<Datum> rows) {
        double total = rows.stream().mapToDouble(Datum::value).sum(), sum = 0;
        int x = box.x(), end = x;
        for (Datum row : rows) {
            sum += row.value; int next = box.x() + (int)Math.round(box.width() * sum / total);
            if (next - end >= 4) canvas.shape(theme.skin(row.skin), end, box.y(), next - end, 18);
            end = next;
        }
        int stride = legendStride(box.height() - 27, rows.size());
        for (int i = 0; i < Math.min(rows.size(), (box.height() - 27) / stride); i++) {
            legend(canvas, theme, node, rows.get(i), x, box.y() + 27 + i * stride, box.width(), stride);
        }
    }

    private static int legendStride(int height, int count) {
        return height >= count * 36 ? 36 : height >= count * 27 ? 27 : 18;
    }
    private static int textOffset(int height) { return ((height / 9 - 1) / 2) * 9; }
    private static int valueWidth(String value, int width) {
        return Math.min(Math.max(54, PixelCanvas.measure(Component.text(value)) + 18), Math.max(36, width / 2));
    }
    private static void legend(PixelCanvas canvas, MenuTheme theme, Node node, Datum row, int x, int y, int width, int stride) {
        int height = Math.min(27, stride), offset = textOffset(height);
        canvas.shape(theme.skin(row.skin), x, y + offset, 9, 9);
        boolean numeric = width >= 99;
        int valueWidth = numeric ? valueWidth(row.display, width) : 0;
        text(canvas, row.label, x + 18, y + offset, width - 18 - (numeric ? valueWidth + 9 : 0));
        if (numeric) readout(canvas, theme, node, row.display, x + width - valueWidth, y, valueWidth, height);
    }

    private static void readout(PixelCanvas canvas, MenuTheme theme, Node node, String value, int x, int y, int width, int height) {
        canvas.shape(theme.skin(node.text("value-skin", "readout")), x, y, width, height);
        var lines = TextFlow.lines(Component.text(value), width - 4, 1);
        if (!lines.isEmpty()) canvas.text(lines.getFirst(), x + (width - PixelCanvas.measure(lines.getFirst())) / 2, y + textOffset(height));
    }
    private static void text(PixelCanvas canvas, Component value, int x, int y, int width) {
        if (width <= 0) return;
        var lines = TextFlow.lines(value, width, 1);
        if (!lines.isEmpty() && PixelCanvas.measure(lines.getFirst()) <= width) canvas.text(lines.getFirst(), x, y);
    }
    static Component summary(MenuController menus, MenuContext ctx, Node node) {
        List<Datum> rows = data(menus, ctx, node); Component result = Component.empty();
        for (Datum row : rows) {
            if (!result.equals(Component.empty())) result = result.append(Component.newline());
            result = result.append(row.label).append(Component.text(": " + row.display));
        }
        return rows.isEmpty() ? menus.text.render(ctx.player(), ctx.session().locale,
                node.text("empty-text", "@messages.chart-empty"), ctx.variables()) : result;
    }
}
