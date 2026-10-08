package gg.fotia.futureui.api;

import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** 通过 Bukkit ServicesManager 获取，不持有全局静态插件单例。所有界面操作在主线程调用。 */
public interface FutureUIService {
    boolean open(Player player,String menu,Map<String,?> arguments);
    /** 只检查菜单及客户端渲染能力；权限、条件和打开事件仍由 open 处理，不等待资源包回执。 */
    boolean canRender(Player player,String menu);
    /** 匹配当前已打开的界面，用调用方自己的参数令牌隔离会话。 */
    boolean isOpen(Player player,String menu,Map<String,?> arguments);
    void close(Player player);
    void refresh(Player player);
    AutoCloseable registerAction(Plugin owner,String id,MenuAction action);
    AutoCloseable registerCondition(Plugin owner,String id,MenuCondition condition);
    AutoCloseable registerCurrency(Plugin owner,CurrencyProvider currency);
    AutoCloseable registerItems(Plugin owner,String id,ItemProvider provider);
    AutoCloseable registerData(Plugin owner,String id,MenuDataSource source);
}
