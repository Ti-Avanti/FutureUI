package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;

/** Dialog 原生控件保留真实点击区域、键盘焦点和客户端悬停绘制。 */
public final class DialogRenderer {
    private final MenuController menus;private final DialogFormRenderer forms;
    public DialogRenderer(MenuController menus){this.menus=menus;forms=new DialogFormRenderer(menus);}
    public void render(MenuContext ctx,List<Node> components){
        boolean nativeMode=ctx.session().menu.renderer().equals("native");if(nativeMode)components=NativeFallback.flatten(components);
        final List<Node> submittedComponents=components;
        List<DialogBody> body=new ArrayList<>();List<ActionButton> buttons=new ArrayList<>();UUID token=ctx.session().token;
        List<Node> dialogComponents=ViewCompiler.dialogComponents(components);
        for(Node node:dialogComponents){
            String type=node.text("type","text");MenuContext scoped=ctx.scoped(node.child("context").values());Map<String,Object> arguments=scoped.variables();
            switch(type){
                case "canvas"->{Node settings=node.child("layout").merge(Node.of(Map.of("width",node.integer("width",420))));var drawing=new CanvasPainter(menus).paint(ctx.scoped(node.child("context").values()),node.nodes("children"),settings,false);body.add(DialogBody.plainMessage(drawing.content(),drawing.width()+32));}
                case "text"->body.add(DialogTextPanel.create(menus,ctx,node,arguments));
                case "chart"->body.add(DialogBody.plainMessage(CanvasCharts.summary(menus,scoped,node),node.integer("width",420)));
                case "spacer"->body.add(DialogBody.plainMessage(Component.text("\n".repeat(Math.max(1,node.integer("height",8)/9))),420));
                case "image"->{var glyph=menus.glyphs.get(menus.values.resolve(scoped,node.text("image","")));if(glyph!=null){int top=Math.max(0,(glyph.height()-10)/9);body.add(DialogBody.plainMessage(Component.text("\n".repeat(top)+glyph.character()+"\n").font(net.kyori.adventure.key.Key.key("futureui:images")),node.integer("width",420)));}}
                case "item"->body.add(DialogItemPreview.create(menus,ctx,node,arguments));
                case "progress"->{double fraction=Math.max(0,Math.min(1,Double.parseDouble(menus.values.resolve(scoped,node.text("value","0")))));int full=(int)Math.round(fraction*20);body.add(DialogBody.plainMessage(Component.text("▰".repeat(full),NamedTextColor.GREEN).append(Component.text("▱".repeat(20-full),NamedTextColor.DARK_GRAY)),node.integer("width",420)));}
                case "button","toggle"->{boolean enabled=menus.conditions.test(scoped,node.condition("enabled"))&&menus.conditions.test(scoped,node.condition("requirements"))&&!ctx.session().busy;Component label=menus.text.render(ctx.player(),ctx.session().locale,node.text("text",""),arguments);
                    String icon=menus.values.resolve(scoped,node.text("icon","")).toLowerCase(Locale.ROOT);
                    if(!icon.isBlank()&&menus.glyphs.get(icon)!=null)label=Component.empty().append(menus.glyphs.image(icon)).append(Component.space()).append(label.font(net.kyori.adventure.key.Key.key("minecraft:default")));
                    if(type.equals("toggle")){String key=menus.values.resolve(scoped,node.text("key",node.text("id","")));boolean active=Boolean.parseBoolean(String.valueOf(menus.values.value(scoped,key)));label=label.append(Component.space()).append(menus.text.render(ctx.player(),ctx.session().locale,active?"@common.on":"@common.off",arguments));}
                    if(node.has("suffix"))label=label.append(menus.text.render(ctx.player(),ctx.session().locale,node.text("suffix",""),arguments));if(node.bool("selected",false))label=label.color(NamedTextColor.YELLOW);
                    if(!enabled)label=menus.text.render(ctx.player(),ctx.session().locale,node.text("disabled-text",node.text("text","")),arguments).color(NamedTextColor.GRAY);
                    Node button=node;Component tooltip=Boolean.parseBoolean(menus.state.get(ctx.player().getUniqueId(),"input.hints","true"))?menus.text.render(ctx.player(),ctx.session().locale,enabled?node.text("tooltip",""):node.text("disabled-tooltip","@messages.requirement-failed"),arguments):null;
                    DialogAction action=enabled?DialogAction.customClick((response,audience)->{if(audience instanceof org.bukkit.entity.Player player&&player.getUniqueId().equals(ctx.player().getUniqueId()))menus.threads.run(()->menus.click(ctx,token,button,forms.collect(response,submittedComponents)));},ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(ctx.session().menu.timeoutSeconds())).build()):null;
                    buttons.add(ActionButton.create(label,tooltip,node.integer("width",ctx.session().menu.layout().integer("button-width",140)),action));}
                default->{/* 输入控件由 forms 单独渲染。 */}
            }
        }
        int columns=ctx.session().menu.layout().integer("columns",3);
        if(!nativeMode){var feedback=forms.feedback(ctx,components);body.addAll(feedback.bodies());}
        else for(String error:ctx.session().errors.values())body.add(DialogBody.plainMessage(menus.text.render(ctx.player(),ctx.session().locale,error.startsWith("@")?error:"@"+error,ctx.variables()),400));
        int bodyWidth=dialogComponents.stream().mapToInt(node->node.integer("width",420)+(node.text("type","").equals("canvas")?32:0)).max().orElse(420);
        int buttonWidth=dialogComponents.stream().filter(node->Set.of("button","toggle").contains(node.text("type",""))).mapToInt(node->node.integer("width",ctx.session().menu.layout().integer("button-width",140))).max().orElse(140);
        int panelWidth=ctx.session().menu.layout().integer("panel-width",Math.max(bodyWidth,buttonWidth*columns+Math.max(0,columns-1)*2)+32);
        int panelHeight=ctx.session().menu.layout().integer("panel-height",0);
        if(!nativeMode&&panelHeight>0&&ctx.session().menu.layout().bool("resize-for-errors",true))panelHeight+=forms.feedback(ctx,components).height();
        Component title=menus.text.render(ctx.player(),ctx.session().locale,ctx.session().menu.title(),ctx.variables());if(!nativeMode)title=DialogBackground.title(menus.glyphs,title,panelWidth,panelHeight,MenuTheme.of(menus.snapshot().assets(),ctx.session().menu.layout()).background());
        Node layout=ctx.session().menu.layout();
        ActionButton exit=ActionButton.create(menus.text.render(ctx.player(),ctx.session().locale,layout.text("exit-text","@common.close"),ctx.variables()),null,layout.integer("exit-width",200),DialogAction.customClick((response,audience)->{if(audience instanceof org.bukkit.entity.Player viewer&&viewer.getUniqueId().equals(ctx.player().getUniqueId()))menus.threads.run(()->menus.closeIfCurrent(ctx,token));},ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(ctx.session().menu.timeoutSeconds())).build()));
        boolean exitOnly=buttons.isEmpty();if(exitOnly)buttons.add(exit);
        // 操作处理期间保留表单；关闭和页面替换由服务端会话明确执行。
        ActionButton footer=!exitOnly&&layout.bool("exit-button",true)?exit:null;
        Component finalTitle=title;Dialog dialog=Dialog.create(factory->factory.empty().base(DialogBase.builder(finalTitle).canCloseWithEscape(true).pause(false).body(body).inputs(forms.render(ctx,submittedComponents)).afterAction(DialogBase.DialogAfterAction.NONE).build()).type(DialogType.multiAction(buttons,footer,nativeMode?1:columns)));
        ctx.player().showDialog(dialog);
    }
}
