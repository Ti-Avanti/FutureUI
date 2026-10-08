package gg.fotia.futureui.model;

import java.util.*;

/** 玩家独立会话；仅在主线程修改。令牌随重绘更新，拒绝旧页面回调。 */
public final class MenuSession {
    public final UUID playerId;
    public final Map<String,Object> variables=new LinkedHashMap<>();
    public final Deque<History> history=new ArrayDeque<>();
    public final Map<String,String> errors=new LinkedHashMap<>();
    public final Map<String,Long> cooldowns=new HashMap<>();
    public final Set<String> consumed=new HashSet<>();
    public UUID token=UUID.randomUUID();
    public MenuDefinition menu;
    public java.util.List<gg.fotia.futureui.config.Node> renderedComponents=java.util.List.of();
    public String locale;
    public boolean busy;
    public boolean languageDirty;
    public boolean closed;
    public long openedAt=System.currentTimeMillis();
    public long lastInteraction=openedAt;
    public int page;
    public MenuSession(UUID playerId,MenuDefinition menu,String locale,Map<String,?> arguments) {
        this.playerId=playerId;this.menu=menu;this.locale=locale;variables.putAll(arguments);
    }
    public record History(String menu,Map<String,Object> variables,int page) {}
    public void invalidate() { token=UUID.randomUUID();consumed.clear(); }
}
