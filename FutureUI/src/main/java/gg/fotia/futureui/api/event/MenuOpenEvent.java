package gg.fotia.futureui.api.event;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

/** 在服务器主线程触发的菜单扩展事件。 */
public final class MenuOpenEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();
    private final Player player;private final String menu;private final Map<String,Object> arguments;private boolean cancelled;
    public MenuOpenEvent(Player player,String menu,Map<String,?> arguments){this.player=player;this.menu=menu;this.arguments=new LinkedHashMap<>(arguments);}
    public Player player(){return player;}public String menu(){return menu;}public Map<String,Object> arguments(){return arguments;}
    public boolean isCancelled(){return cancelled;}public void setCancelled(boolean value){cancelled=value;}
    public HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
