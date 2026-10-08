package gg.fotia.futureui.command;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import gg.fotia.futureui.model.*;
import gg.fotia.futureui.placeholder.LiteralValue;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

/** 命令与物品入口通过公开 CommandMap 注册，可热重载且不覆盖其他插件命令。 */
public final class MenuEntryBindings implements Listener,AutoCloseable {
    private final MenuController menus;private final Set<Command> commands=new HashSet<>();
    public MenuEntryBindings(MenuController menus){this.menus=menus;Bukkit.getPluginManager().registerEvents(this,menus.plugin);reload();}
    public void reload(){
        unregister();for(MenuDefinition menu:menus.snapshot().menus().values())for(String label:menu.source().strings("open-commands")){
            Command command=new BoundCommand(label,menu.id());commands.add(command);Bukkit.getCommandMap().register("futureui",command);
        }
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
    }
    private final class BoundCommand extends Command {
        private final String menu;
        BoundCommand(String name,String menu){super(name);this.menu=menu;}
        @Override public boolean execute(CommandSender sender,String label,String[] args){
            if(!(sender instanceof Player player))return true;MenuDefinition definition=menus.snapshot().menus().get(menu);if(definition==null)return true;
            List<Node> parameters=definition.source().nodes("parameters");Map<String,Object> values=new LinkedHashMap<>();
            if(args.length>parameters.size()){player.sendMessage(menus.text.message(player,"messages.argument-invalid",Map.of("argument","arguments")));return true;}
            for(int i=0;i<args.length;i++)values.put(parameters.get(i).text("name",""),new LiteralValue(args[i]));menus.open(player,menu,values);return true;
        }
        @Override public List<String> tabComplete(CommandSender sender,String alias,String[] args){
            MenuDefinition definition=menus.snapshot().menus().get(menu);if(definition==null||!sender.hasPermission(definition.permission()))return List.of();List<Node> parameters=definition.source().nodes("parameters");int index=args.length-1;if(index<0||index>=parameters.size())return List.of();Node parameter=parameters.get(index);
            List<String> values=parameter.text("type","").equals("player")?Bukkit.getOnlinePlayers().stream().map(Player::getName).toList():parameter.text("type","").equals("boolean")?List.of("true","false"):parameter.strings("values");return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(args[index].toLowerCase(Locale.ROOT))).toList();
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=false) public void interact(PlayerInteractEvent event){
        if(event.getHand()!=EquipmentSlot.HAND||event.getItem()==null)return;
        for(MenuDefinition menu:menus.snapshot().menus().values()){
            if(!event.getPlayer().hasPermission(menu.permission()))continue;MenuContext ctx=new MenuContext(event.getPlayer(),new MenuSession(event.getPlayer().getUniqueId(),menu,menus.text.locale(event.getPlayer()),Map.of()));
            for(Node binding:menu.source().nodes("item-bindings")){
                String click=binding.text("click","right");boolean match=click.equals("right")?event.getAction().isRightClick():click.equals("left")?event.getAction().isLeftClick():event.getAction().name().equalsIgnoreCase(click);
                if(match&&menus.conditions.test(ctx,binding.condition("condition"))&&menus.itemMatcher.matches(ctx,event.getItem(),binding.child("item"))){event.setCancelled(true);Bukkit.getScheduler().runTask(menus.plugin,()->menus.open(event.getPlayer(),menu.id(),binding.child("arguments").values()));return;}
            }
        }
    }
    @EventHandler public void join(PlayerJoinEvent event){trigger(event.getPlayer(),"join");}
    @EventHandler public void respawn(PlayerRespawnEvent event){trigger(event.getPlayer(),"respawn");}
    @EventHandler public void world(PlayerChangedWorldEvent event){trigger(event.getPlayer(),"world-change");}
    private void trigger(Player player,String type){Bukkit.getScheduler().runTask(menus.plugin,()->{for(MenuDefinition menu:menus.snapshot().menus().values())for(Node trigger:menu.source().nodes("open-events"))if(trigger.text("event","").equals(type)&&player.hasPermission(menu.permission())){MenuContext ctx=new MenuContext(player,new MenuSession(player.getUniqueId(),menu,menus.text.locale(player),Map.of()));if(menus.conditions.test(ctx,trigger.condition("condition"))){menus.open(player,menu.id(),trigger.child("arguments").values());return;}}});}
    private void unregister(){var map=Bukkit.getCommandMap();new ArrayList<>(map.getKnownCommands().entrySet()).forEach(e->{if(commands.contains(e.getValue()))map.getKnownCommands().remove(e.getKey(),e.getValue());});commands.forEach(c->c.unregister(map));commands.clear();}
    @Override public void close(){unregister();HandlerList.unregisterAll(this);}
}
