package gg.fotia.futureui.render;

import gg.fotia.futureui.asset.GlyphRegistry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/** 提示文字附带高亮几何；点击和悬停仍由原版客户端的同一命中区域决定。 */
final class CanvasHover {
    private CanvasHover() {}
    static Component create(GlyphRegistry glyphs,int canvasWidth,int canvasHeight,boolean nativeExitButton,String skin,int x,int y,int width,int height,Component hint) {
        if(canvasWidth>2047||canvasHeight>2047||x<0||y<0||x+width>2047||y+height>2047||glyphs.get("hover/"+skin+"/top/left")==null)return hint;
        Component visible=hint==null||hint.equals(Component.empty())?Component.text(" "):hint;
        // 第 22 位标识无原生退出栏；其 DialogList 多出 10 像素空行，页脚由 33 缩为 5。
        TextColor geometry=TextColor.color(0x800000|(nativeExitButton?0:0x400000)|canvasWidth<<11|canvasHeight);
        Layer layer=new Layer(geometry);
        // 常规按钮整高绘制一次，避免把整份提示数据按每个九像素行重复膨胀。
        boolean full=glyphs.get("hover/"+skin+"/h"+height+"/left")!=null;
        for(int row=0;row<(full?1:height/9);row++) {
            String prefix="hover/"+skin+"/"+(full?"h"+height:row==0?"top":row==height/9-1?"bottom":"middle")+"/";
            layer.tile(glyphs,prefix+"left",x,y+row*9);
            for(int cursor=x+2,remaining=width-4;remaining>0;){int part=Math.min(128,Integer.highestOneBit(remaining));layer.tile(glyphs,prefix+"fill"+part,cursor,y+row*9);cursor+=part;remaining-=part;}
            layer.tile(glyphs,prefix+"right",x+width-2,y+row*9);
        }
        // 大间距编码会舍去浮点坐标低位；先绘制提示，再恢复测量宽度，提示不会被移到屏幕左边。
        return Component.empty().append(visible).append(layer.finish()).append(PixelCanvas.space(PixelCanvas.measure(visible))).compact();
    }
    /** 同一提示共用样式，连续图块只编码坐标差，避免整行反复发送完整坐标和字体。 */
    private static final class Layer {
        private int position;
        private Component content;
        Layer(TextColor geometry){content=Component.empty().font(Key.key("futureui:interaction")).color(geometry).shadowColor(net.kyori.adventure.text.format.ShadowColor.none());}
        void tile(GlyphRegistry glyphs,String id,int x,int y){
            int next=0x400000|x<<11|y;
            content=content.append(offset(position-next)).append(glyphs.image(id));position=next;
        }
        Component finish(){return content.append(offset(position));}
        private Component offset(int distance){
            StringBuilder value=new StringBuilder();int magnitude=Math.abs(distance),base=distance<0?0xe020:0xe000;
            for(int bit=0;bit<23;bit++)if((magnitude&(1<<bit))!=0)value.append((char)(base+bit));
            return Component.text(value.toString());
        }
    }
}
