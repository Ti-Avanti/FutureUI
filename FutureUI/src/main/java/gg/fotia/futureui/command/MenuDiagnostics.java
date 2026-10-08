package gg.fotia.futureui.command;

import gg.fotia.futureui.FutureUIPlugin;
import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.model.MenuSession;
import gg.fotia.futureui.placeholder.LiteralValue;
import gg.fotia.futureui.render.ViewCompiler;
import java.util.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** 诊断仅解析与检查，不执行配置中的业务动作。 */
final class MenuDiagnostics {
    private final FutureUIPlugin plugin;
    MenuDiagnostics(FutureUIPlugin plugin){this.plugin=plugin;}
    boolean execute(CommandSender sender,String[] args){
        if(args.length==0||!Set.of("validate","inspect","trace","dryrun").contains(args[0]))return false;
        if(!sender.hasPermission("futureui.admin")){message(sender,"messages.admin-only",Map.of());return true;}
        if(args[0].equals("validate")){plugin.validateConfiguration().whenComplete((snapshot,error)->plugin.threads().run(()->{if(error!=null){message(sender,"messages.validation-failed",Map.of("reason",new LiteralValue(root(error))));}else message(sender,"messages.validation-success",Map.of("menus",snapshot.menus().size(),"functions",snapshot.functions().size(),"rules",snapshot.rules().size()));}));return true;}
        if(!(sender instanceof Player player)){message(sender,"messages.player-required",Map.of());return true;}
        var menus=plugin.menus();MenuSession active=menus.sessions().get(player.getUniqueId());String id=args[0].equals("trace")?(active==null?menus.snapshot().settings().text("default-menu","main"):active.menu.id()):args.length>1?args[1]:active==null?"main":active.menu.id();var menu=menus.snapshot().menus().get(id);
        if(menu==null){message(sender,"messages.menu-unavailable",Map.of());return true;}
        MenuSession session=new MenuSession(player.getUniqueId(),menu,menus.text.locale(player),active==null?Map.of():active.variables);MenuContext ctx=new MenuContext(player,session);
        try{
            if(args[0].equals("trace")){
                String source=String.join(" ",Arrays.copyOfRange(args,1,args.length));var result=menus.values.trace(ctx,source,Node.of(Map.of()));int index=0;
                for(String round:result.rounds())message(sender,"messages.trace-step",Map.of("index",index++,"value",new LiteralValue(round)));
                message(sender,"messages.trace-result",Map.of("stop",result.stop(),"value",new LiteralValue(result.plain())));return true;
            }
            List<Node> compiled=new ViewCompiler(menus).compile(ctx);var report=menus.conditions.evaluate(ctx,menu.requirements());
            message(sender,"messages.inspect",Map.of("menu",id,"renderer",menu.renderer(),"components",ViewCompiler.flatten(compiled).size(),"allowed",report.success(),"locale",session.locale));
            if(args[0].equals("dryrun")){
                List<Node> selected=ViewCompiler.flatten(compiled).stream().filter(node->args.length<3||node.text("id","").equals(args[2])).toList();
                for(Node component:selected){var gate=menus.conditions.evaluate(ctx.scoped(component.child("context").values()),component.condition("requirements"));
                    if(!component.nodes("actions").isEmpty())message(sender,"messages.dryrun-step",Map.of("id",component.text("id",""),"allowed",gate.success(),"actions",new LiteralValue(component.nodes("actions").stream().map(n->n.text("type","")).reduce((a,b)->a+" → "+b).orElse(""))));
                }
                message(sender,"messages.dryrun-note",Map.of());
            }
        }catch(Exception error){message(sender,"messages.validation-failed",Map.of("reason",new LiteralValue(root(error))));}
        return true;
    }
    private void message(CommandSender sender,String key,Map<String,?> args){var text=plugin.menus().text;Player player=sender instanceof Player p?p:null;sender.sendMessage(text.render(player,player==null?text.catalog().fallback():text.locale(player),"@"+key,args));}
    private static String root(Throwable error){while(error.getCause()!=null)error=error.getCause();return Objects.toString(error.getMessage(),error.getClass().getSimpleName());}
}
