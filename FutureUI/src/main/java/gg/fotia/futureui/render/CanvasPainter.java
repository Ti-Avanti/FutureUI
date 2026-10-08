package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.layout.LayoutEngine;
import gg.fotia.futureui.menu.MenuController;
import java.time.Duration;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.NamedTextColor;

/** 画布页与表单内展示区共用布局、皮肤和文本绘制，不绑定业务数据。 */
public final class CanvasPainter {
    public record Drawing(Component content,int width,int height){}
    private final MenuController menus;
    private final LayoutEngine layout=new LayoutEngine();
    public CanvasPainter(MenuController menus){this.menus=menus;}
    public Drawing paint(MenuContext ctx,List<Node> components,Node settings,boolean interactive){
        var measured=layout.measure(components,settings);int width=measured.width(),height=measured.height();List<LayoutEngine.Box> boxes=measured.boxes();
        MenuTheme theme=MenuTheme.of(menus.snapshot().assets(),settings.has("theme")?settings:ctx.session().menu.layout());
        PixelCanvas canvas=new PixelCanvas(width,height,menus.glyphs);canvas.pointerDescriptions(theme.configuration().child("canvas").child("interaction").bool("pointer-descriptions",false));canvas.frame(theme.frame());UUID token=ctx.session().token;
        canvas.nativeExitButton(settings.bool("exit-button",true));
        if(settings.has("skin"))canvas.shape(theme.skin(menus.values.resolve(ctx,settings.text("skin",""))),0,0,width,height);
        paintBoxes(canvas,ctx,boxes,theme,token,interactive,width);
        return new Drawing(canvas.build(),width,height);
    }
    private void paintBoxes(PixelCanvas canvas,MenuContext ctx,List<LayoutEngine.Box> boxes,MenuTheme theme,UUID token,boolean interactive,int canvasWidth){
        for(var box:boxes){Node node=box.component();MenuContext scoped=ctx.scoped(node.child("context").values());String type=node.text("type","text"),skin=theme.skin(menus.values.resolve(scoped,node.text("skin","")));
            if(!skin.isBlank()&&!Set.of("button","toggle").contains(type))canvas.shape(skin,box.x(),box.y(),box.width(),box.height());
            if(node.has("background-image"))CanvasImages.draw(menus,canvas,scoped,node,box,"background-image",node.integer("background-size",72));
            switch(type){
                case "viewport"->{
                    if(!interactive||!ctx.session().menu.renderer().equals("canvas"))throw new IllegalArgumentException("Viewport requires a standalone Canvas display region");
                    CanvasViewport.draw(menus,canvas,scoped,box,canvasWidth,children->paintBoxes(canvas,ctx,children,theme,token,false,canvasWidth));
                }
                case "image"->CanvasImages.draw(menus,canvas,scoped,node,box,"image",node.integer("size",72));
                case "raster"->drawRaster(canvas,ctx,scoped,node,box,theme,token,interactive);
                case "chart"->CanvasCharts.draw(menus,canvas,scoped,node,box,theme);
                case "text"->drawText(canvas,menus.text.render(scoped.player(),scoped.session().locale,node.text("text",""),scoped.variables()),box);
                case "progress"->{double value=Double.parseDouble(menus.values.resolve(scoped,node.text("value","0")));int filled=(int)Math.round(box.width()*Math.max(0,Math.min(1,value)));canvas.shape(theme.skin(menus.values.resolve(scoped,node.text("track-skin","well"))),box.x(),box.y(),box.width(),box.height());if(filled>=4)canvas.shape(theme.skin(menus.values.resolve(scoped,node.text("fill-skin","selected"))),box.x(),box.y(),filled,box.height());}
                case "button","toggle"->{if(!interactive)throw new IllegalArgumentException("Dialog canvas sections only support display components");boolean enabled=menus.conditions.test(scoped,node.condition("enabled"))&&menus.conditions.test(scoped,node.condition("requirements"));Component label=menus.text.render(ctx.player(),ctx.session().locale,node.text("text",""),scoped.variables());
                    if(type.equals("toggle")){String key=menus.values.resolve(scoped,node.text("key",box.id()));label=label.append(Component.space()).append(menus.text.render(ctx.player(),ctx.session().locale,Boolean.parseBoolean(String.valueOf(menus.values.value(scoped,key)))?"@common.on":"@common.off",scoped.variables()));}
                    Component hint=menus.text.render(ctx.player(),ctx.session().locale,node.text("tooltip",""),scoped.variables());ClickEvent click=enabled?ClickEvent.callback(audience->{if(audience instanceof org.bukkit.entity.Player player&&player.getUniqueId().equals(ctx.player().getUniqueId()))menus.threads.run(()->menus.click(ctx,token,node,Map.of()));},ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(ctx.session().menu.callbackLifetimeSeconds())).build()):null;
                    canvas.button(theme.skin(menus.values.resolve(scoped,enabled?node.text("skin","button"):node.text("disabled-skin","disabled"))),box.x(),box.y(),box.width(),box.height(),enabled?label:label.color(NamedTextColor.GRAY),click,hint,node.text("text-align","center"),node.integer("padding",0),menus.values.resolve(scoped,node.text("icon","")),node.integer("icon-size",18),node.integer("icon-gap",6));
                }
                case "row","column","grid","spacer"->{}
                default->throw new IllegalArgumentException("Unsupported canvas component: "+type);
            }
        }
    }
    private void drawRaster(PixelCanvas canvas,MenuContext ctx,MenuContext scoped,Node node,LayoutEngine.Box box,MenuTheme theme,UUID token,boolean interactive){
        if(!node.nodes("actions").isEmpty()){
            if(!interactive)throw new IllegalArgumentException("Dialog canvas previews cannot have actions");
            boolean enabled=menus.conditions.test(scoped,node.condition("enabled"))&&menus.conditions.test(scoped,node.condition("requirements"));
            ClickEvent click=enabled?ClickEvent.callback(audience->{if(audience instanceof org.bukkit.entity.Player player&&player.getUniqueId().equals(ctx.player().getUniqueId()))menus.threads.run(()->menus.click(ctx,token,node,Map.of()));},ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(ctx.session().menu.callbackLifetimeSeconds())).build()):null;
            canvas.button(theme.skin(menus.values.resolve(scoped,node.text(enabled?"skin":"disabled-skin",enabled?"button":"disabled"))),box.x(),box.y(),box.width(),box.height(),Component.empty(),click,null);
        }
        Object value=menus.values.object(scoped,node.get("value"));
        if(value instanceof gg.fotia.futureui.api.RasterImage bitmap)CanvasRaster.draw(canvas,bitmap,box);
        else if(node.has("empty-text"))drawText(canvas,menus.text.render(ctx.player(),ctx.session().locale,node.text("empty-text",""),scoped.variables()),box);
    }
    private void drawText(PixelCanvas canvas,Component text,LayoutEngine.Box box){
        Node node=box.component();int padding=node.integer("padding",0),width=box.width()-padding*2,height=box.height()-padding*2;
        if(width<1||height<9)throw new IllegalArgumentException("Text padding exceeds its bounds: "+box.id());
        List<Component> lines=TextFlow.lines(text,width,height/9);String align=node.text("text-align","start");
        int unused=height-lines.size()*9;int top=box.y()+padding+switch(node.text("vertical-align","start")){case "center"->(unused/18)*9;case "end"->unused;default->0;};
        for(int i=0;i<lines.size();i++){Component line=lines.get(i);int lineWidth=PixelCanvas.measure(line);int offset=align.equals("center")?(width-lineWidth)/2:align.equals("end")?width-lineWidth:0;canvas.text(line,box.x()+padding+offset,top+i*9);}
    }
}
