package gg.fotia.futureui.api.event;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

/** 在服务器主线程触发的菜单扩展事件。 */
public final class TransactionPrepareEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();
    private final MenuContext context;private final Node transaction;private boolean cancelled;
    public TransactionPrepareEvent(MenuContext context,Node transaction){this.context=context;this.transaction=transaction;}
    public MenuContext context(){return context;}public Node transaction(){return transaction;}
    public boolean isCancelled(){return cancelled;}public void setCancelled(boolean value){cancelled=value;}
    public HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
