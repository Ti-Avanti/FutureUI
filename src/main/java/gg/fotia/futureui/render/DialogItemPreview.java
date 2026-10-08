package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import java.util.*;
import net.kyori.adventure.text.Component;

/** 使用资源包图片展示物品，避免第三方包翻译器对原生 Dialog 物品结构的重编码差异。 */
public final class DialogItemPreview {
    private DialogItemPreview(){}
    public static DialogBody create(MenuController menus,MenuContext ctx,Node node,Map<String,Object> arguments){
        Node item=node.bool("product",false)?Node.of(ctx.variables().get("product.item")):node.child("item");String icon=item.text("icon",item.text("material","").toLowerCase(Locale.ROOT));int size=node.integer("image-size",72),width=node.integer("width",320);String key="large/"+size+"/"+icon;
        Component description=menus.text.render(ctx.player(),ctx.session().locale,node.text("description",item.text("name",item.text("material",""))),arguments);
        if(menus.glyphs.get(key+"_0")==null)return DialogBody.plainMessage(description,width);
        int textWidth=width-size-36;if(textWidth<18)throw new IllegalArgumentException("Item preview is too narrow");List<Component> lines=TextFlow.lines(description,textWidth,32);int height=Math.max(size+18,lines.size()*9+18);
        PixelCanvas canvas=new PixelCanvas(width,height,menus.glyphs);canvas.picture(key,9,9,size);for(int i=0;i<lines.size();i++)canvas.text(lines.get(i),size+27,9+i*9);
        return DialogBody.plainMessage(canvas.build(),width+32);
    }
}
