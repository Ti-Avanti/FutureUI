package gg.fotia.futureui.api.event;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

/** 在服务器主线程触发的菜单扩展事件。 */
public final class MenuClickEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();
    private final MenuContext context;private final Node component;private final Map<String,Object> submitted;private boolean cancelled;
    public MenuClickEvent(MenuContext context,Node component,Map<String,Object> submitted){this.context=context;this.component=component;this.submitted=Map.copyOf(submitted);}
    public MenuContext context(){return context;}public Node component(){return component;}public Map<String,Object> submitted(){return submitted;}
    public boolean isCancelled(){return cancelled;}public void setCancelled(boolean value){cancelled=value;}
    public HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
