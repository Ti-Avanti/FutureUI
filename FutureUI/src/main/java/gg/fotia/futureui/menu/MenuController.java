package gg.fotia.futureui.menu;

import gg.fotia.futureui.action.ActionEngine;
import gg.fotia.futureui.api.*;
import gg.fotia.futureui.asset.GlyphRegistry;
import gg.fotia.futureui.condition.*;
import gg.fotia.futureui.config.*;
import gg.fotia.futureui.form.FormValidator;
import gg.fotia.futureui.i18n.TextService;
import gg.fotia.futureui.model.*;
import gg.fotia.futureui.render.*;
import gg.fotia.futureui.shop.*;
import gg.fotia.futureui.state.*;
import gg.fotia.futureui.util.ThreadGate;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** 管理会话生命周期；渲染、条件、业务动作各自独立。 */
public final class MenuController implements FutureUIService,Listener,AutoCloseable {
    public final JavaPlugin plugin;public final ThreadGate threads;public final TextService text;
    public final PlayerStateStore state;public final MetricsStore metrics;public final GlyphRegistry glyphs;
    public final DataStore storage;
    public final ExtensionRegistry<MenuAction> actions=new ExtensionRegistry<>();
    public final ExtensionRegistry<MenuCondition> conditionExtensions=new ExtensionRegistry<>();
    public final ExtensionRegistry<CurrencyProvider> currencies=new ExtensionRegistry<>();
    public final ExtensionRegistry<ItemProvider> itemProviders=new ExtensionRegistry<>();
    public final ExtensionRegistry<MenuDataSource> data=new ExtensionRegistry<>();
    public final ValueResolver values;public final ConditionEngine conditions;public final ItemFactory items;
    public final gg.fotia.futureui.item.ItemMatcher itemMatcher;
    public final HudRenderer hud;public ShopService shops;
    public gg.fotia.futureui.action.TransactionActions transactions;
    public final gg.fotia.futureui.shop.ShopOperations commerce;
    public final gg.fotia.futureui.form.InputCapture inputs;
    private gg.fotia.futureui.integration.FutureUIExpansion expansion;
    private gg.fotia.futureui.command.MenuEntryBindings entryBindings;
    private final Map<UUID,MenuSession> sessions=new HashMap<>();private final Set<UUID> replacing=new HashSet<>();
    private final ActionEngine executor;private final FormValidator forms;private final DialogRenderer dialogs;private final InventoryRenderer inventories;private final ViewCompiler compiler;
    private final InventoryInteractions inventoryInteractions;
    private volatile ConfigurationSnapshot snapshot;private long ticks;
    private gg.fotia.futureui.asset.PackProfiles packProfiles;
    public String packProfile(Player player){var profile=packProfiles.forProtocol(gg.fotia.futureui.integration.ClientCapabilities.protocol(player));return profile==null?"native":profile.directory();}
    public void resourcesReady(UUID id){threads.run(()->{
        Player player=Bukkit.getPlayer(id);if(player==null)return;
        MenuSession session=sessions.get(id);if(session==null)return;
        if(session.menu.form()){session.languageDirty=true;return;}
        MenuDefinition definition=compatible(player,snapshot.menus().get(session.menu.id()));
        if(definition==null){close(player);return;}
        session.menu=definition;
        refresh(player);
    });}
    public MenuController(JavaPlugin plugin,ThreadGate threads,TextService text,PlayerStateStore state,MetricsStore metrics,GlyphRegistry glyphs,ConfigurationSnapshot snapshot){
        this.plugin=plugin;this.threads=threads;this.text=text;this.state=state;this.metrics=metrics;this.glyphs=glyphs;this.snapshot=snapshot;this.packProfiles=gg.fotia.futureui.asset.PackProfiles.parse(snapshot.assets().child("versions"));
        storage=new DataStore(plugin.getDataFolder().toPath().resolve("data.json"),plugin.getLogger());
        values=new ValueResolver(state,storage,Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"),snapshot.settings().child("placeholders"));text.resolver(values);items=new ItemFactory(text,itemProviders,values);itemMatcher=new gg.fotia.futureui.item.ItemMatcher(values,text,itemProviders);conditions=new ConditionEngine(values,conditionExtensions,currencies,plugin.getLogger());conditions.items(itemMatcher);conditions.storage(storage);
        executor=new ActionEngine(this);forms=new FormValidator(conditions);dialogs=new DialogRenderer(this);inventories=new InventoryRenderer(this);compiler=new ViewCompiler(this);hud=new HudRenderer(this);
        inventoryInteractions=new InventoryInteractions(this);
        inputs=new gg.fotia.futureui.form.InputCapture(this);
        commerce=new gg.fotia.futureui.shop.ShopOperations(this);
        registerData(plugin,"cart",(ctx,node)->CompletableFuture.completedFuture(commerce.rows(ctx,false)));
        registerData(plugin,"sell-products",(ctx,node)->CompletableFuture.completedFuture(commerce.rows(ctx,true)));
        new gg.fotia.futureui.data.BuiltinDataSources(this).register();
        new ShopDataSources(this).register();
        conditions.rules(snapshot.rules());
        values.placeholders().literalOutput((player,token)->{
            if(token.startsWith("%futureui_data_")||token.startsWith("%futureui_global_"))return true;
            String prefix="%futureui_variable_";if(!token.startsWith(prefix)||!token.endsWith("%"))return false;String key=token.substring(prefix.length(),token.length()-1);MenuSession current=sessions.get(player.getUniqueId());return gg.fotia.futureui.placeholder.PlaceholderResolver.untrusted(key)||current!=null&&current.variables.get(key) instanceof gg.fotia.futureui.placeholder.LiteralValue;
        });
    }
    public void start(){entryBindings=new gg.fotia.futureui.command.MenuEntryBindings(this);if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")){expansion=new gg.fotia.futureui.integration.FutureUIExpansion(this);expansion.register();expansion.update();}Bukkit.getPluginManager().registerEvents(this,plugin);Bukkit.getMessenger().registerOutgoingPluginChannel(plugin,"BungeeCord");Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);Bukkit.getOnlinePlayers().forEach(p->state.load(p.getUniqueId()));}
    public CompletionStage<ActionResult> execute(MenuContext ctx,List<Node> actions){return executor.execute(ctx,actions);}
    public ConfigurationSnapshot snapshot(){return snapshot;}
    public Map<UUID,MenuSession> sessions(){return Collections.unmodifiableMap(sessions);}
    public boolean open(Player player,String id,Map<String,?> arguments){return navigate(player,id,arguments,false);}
    @Override public boolean canRender(Player player,String id){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Inspect menus on server thread");
        MenuDefinition definition=snapshot.menus().get(id);if(definition==null)return false;
        if(!Set.of("canvas","dialog","native").contains(definition.renderer()))return true;
        if(!gg.fotia.futureui.integration.ClientCapabilities.supportsDialog(player))return false;
        return definition.renderer().equals("native")||!packProfiles.unsupportedClient().equals("deny")||packProfiles.forProtocol(gg.fotia.futureui.integration.ClientCapabilities.protocol(player))!=null;
    }
    @Override public boolean isOpen(Player player,String id,Map<String,?> arguments){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Inspect menus on server thread");
        MenuSession session=sessions.get(player.getUniqueId());
        return session!=null&&!session.closed&&session.menu.id().equals(id)&&arguments.entrySet().stream().allMatch(e->Objects.equals(session.variables.get(e.getKey()),e.getValue()));
    }
    public boolean navigate(Player player,String id,Map<String,?> arguments,boolean keepHistory){
        return navigate(player,id,arguments,keepHistory,next->{});
    }
    private boolean navigate(Player player,String id,Map<String,?> arguments,boolean keepHistory,java.util.function.Consumer<MenuSession> initialize){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Open menus on server thread");MenuDefinition definition=compatible(player,snapshot.menus().get(id));
        if(definition==null||!player.hasPermission(definition.permission())){player.sendMessage(text.message(player,"messages.menu-unavailable",Map.of()));return false;}
        var opening=new gg.fotia.futureui.api.event.MenuOpenEvent(player,id,arguments);Bukkit.getPluginManager().callEvent(opening);if(opening.isCancelled())return false;
        try{arguments=gg.fotia.futureui.command.MenuArguments.normalize(this,player,definition,opening.arguments());}catch(IllegalArgumentException invalid){player.sendMessage(text.message(player,"messages.argument-invalid",Map.of("argument",String.valueOf(invalid.getMessage()))));return false;}
        MenuSession old=sessions.get(player.getUniqueId());MenuSession next=new MenuSession(player.getUniqueId(),definition,text.locale(player),arguments);
        state.snapshot(player.getUniqueId()).forEach((key,value)->next.variables.putIfAbsent(key,value));next.variables.put("player",player.getName());next.variables.put("player.display",state.get(player.getUniqueId(),"input.nickname",player.getName()));
        initialize.accept(next);
        MenuContext context=new MenuContext(player,next);ConditionEngine.Result requirements=conditions.evaluate(context,definition.requirements());if(!requirements.success()){executor.requirementHandlers(context,requirements);player.sendMessage(text.message(player,"messages.requirement-failed",Map.of()));return false;}
        if(old!=null){if(keepHistory){next.history.addAll(old.history);if(next.history.size()>=32)next.history.removeFirst();next.history.addLast(new MenuSession.History(old.menu.id(),new LinkedHashMap<>(old.variables),old.page));}finish(player,old);}
        sessions.put(player.getUniqueId(),next);metrics.count("open",id);next.busy=true;
        executor.requirementHandlers(context,requirements).thenCompose(result->result.success()?executor.execute(context,definition.onOpen()):CompletableFuture.completedFuture(result)).whenComplete((result,error)->threads.run(()->{
            next.busy=false;if(error!=null||result==null||!result.success())report(context,result==null?"messages.action-failed":result.message());
            if(isCurrent(context))draw(context);
        }));return true;
    }
    public void back(Player player){
        back(player,Map.of());
    }
    private MenuDefinition compatible(Player player,MenuDefinition definition){
        if(definition==null||!Set.of("canvas","dialog","native").contains(definition.renderer()))return definition;
        if(!gg.fotia.futureui.integration.ClientCapabilities.supportsDialog(player)){player.sendMessage(text.message(player,"messages.client-unsupported",Map.of()));return null;}
        boolean missingProfile=packProfiles.forProtocol(gg.fotia.futureui.integration.ClientCapabilities.protocol(player))==null;
        if(missingProfile&&packProfiles.unsupportedClient().equals("deny")&&!definition.renderer().equals("native")){player.sendMessage(text.message(player,"messages.shader-unsupported",Map.of("protocol",gg.fotia.futureui.integration.ClientCapabilities.protocol(player))));return null;}
        if(missingProfile||state.get(player.getUniqueId(),"input.renderer","auto").equals("native"))return new MenuDefinition(definition.id(),definition.title(),"native",definition.layout(),definition.components(),definition.requirements(),definition.onOpen(),definition.onClose(),definition.refreshTicks(),definition.timeoutSeconds(),definition.permission(),definition.source());
        return definition;
    }
    public void back(Player player,Map<String,?> values){
        MenuSession session=sessions.get(player.getUniqueId());if(session==null)return;if(session.history.isEmpty()){close(player);return;}
        Deque<MenuSession.History> remaining=new ArrayDeque<>(session.history);MenuSession.History previous=remaining.removeLast();
        // 首次绘制前恢复历史页码，避免先显示第一页，再发送第二次重绘。
        Map<String,Object> restored=new LinkedHashMap<>(previous.variables());restored.putAll(values);
        navigate(player,previous.menu(),restored,false,next->{next.history.addAll(remaining);next.page=previous.page();});
    }
    public void refresh(Player player){
        MenuSession session=sessions.get(player.getUniqueId());if(session==null||session.closed)return;
        // 动作链完成时统一刷新，避免翻页、语言变更等动作先重绘一次，再被完成回调重复重绘。
        if(session.busy)return;
        session.locale=text.locale(player);session.languageDirty=false;draw(new MenuContext(player,session));
    }
    private void draw(MenuContext ctx){
        if(ctx.session().closed)return;ctx.session().invalidate();
        replacing.add(ctx.player().getUniqueId());
        try(var frame=values.placeholders().frame()){List<Node> components=compiler.compile(ctx);ctx.session().renderedComponents=components;switch(ctx.session().menu.renderer()){case "canvas"->new CanvasRenderer(this).render(ctx,components);case "inventory"->inventories.render(ctx,components);case "hud"->hud.show(ctx,ctx.session().menu.source().text("hud","status"));default->dialogs.render(ctx,components);}}
        catch(Exception error){plugin.getLogger().log(java.util.logging.Level.WARNING,"菜单渲染失败: "+ctx.session().menu.id(),error);report(ctx,"messages.render-failed");}
        finally{replacing.remove(ctx.player().getUniqueId());}
    }
    public void click(MenuContext ctx,UUID token,Node button,Map<String,Object> submitted){
        if(!isCurrent(ctx)||ctx.session().busy)return;
        // 动画重绘不应丢失关闭操作；只放行同一会话的无条件纯关闭按钮。
        // 交易、扩展动作及带条件的按钮仍必须匹配当前帧令牌。
        if(!ctx.session().token.equals(token)&&!unconditionalCanvasClose(ctx,button))return;
        String buttonId=button.text("id","");if(!ctx.session().consumed.add(buttonId))return;
        var event=new gg.fotia.futureui.api.event.MenuClickEvent(ctx,button,submitted);Bukkit.getPluginManager().callEvent(event);if(event.isCancelled()){ctx.session().consumed.remove(buttonId);return;}
        ctx.session().lastInteraction=System.currentTimeMillis();ctx.variables().putAll(button.child("context").values());submitted.forEach((key,value)->ctx.variables().put("input."+key,value));
        if(button.bool("validate",true)&&ctx.session().menu.form()){
            Map<String,String> errors=forms.validate(ctx,ctx.session().renderedComponents,submitted);ctx.session().errors.clear();ctx.session().errors.putAll(errors);if(!errors.isEmpty()){refresh(ctx.player());return;}
        }
        if(!ctx.player().hasPermission(ctx.session().menu.permission())){report(ctx,"messages.requirement-failed");refresh(ctx.player());return;}
        List<Node> list=button.nodes("actions");if(button.text("type","").equals("toggle")){List<Node> full=new ArrayList<>();full.add(Node.of(Map.of("type","toggle","key",button.text("key",buttonId),"persist",button.bool("persist",false))));full.addAll(list);list=full;}
        List<Node> checked=new ArrayList<>();checked.add(Node.of(Map.of("type","require","requirements",Node.of(Map.of("type","all","conditions",List.of(button.condition("enabled"),button.condition("requirements")))))));checked.addAll(list);list=checked;
        ctx.session().busy=true;metrics.count("click",ctx.session().menu.id()+"."+buttonId);
        executor.execute(ctx,list).whenComplete((result,error)->threads.run(()->{
            ctx.session().busy=false;if(error!=null)report(ctx,"messages.action-failed");else if(!result.success())report(ctx,result.message());
            if(isCurrent(ctx)&&!ctx.session().closed)refresh(ctx.player());
        }));
    }
    private boolean unconditionalCanvasClose(MenuContext ctx,Node button){
        List<Node> actions=button.nodes("actions");
        return ctx.session().menu.renderer().equals("canvas")&&button.text("type","").equals("button")
                &&!button.has("enabled")&&!button.has("requirements")&&actions.size()==1
                &&actions.getFirst().values().equals(Map.of("type","close"));
    }
    public boolean isCurrent(MenuContext ctx){return sessions.get(ctx.player().getUniqueId())==ctx.session()&&!ctx.session().closed;}
    public void closeIfCurrent(MenuContext ctx,UUID token){if(isCurrent(ctx)&&ctx.session().token.equals(token))close(ctx.player());}
    public void close(Player player){MenuSession session=sessions.remove(player.getUniqueId());if(session==null)return;finish(player,session);replacing.add(player.getUniqueId());try{player.closeInventory();player.closeDialog();}finally{replacing.remove(player.getUniqueId());}}
    private void finish(Player player,MenuSession session){if(session.closed)return;executor.cancelAll(session.playerId);session.closed=true;inputs.cancel(session.playerId,false);session.invalidate();metrics.count("close",session.menu.id());Bukkit.getPluginManager().callEvent(new gg.fotia.futureui.api.event.MenuCloseEvent(player,session.menu.id()));if(!session.menu.onClose().isEmpty())executor.closing(new MenuContext(player,session),session.menu.onClose());}
    public void languageChanged(UUID id){threads.run(()->{Player player=Bukkit.getPlayer(id);MenuSession session=sessions.get(id);if(player==null||session==null)return;if(session.menu.form())session.languageDirty=true;else refresh(player);});}
    public void replace(ConfigurationSnapshot next,ShopService shops){this.packProfiles=gg.fotia.futureui.asset.PackProfiles.parse(next.assets().child("versions"));this.snapshot=next;this.shops=shops;conditions.rules(next.rules());if(entryBindings!=null)entryBindings.reload();values.placeholders().configure(next.settings().child("placeholders"));for(MenuSession session:new ArrayList<>(sessions.values())){Player player=Bukkit.getPlayer(session.playerId);if(player==null)continue;MenuDefinition definition=next.menus().get(session.menu.id());if(definition==null){close(player);continue;}if(session.menu.form()){session.languageDirty=true;continue;}session.menu=compatible(player,definition);if(session.menu==null){close(player);continue;}refresh(player);}}
    private void tick(){ticks++;hud.tick();if(ticks%20==0){storage.flush();if(expansion!=null)expansion.update();}long now=System.currentTimeMillis();for(MenuSession session:new ArrayList<>(sessions.values())){Player player=Bukkit.getPlayer(session.playerId);if(player==null)continue;if(now-session.lastInteraction>session.menu.timeoutSeconds()*1000L){close(player);continue;}int interval=session.menu.refreshTicks(),animation=AnimationFrames.interval(session.menu.components());if(animation>0)interval=interval==0?animation:Math.min(interval,animation);if(interval>0&&!session.menu.form()&&!session.busy&&ticks%Math.max(1,interval)==0)refresh(player);}}
    private void report(MenuContext ctx,String key){if(key!=null&&!key.isBlank()&&ctx.player().isOnline())ctx.player().sendMessage(text.message(ctx.player(),key,ctx.variables()));}
    @EventHandler public void join(PlayerJoinEvent event){state.load(event.getPlayer().getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent event){close(event.getPlayer());hud.hide(event.getPlayer());state.unload(event.getPlayer().getUniqueId());}
    @EventHandler public void locale(PlayerLocaleChangeEvent event){if(!text.translatorMode())Bukkit.getScheduler().runTask(plugin,()->languageChanged(event.getPlayer().getUniqueId()));}
    @EventHandler public void disabled(PluginDisableEvent event){actions.removeOwner(event.getPlugin());conditionExtensions.removeOwner(event.getPlugin());currencies.removeOwner(event.getPlugin());itemProviders.removeOwner(event.getPlugin());data.removeOwner(event.getPlugin());}
    @EventHandler public void inventoryClick(InventoryClickEvent event){
        if(event.getView().getTopInventory().getHolder() instanceof InventoryRenderer.Holder holder)inventoryInteractions.click(event,holder);
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof InventoryRenderer.Holder holder)inventoryInteractions.drag(event,holder);}
    @EventHandler public void inventoryClose(InventoryCloseEvent event){if(event.getInventory().getHolder() instanceof InventoryRenderer.Holder holder){InventoryRenderer.returnInputs(holder);if(!replacing.contains(holder.context.player().getUniqueId())&&isCurrent(holder.context)){sessions.remove(holder.context.player().getUniqueId());finish(holder.context.player(),holder.context.session());}}}
    public AutoCloseable registerAction(Plugin owner,String id,MenuAction action){return actions.register(owner,id,action);}
    public AutoCloseable registerCondition(Plugin owner,String id,MenuCondition condition){return conditionExtensions.register(owner,id,condition);}
    public AutoCloseable registerCurrency(Plugin owner,CurrencyProvider currency){return currencies.register(owner,currency.id(),currency);}
    public AutoCloseable registerItems(Plugin owner,String id,ItemProvider provider){return itemProviders.register(owner,id,provider);}
    public AutoCloseable registerData(Plugin owner,String id,MenuDataSource source){return data.register(owner,id,source);}
    public void close(){new ArrayList<>(sessions.keySet()).forEach(id->{Player player=Bukkit.getPlayer(id);if(player!=null)close(player);});hud.close();inputs.close();if(entryBindings!=null)entryBindings.close();if(expansion!=null)expansion.unregister();HandlerList.unregisterAll(this);}
}
