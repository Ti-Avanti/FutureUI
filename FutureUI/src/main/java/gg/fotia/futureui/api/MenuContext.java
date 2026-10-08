package gg.fotia.futureui.api;

import gg.fotia.futureui.model.MenuSession;
import java.util.Map;
import org.bukkit.entity.Player;

/** 面向扩展点的玩家和参数上下文。 */
public record MenuContext(Player player,MenuSession session,Map<String,Object> variables) {
    public MenuContext(Player player,MenuSession session){this(player,session,session.variables);}
    public MenuContext scoped(Map<String,?> extra){Map<String,Object> scope=new java.util.LinkedHashMap<>(variables);scope.putAll(extra);return new MenuContext(player,session,scope);}
}
