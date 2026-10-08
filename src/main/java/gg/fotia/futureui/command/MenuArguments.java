package gg.fotia.futureui.command;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import gg.fotia.futureui.model.*;
import gg.fotia.futureui.placeholder.LiteralValue;
import java.math.BigDecimal;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** 声明式菜单参数：默认值、类型、范围、枚举和玩家解析共用。 */
public final class MenuArguments {
    private MenuArguments(){}
    public static Map<String,Object> normalize(MenuController menus,Player player,MenuDefinition menu,Map<String,?> supplied){
        Map<String,Object> result=new LinkedHashMap<>(supplied);MenuContext ctx=new MenuContext(player,new MenuSession(player.getUniqueId(),menu,menus.text.locale(player),supplied),result);
        for(Node field:menu.source().nodes("parameters")){
            String name=field.text("name","");Object raw=supplied.containsKey(name)?supplied.get(name):menus.values.object(ctx,field.get("default"));
            if(raw==null){if(field.bool("required",false))throw new IllegalArgumentException(name);continue;}
            String value=raw.toString();Object normalized;
            switch(field.text("type","text")){
                case "integer","number"->{BigDecimal number=new BigDecimal(value);if(field.text("type","").equals("integer"))number.toBigIntegerExact();if(number.compareTo(field.decimal("min","-1E100"))<0||number.compareTo(field.decimal("max","1E100"))>0)throw new IllegalArgumentException(name);normalized=number;}
                case "boolean"->{if(!Set.of("true","false").contains(value.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException(name);normalized=Boolean.parseBoolean(value);}
                case "enum"->{if(!field.strings("values").contains(value))throw new IllegalArgumentException(name);normalized=new LiteralValue(value);}
                case "player"->{Player target=Bukkit.getPlayerExact(value);if(target==null)try{target=Bukkit.getPlayer(UUID.fromString(value));}catch(IllegalArgumentException ignored){}if(target==null)throw new IllegalArgumentException(name);normalized=target.getUniqueId().toString();}
                case "text"->{if(value.length()>field.integer("max-length",128)||field.has("pattern")&&!com.google.re2j.Pattern.compile(field.text("pattern","")).matcher(value).matches())throw new IllegalArgumentException(name);normalized=new LiteralValue(value);}
                default->throw new IllegalArgumentException("Unknown argument type "+field.text("type",""));
            }
            result.put(name,normalized);result.put("argument."+name,normalized);
        }
        return result;
    }
    public static void validate(Node source,String path){Set<String> names=new HashSet<>();for(Node field:source.nodes("parameters")){String name=field.text("name","");if(!name.matches("[a-zA-Z0-9_.-]{1,80}")||!names.add(name))throw new IllegalArgumentException(path+": invalid or duplicate parameter "+name);if(!Set.of("text","integer","number","enum","boolean","player").contains(field.text("type","text")))throw new IllegalArgumentException(path+": unknown parameter type");if(field.has("pattern"))com.google.re2j.Pattern.compile(field.text("pattern",""));}}
}
