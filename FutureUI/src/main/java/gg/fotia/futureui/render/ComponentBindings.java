package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.condition.ValueResolver;
import gg.fotia.futureui.config.Node;
import java.util.*;

/** 模板参数逐层继承，完整占位符保留原始类型；不会递归解释玩家输入。 */
final class ComponentBindings {
    private ComponentBindings() {}

    static Node bind(MenuContext root,Node node,Map<String,Object> inherited,ValueResolver values){
        Map<String,Object> context=new LinkedHashMap<>(inherited);
        MenuContext parent=root.scoped(inherited);
        node.child("context").values().forEach((key,value)->context.put(key,resolve(parent,value,values)));
        Map<String,Object> result=new LinkedHashMap<>(node.values());
        result.put("context",Node.of(context));
        return Node.of(result);
    }

    private static Object resolve(MenuContext ctx,Object value,ValueResolver values){
        return values.object(ctx,value);
    }
}
