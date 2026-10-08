package gg.fotia.futureui.render;

import gg.fotia.futureui.asset.GlyphRegistry;
import java.util.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** 透明命中层先于绘画层：负间距叠图不会吞掉文字和按钮的点击。 */
public final class PixelCanvas {
    private static volatile Map<Integer,Integer> metrics=Map.of();
    public static void metrics(Map<Integer,Integer> value){metrics=Map.copyOf(value);}
    private record Paint(int x,int advance,Component image,String viewport){}
    private record Hit(int x,int width,ClickEvent action,HoverEvent<?> hover){}
    private final int width;private final GlyphRegistry glyphs;
    private boolean pointerDescriptions;
    private boolean nativeExitButton=true;
    private String viewport;
    private final List<List<Paint>> rows;private final List<List<Hit>> hits;
    public PixelCanvas(int width,int height,GlyphRegistry glyphs){this.width=width+16;this.glyphs=glyphs;rows=new ArrayList<>();hits=new ArrayList<>();for(int i=0;i<(height+8)/9;i++){rows.add(new ArrayList<>());hits.add(new ArrayList<>());}}
    public void pointerDescriptions(boolean enabled){pointerDescriptions=enabled;}
    public void nativeExitButton(boolean enabled){nativeExitButton=enabled;}
    public void viewport(String id){viewport=id;}
    public void frame(){frame("");}
    public void frame(String theme){if(glyphs.get("frame/"+theme+"top/left")==null)return;for(int row=0;row<rows.size();row++){
        String prefix="frame/"+theme+(rows.size()==1?"single":row==0?"top":row==rows.size()-1?"bottom":"middle")+"/";int x=-16,frameWidth=width+7;image(prefix+"left",x,row*9);
        for(int cursor=x+2,remaining=frameWidth-4;remaining>0;){int part=Math.min(128,Integer.highestOneBit(remaining));image(prefix+"fill"+part,cursor,row*9);cursor+=part;remaining-=part;}
        // 高度计算会按实际行宽再次换行；右侧的 8 像素内边距和 1 像素前进量由着色器补齐。
        var right=glyphs.get(prefix+"right");paint(x+frameWidth-2,row*9,right.width(),glyphs.image(prefix+"right").color(TextColor.color(0xfffe00|(rows.size()==1?4:row==0?1:row==rows.size()-1?3:2))).shadowColor(net.kyori.adventure.text.format.ShadowColor.none()));
    }}
    public void shape(String name,int x,int y,int width,int height){if(glyphs.get("canvas/"+name+"/top/left")==null)throw new IllegalArgumentException("Unknown canvas skin: "+name);if(width<4||height<9)return;for(int i=0;i<height/9;i++){
        String prefix="canvas/"+name+"/"+(i==0?"top":i==height/9-1?"bottom":"middle")+"/";int cursor=x+2,remaining=width-4;image(prefix+"left",x,y+i*9);
        while(remaining>0){int part=Math.min(128,Integer.highestOneBit(remaining));image(prefix+"fill"+part,cursor,y+i*9);remaining-=part;cursor+=part;}
        image(prefix+"right",x+width-2,y+i*9);
    }}
    public void picture(String id,int x,int y,int height){for(int row=0;row<height/9;row++)image(id+"_"+row,x,y+row*9);}
    public void image(String id,int x,int y){var glyph=glyphs.get(id);if(glyph!=null)paint(x,y,glyph.width(),glyphs.image(id).shadowColor(net.kyori.adventure.text.format.ShadowColor.none()));}
    public void text(Component text,int x,int y){double advance=advance(text);Component value=Component.empty().append(text.font(Key.key("minecraft:default")));if(advance!=Math.ceil(advance))value=value.append(Component.text("\ue801").font(Key.key("futureui:space")));paint(x,y,(int)Math.ceil(advance),value);}
    public void button(String skin,int x,int y,int width,int height,Component text,ClickEvent click,Component hint){button(skin,x,y,width,height,text,click,hint,"center",0,"",18,6);}
    public void button(String skin,int x,int y,int width,int height,Component text,ClickEvent click,Component hint,String align,int padding,String icon,int iconSize,int iconGap){
        shape(skin,x,y,width,height);
        boolean illustrated=!icon.isBlank()&&glyphs.get("large/"+iconSize+"/"+icon+"_0")!=null;
        int imageWidth=illustrated?iconSize+iconGap:0,labelWidth=measure(text)+imageWidth;
        int start=x+switch(align){case "start"->padding;case "end"->width-padding-labelWidth;default->(width-labelWidth)/2;};
        if(illustrated)picture("large/"+iconSize+"/"+icon,start,y+((height-iconSize)/18)*9,iconSize);
        text(text,start+imageWidth,y+((height/9-1)/2)*9);
        if(click!=null){Component description=pointerDescriptions?(hint==null||PlainTextComponentSerializer.plainText().serialize(hint).isBlank()?text:hint):Component.empty();Component content=CanvasHover.create(glyphs,this.width-16,rows.size()*9,nativeExitButton,skin,x,y,width,height,description);HoverEvent<?> hover=content==null?null:CanvasTooltip.create(content);for(int row=y/9;row<(y+height)/9;row++)hits.get(row).add(new Hit(x+8,width,click,hover));}
    }
    void paint(int x,int y,int advance,Component image){if(y>=0&&y/9<rows.size())rows.get(y/9).add(new Paint(x+8,advance,image,viewport));}
    public Component build(){Component result=Component.empty().decoration(TextDecoration.ITALIC,false);for(int row=0;row<rows.size();row++){
        if(row>0)result=result.append(Component.newline());int cursor=0;
        for(Hit hit:hits.get(row).stream().sorted(Comparator.comparingInt(Hit::x)).toList()){
            if(hit.x<cursor)throw new IllegalArgumentException("Overlapping canvas buttons");result=result.append(space(hit.x-cursor));Component area=space(hit.width).clickEvent(hit.action);if(hit.hover!=null)area=area.hoverEvent(hit.hover);result=result.append(area);cursor=hit.x+hit.width;
        }
        result=result.append(space(width-cursor)).append(space(-width));cursor=0;
        if(rows.get(row).stream().anyMatch(p->p.viewport!=null))result=result.append(glyphs.image("viewport/anchor")).append(space(-2));
        String clipped=null;
        for(Paint paint:rows.get(row)){
            if(!Objects.equals(clipped,paint.viewport)){
                if(clipped!=null){result=result.append(space(-cursor)).append(glyphs.image("viewport/"+clipped+"/end"));cursor=0;}
                clipped=paint.viewport;
                if(clipped!=null)result=result.append(glyphs.image("viewport/"+clipped+"/start"));
            }
            result=result.append(space(paint.x-cursor)).append(paint.image);cursor=paint.x+paint.advance;
        }
        if(clipped!=null){result=result.append(space(-cursor)).append(glyphs.image("viewport/"+clipped+"/end"));cursor=0;}
        result=result.append(space(width-cursor));
    }return result.compact();}
    public static Component space(int advance){Component value=Component.empty();while(advance!=0){int step=Math.max(-1024,Math.min(1024,advance));value=value.append(Component.text(Character.toString(0xe000+step+1024)).font(Key.key("futureui:space")));advance-=step;}return value;}
    public static int measure(Component text){return (int)Math.ceil(advance(text));}
    static double advance(Component text){return advance(text,false);}
    private static double advance(Component text,boolean inheritedBold){
        boolean bold=text.decoration(TextDecoration.BOLD)==TextDecoration.State.NOT_SET?inheritedBold:text.decoration(TextDecoration.BOLD)==TextDecoration.State.TRUE;
        String value=text instanceof net.kyori.adventure.text.TextComponent literal?literal.content():PlainTextComponentSerializer.plainText().serialize(text.children(List.of()));
        // 原版 Unihex 字形的粗体增量为半像素，位图字形为一像素。
        double width=value.codePoints().mapToDouble(c->metrics.getOrDefault(c,c>255?9:6)+(bold?(metrics.containsKey(c)||c<=255?1:0.5):0)).sum();
        for(Component child:text.children())width+=advance(child,bold);return width;
    }
}
