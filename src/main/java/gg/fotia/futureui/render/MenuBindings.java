package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import java.util.*;

/** 页面绘制前计算只读值，商店与工坊无需专属代码或开屏后的第二次刷新。 */
final class MenuBindings {
    private MenuBindings(){}
    static void populate(MenuContext ctx,MenuController menus){
        gg.fotia.futureui.config.VariableMaps.flatten(ctx.session().menu.source().child("defaults"),false).forEach((key,value)->ctx.variables().putIfAbsent(key,menus.values.object(ctx,value)));
        gg.fotia.futureui.config.VariableMaps.flatten(ctx.session().menu.source().child("bindings"),true).forEach((key,raw)->{
            Node source=Node.of(raw),node=menus.values.bind(ctx,source,Set.of());Object value;
            if(node.bool("initial-only",false)&&ctx.variables().containsKey(key))return;
            value=switch(node.text("type","value")){
                case "data"->menus.storage.get(node.text("scope","player").equals("global")?"global":ctx.player().getUniqueId().toString(),node.text("key",key),source.has("default")?source.get("default"):"");
                case "item-count"->menus.itemMatcher.count(ctx,node.child("match"));
                case "currency"->{var currency=menus.currencies.get(node.text("currency","vault"));yield currency==null||!currency.available()?"—":currency.balance(ctx.player());}
                case "calculate"->gg.fotia.futureui.condition.Arithmetic.evaluate(node.text("expression","0"));
                case "condition"->menus.conditions.test(ctx,node.condition("condition"));
                case "date"->java.time.ZonedDateTime.now(java.time.ZoneId.of(node.text("timezone","UTC"))).format(java.time.format.DateTimeFormatter.ofPattern(node.text("format","yyyy-MM-dd")));
                default->menus.values.object(ctx,source.get("value"));
            };
            ctx.variables().put(key,value==null?"":value);
        });
    }
}
