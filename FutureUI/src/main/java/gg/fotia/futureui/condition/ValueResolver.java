package gg.fotia.futureui.condition;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.placeholder.PlaceholderResolver;
import gg.fotia.futureui.state.DataStore;
import gg.fotia.futureui.state.PlayerStateStore;
import java.util.*;
import org.bukkit.entity.Player;

/** 数据替换用于条件和动作参数，不执行代码。 */
public final class ValueResolver {
    private final PlayerStateStore state;private final DataStore data;
    private final PlaceholderResolver placeholders;
    public ValueResolver(PlayerStateStore state,DataStore data,boolean papi,Node policy){this.state=state;this.data=data;placeholders=new PlaceholderResolver(papi,policy);}
    public PlaceholderResolver placeholders(){return placeholders;}
    public Object value(MenuContext context,String key){
        if(key.equals("page"))return context.session().page+1;
        return value(context.player(),context.variables(),key);
    }
    public Object value(Player player,Map<String,?> variables,String key){
        if(player==null)return variables.get(key);
        if(key.startsWith("data."))return data.get(player.getUniqueId().toString(),key.substring(5),state.get(player.getUniqueId(),key.substring(5),""));
        if(key.startsWith("global."))return data.get("global",key.substring(7),"");
        if(key.startsWith("cooldown."))return (data.remaining(player.getUniqueId().toString(),"cooldown."+key.substring(9))+999)/1000;
        return switch(key){
            case "player" ,"player.name"->player.getName();
            case "player.uuid"->player.getUniqueId().toString();
            case "player.level"->player.getLevel();
            case "player.health"->player.getHealth();
            case "player.world"->player.getWorld().getName();
            case "player.gamemode"->player.getGameMode().name();
            default->variables.get(key);
        };
    }
    public String resolve(MenuContext context,String input){
        return resolve(context,input,Node.of(Map.of()));
    }
    public String resolve(MenuContext context,String input,Node policy){return trace(context,input,policy).plain();}
    public PlaceholderResolver.Resolution trace(MenuContext context,String input,Node policy){return placeholders.resolve(context.player(),input,key->value(context,key),policy);}
    public Object object(MenuContext ctx,Object source){
        if(source instanceof Node n){if(n.has("value")&&n.has("parse")){var result=trace(ctx,n.text("value",""),n.child("parse"));return !n.child("parse").text("mode","recursive").equals("recursive")||!result.literals().isEmpty()?new gg.fotia.futureui.placeholder.LiteralValue(result.plain()):result.plain();}return source;}
        if(!(source instanceof String input))return source;
        if(input.matches("\\{[a-zA-Z0-9_.-]+}")){String key=input.substring(1,input.length()-1);Object value=value(ctx,key);if(value!=null&&!(value instanceof String))return value;if(value instanceof String s&&PlaceholderResolver.untrusted(key))return new gg.fotia.futureui.placeholder.LiteralValue(s);}
        var resolution=trace(ctx,input,Node.of(Map.of()));String result=resolution.plain();return resolution.literals().isEmpty()?result:new gg.fotia.futureui.placeholder.LiteralValue(result);
    }
    /** 只绑定当前节点的标量；分支、动作和条件在真正执行时再读取最新状态。 */
    public Node bind(MenuContext ctx,Node node,Set<String> literalFields){
        Map<String,Object> map=new LinkedHashMap<>();node.values().forEach((key,v)->map.put(key,literalFields.contains(key)?v:v instanceof String?object(ctx,v):v instanceof List<?> l?l.stream().map(e->e instanceof String?object(ctx,e):e).toList():v));return Node.of(map);
    }
}
