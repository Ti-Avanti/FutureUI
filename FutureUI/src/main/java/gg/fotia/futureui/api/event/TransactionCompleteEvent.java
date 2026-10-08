package gg.fotia.futureui.api.event;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

/** 在服务器主线程触发的菜单扩展事件。 */
public final class TransactionCompleteEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final MenuContext context;private final UUID transaction;private final ActionResult result;private final String status;
    public TransactionCompleteEvent(MenuContext context,UUID transaction,ActionResult result,String status){this.context=context;this.transaction=transaction;this.result=result;this.status=status;}
    public MenuContext context(){return context;}public UUID transaction(){return transaction;}public ActionResult result(){return result;}public String status(){return status;}
    
    public HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
