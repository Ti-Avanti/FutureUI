package gg.fotia.futureui.command;

import gg.fotia.futureui.FutureUIPlugin;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

/** 管理与玩家入口，权限在每次执行时校验。 */
public final class FutureUICommand implements CommandExecutor,TabCompleter {
    private final FutureUIPlugin plugin;
    public FutureUICommand(FutureUIPlugin plugin){this.plugin=plugin;}
    public boolean onCommand(CommandSender sender,Command command,String alias,String[] args){
        var menus=plugin.menus();if(menus==null){if(args.length>0&&args[0].equals("reload")&&sender.hasPermission("futureui.admin"))plugin.reloadAll().whenComplete((ignored,error)->plugin.threads().run(()->sender.sendMessage(error==null?"FutureUI loaded.":"FutureUI configuration failed; inspect the server log.")));else sender.sendMessage("FutureUI is initializing; inspect server startup logs.");return true;}
        if(new MenuDiagnostics(plugin).execute(sender,args))return true;
        String sub=args.length==0?"open":args[0].toLowerCase(Locale.ROOT);
        if(Set.of("reload","pack","status","transactions","openfor").contains(sub)&&!sender.hasPermission("futureui.admin")){message(sender,"messages.admin-only");return true;}
        switch(sub){
            case "open"->{if(sender instanceof Player player){String id=args.length>1?args[1]:menus.snapshot().settings().text("default-menu","main");Map<String,Object> parameters=new LinkedHashMap<>();for(int i=2;i<args.length;i++){String[] pair=args[i].split("=",2);if(pair.length==2)parameters.put(pair[0],pair[1]);}menus.open(player,id,parameters);}}
            case "close"->{if(sender instanceof Player player)menus.close(player);}
            case "cancel"->{if(sender instanceof Player player)menus.inputs.cancel(player.getUniqueId(),false);}
            case "reload"->(args.length>1?plugin.reloadMenu(args[1]):plugin.reloadAll()).whenComplete((ignored,error)->plugin.threads().run(()->{message(sender,error==null?"messages.reload-success":"messages.reload-failed");if(error!=null)plugin.getLogger().log(java.util.logging.Level.WARNING,"配置重载失败",error);}));
            case "pack"->{message(sender,"messages.pack-build-started");plugin.buildPack().whenComplete((ignored,error)->plugin.threads().run(()->{if(error==null)message(sender,"messages.pack-built",Map.of("path",plugin.packArchive().toString(),"backend",plugin.packBackend()));else{message(sender,"messages.pack-build-failed");plugin.getLogger().log(java.util.logging.Level.WARNING,"资源包构建失败",error);}}));}
            case "status"->{message(sender,"messages.runtime-status",Map.of("menus",menus.snapshot().menus().size(),"revision",menus.snapshot().revision(),"sessions",menus.sessions().size(),"currencies",String.join(", ",menus.currencies.ids())));message(sender,"messages.pack-backend",Map.of("backend",plugin.packBackend()));if(sender instanceof Player player)message(sender,"messages.player-status",Map.of("locale",menus.text.locale(player),"translator",menus.text.backend()!=null&&menus.text.backend().ready(),"pack",plugin.packs()!=null&&plugin.packs().ready(player.getUniqueId())));if(sender instanceof Player player)message(sender,"messages.client-profile",Map.of("protocol",gg.fotia.futureui.integration.ClientCapabilities.protocol(player),"profile",menus.packProfile(player)));if(plugin.packs()!=null&&!plugin.packs().problem().isEmpty())message(sender,"messages.pack-verification",Map.of("reason",plugin.packs().problem()));}
            case "openfor"->{if(args.length>=3){Player player=Bukkit.getPlayerExact(args[1]);if(player!=null)menus.open(player,args[2],Map.of());}}
            case "transactions"->{if(args.length==4&&args[1].equals("resolve")){try{UUID id=UUID.fromString(args[2]);plugin.journal().resolve(id,args[3].toUpperCase(Locale.ROOT)).whenComplete((ignored,error)->plugin.threads().run(()->message(sender,error==null?"messages.transaction-reviewed":"messages.transaction-review-failed",Map.of("id",id))));}catch(IllegalArgumentException error){message(sender,"messages.transaction-usage");}}else {var pending=plugin.journal().unresolved();if(pending.isEmpty())message(sender,"messages.transactions-empty");else pending.forEach((id,record)->message(sender,"messages.transaction-entry",Map.of("id",id,"record",record)));}}
            default->message(sender,"messages.command-usage");
        }return true;
    }
    private void message(CommandSender sender,String key){message(sender,key,Map.of());}
    private void message(CommandSender sender,String key,Map<String,?> args){var text=plugin.menus().text;Player player=sender instanceof Player p?p:null;sender.sendMessage(text.render(player,player==null?text.catalog().fallback():text.locale(player),"@"+key,args));}
    public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return List.of("open","close","cancel","reload","pack","status","transactions","validate","inspect","trace","dryrun").stream().filter(s->s.startsWith(args[0])).filter(s->Set.of("open","close","cancel").contains(s)||sender.hasPermission("futureui.admin")).toList();
        if(args.length==2&&args[0].equals("open")&&plugin.menus()!=null)return plugin.menus().snapshot().menus().keySet().stream().filter(id->id.startsWith(args[1])).sorted().toList();return List.of();
    }
}
