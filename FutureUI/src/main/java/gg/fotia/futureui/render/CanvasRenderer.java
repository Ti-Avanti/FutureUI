package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistrySet;
import java.time.Duration;
import java.util.*;
import net.kyori.adventure.text.event.ClickCallback;

/** 画布页的原生 Dialog 外壳；绘制由共享画笔负责。 */
public final class CanvasRenderer {
    private final MenuController menus;
    public CanvasRenderer(MenuController menus){this.menus=menus;}
    public void render(MenuContext ctx,List<Node> components){
        var drawing=new CanvasPainter(menus).paint(ctx,components,ctx.session().menu.layout(),true);
        UUID token=ctx.session().token;
        Node layout=ctx.session().menu.layout();
        ActionButton close=ActionButton.create(menus.text.render(ctx.player(),ctx.session().locale,layout.text("exit-text","@common.close"),ctx.variables()),null,layout.integer("exit-width",200),DialogAction.customClick((response,audience)->{if(audience instanceof org.bukkit.entity.Player viewer&&viewer.getUniqueId().equals(ctx.player().getUniqueId()))menus.threads.run(()->menus.closeIfCurrent(ctx,token));},ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(ctx.session().menu.callbackLifetimeSeconds())).build()));
        // 保留当前画布，收到新内容后直接替换；等待页会使每次点击都闪屏。
        // Paper 的 MultiAction 要求至少一个按钮；空 DialogList 允许仅显示可交互画布。
        DialogType type=layout.bool("exit-button",true)?DialogType.notice(close):DialogType.dialogList(RegistrySet.valueSet(RegistryKey.DIALOG,List.of()),null,1,150);
        Dialog dialog=Dialog.create(builder->builder.empty().base(DialogBase.builder(menus.text.render(ctx.player(),ctx.session().locale,ctx.session().menu.title(),ctx.variables())).pause(false).canCloseWithEscape(true).body(List.of(DialogBody.plainMessage(drawing.content(),drawing.width()+32))).afterAction(DialogBase.DialogAfterAction.NONE).build()).type(type));
        ctx.player().showDialog(dialog);
    }
}
