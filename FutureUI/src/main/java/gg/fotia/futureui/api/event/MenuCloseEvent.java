package gg.fotia.futureui.api.event;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

/** 在服务器主线程触发的菜单扩展事件。 */
public final class MenuCloseEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final Player player;private final String menu;
    public MenuCloseEvent(Player player,String menu){this.player=player;this.menu=menu;}
    public Player player(){return player;}public String menu(){return menu;}
    
    public HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
