package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;

/** 原生表单中的文本也可复用画布皮肤，内边距和换行保持一致。 */
public final class DialogTextPanel {
    private DialogTextPanel() {}

    public static DialogBody create(MenuController menus,MenuContext ctx,Node node,Map<String,Object> arguments){
        Component text=menus.text.render(ctx.player(),ctx.session().locale,node.text("text",""),arguments);
        MenuTheme theme=MenuTheme.of(menus.snapshot().assets(),ctx.session().menu.layout());
        int width=node.integer("width",420);String skin=theme.skin(menus.values.resolve(ctx.scoped(arguments),node.text("skin","")));
        if(skin.isBlank())return DialogBody.plainMessage(text,width);
        int padding=node.integer("padding",9),inner=width-padding*2;
        if(padding<0||padding%9!=0||inner<1)throw new IllegalArgumentException("Invalid text panel padding: "+node.text("id",""));
        if(menus.glyphs.get("canvas/"+skin+"/top/left")==null)throw new IllegalArgumentException("Unknown text panel skin: "+skin);
        List<Component> lines=TextFlow.lines(text,inner,64);int intrinsic=lines.size()*9+padding*2;
        int height=Math.max(intrinsic,node.integer("min-height",0));
        if(node.has("height")){height=node.integer("height",height);if(height<intrinsic)throw new IllegalArgumentException("Text panel height clips content: "+node.text("id",""));}
        PixelCanvas canvas=new PixelCanvas(width,height,menus.glyphs);canvas.frame(theme.frame());canvas.shape(skin,0,0,width,height);
        for(int i=0;i<lines.size();i++){
            Component line=lines.get(i);int lineWidth=PixelCanvas.measure(line);
            int offset=switch(node.text("text-align","start")){case "center"->(inner-lineWidth)/2;case "end"->inner-lineWidth;default->0;};
            int top=node.text("vertical-align","start").equals("center")?((height-intrinsic)/18)*9:node.text("vertical-align","start").equals("end")?height-intrinsic:0;
            canvas.text(line,padding+offset,padding+top+i*9);
        }
        return DialogBody.plainMessage(canvas.build(),width+32);
    }
}
