package gg.fotia.futureui.render;

import gg.fotia.futureui.api.RasterImage;
import gg.fotia.futureui.asset.RasterAtlasCompiler;
import gg.fotia.futureui.layout.LayoutEngine;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import java.util.*;

/** 最近邻缩放并合并同色像素段；图片快照不需重新生成或推送资源包。 */
final class CanvasRaster {
    private static final Key FONT = Key.key("futureui:raster");
    private CanvasRaster() {}
    static void draw(PixelCanvas canvas, RasterImage image, LayoutEngine.Box box) {
        int padding = box.component().integer("padding", 0);
        int innerWidth = box.width() - 2 * padding, innerHeight = box.height() - 2 * padding;
        if (innerWidth < 1 || innerHeight < 1) return;
        double scale = Math.min(4, Math.min((double)innerWidth / image.width(), (double)innerHeight / image.height()));
        if (scale >= 1) scale = Math.floor(scale);
        int width = Math.max(1, (int)(image.width() * scale)), height = Math.max(1, (int)(image.height() * scale));
        int left = box.x() + (box.width() - width) / 2, top = box.y() + (box.height() - height) / 2;
        Map<Integer,Row> batches=new LinkedHashMap<>();
        for (int y = 0; y < height;) {
            int sourceY = Math.min(image.height() - 1, (int)(y / scale)), rows = 1;
            while (y + rows < height && (int)((y + rows) / scale) == sourceY) rows++;
            for (int x = 0; x < width;) {
                int color = image.pixel(Math.min(image.width() - 1, (int)(x / scale)), sourceY), end = x + 1;
                while (end < width && image.pixel(Math.min(image.width() - 1, (int)(end / scale)), sourceY) == color) end++;
                if ((color >>> 24) >= 128) span(batches, left + x, top + y, end - x, rows, color);
                x = end;
            }
            y += rows;
        }
        batches.forEach((row,line)->canvas.paint(line.start,row*9,line.end-line.start,line.finish()));
    }
    private static void span(Map<Integer,Row> batches, int x, int y, int width, int height, int color) {
        for (int dy = 0; dy < height;) {
            int offset = (y + dy) % 9, partHeight = Math.min(height - dy, 9 - offset);
            for (int dx = 0; dx < width;) {
                int part = Math.min(128, Integer.highestOneBit(width - dx));
                Row line=batches.computeIfAbsent((y+dy)/9,ignored->new Row(x));
                line.append(x+dx,part,RasterAtlasCompiler.code(part,offset,partHeight),color);dx+=part;
            }
            dy += partHeight;
        }
    }
    /** 每行共用字体和阴影样式，同色文本合并，避免像素预览触及组件 NBT 大小上限。 */
    private static final class Row {
        private record Span(int x,int width,int glyph){}
        final int start;int end;
        private final Map<Integer,List<Span>> colors=new LinkedHashMap<>();
        Row(int x){start=x;end=x;}
        void append(int x,int width,int glyph,int nextColor){
            colors.computeIfAbsent(nextColor&0xffffff,ignored->new ArrayList<>()).add(new Span(x,width,glyph));
            end=Math.max(end,x+width);
        }
        Component finish(){
            // 同一九像素行内的片段互不重叠，可按颜色重排，只发送一次每种颜色样式。
            Component line=Component.empty().font(FONT).shadowColor(ShadowColor.none());int cursor=start;
            for(var color:colors.entrySet()){
                StringBuilder run=new StringBuilder();
                for(Span span:color.getValue()){
                    run.append(RasterAtlasCompiler.space(span.x-cursor)).append((char)span.glyph).append(RasterAtlasCompiler.space(-1));
                    cursor=span.x+span.width;
                }
                line=line.append(Component.text(run.toString()).color(TextColor.color(color.getKey())));
            }
            return line.append(Component.text(RasterAtlasCompiler.space(end-cursor)));
        }
    }
}
