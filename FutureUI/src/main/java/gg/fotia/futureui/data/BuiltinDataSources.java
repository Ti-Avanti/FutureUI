package gg.fotia.futureui.data;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;

/** 可组合的数据源；不决定菜单布局，读取 Bukkit 状态时由主线程调用。 */
public final class BuiltinDataSources {
    private final MenuController menus;
    public BuiltinDataSources(MenuController menus){this.menus=menus;}
    public void register() {
        menus.registerData(menus.plugin,"item-catalog",(ctx,node)->{List<Node> result=new ArrayList<>();String selected=menus.values.resolve(ctx,node.text("provider","all"));for(String id:menus.itemProviders.ids().stream().sorted().toList()){if(!selected.equals("all")&&!selected.equals(id))continue;for(Node item:menus.itemProviders.get(id).catalog())result.add(item.merge(Node.of(Map.of("provider",id,"item-type",item.text("item-type",""),"name",item.text("id","")))));}return CompletableFuture.completedFuture(List.copyOf(result));});
        menus.registerData(menus.plugin,"item-providers",(ctx,node)->CompletableFuture.completedFuture(menus.itemProviders.ids().stream().sorted().map(id->Node.of(Map.of("value",id,"label",id))).toList()));
        menus.data.register(menus.plugin,"static",(ctx,node)->CompletableFuture.completedFuture(node.nodes("entries")));
        menus.data.register(menus.plugin,"variable-list",(ctx,node)->{Object value=menus.values.value(ctx,node.text("key",""));if(!(value instanceof List<?> list))throw new IllegalArgumentException("Variable data source requires a list");return CompletableFuture.completedFuture(list.stream().map(entry->entry instanceof Node||entry instanceof Map<?,?>?Node.of(entry):Node.of(Map.of("value",entry))).toList());});
        menus.data.register(menus.plugin,"online-players",(ctx,node)->CompletableFuture.completedFuture(Bukkit.getOnlinePlayers().stream().sorted(Comparator.comparing(org.bukkit.entity.Player::getName)).map(p->Node.of(Map.of("name",p.getName(),"uuid",p.getUniqueId().toString(),"world",p.getWorld().getName(),"level",p.getLevel(),"ping",p.getPing()))).toList()));
        menus.data.register(menus.plugin,"currencies",(ctx,node)->CompletableFuture.completedFuture(currencies(ctx,node)));
        menus.data.register(menus.plugin,"condition-checks",(ctx,node)->CompletableFuture.completedFuture(node.nodes("checks").stream().map(check->{boolean passed=menus.conditions.test(ctx,check.condition("condition"));return check.merge(Node.of(Map.of("name",menus.text.render(ctx.player(),ctx.session().locale,check.text("name",""),ctx.variables()),"passed",passed,"status",menus.text.render(ctx.player(),ctx.session().locale,passed?"@common.ready":"@common.not-ready",ctx.variables()))));}).toList()));
        menus.data.register(menus.plugin,"metrics",(ctx,node)->CompletableFuture.completedFuture(ctx.player().hasPermission("futureui.admin")?menus.metrics.snapshot().entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).map(e->Node.of(Map.of("name",e.getKey(),"count",e.getValue()))).toList():List.of()));
        menus.data.register(menus.plugin,"extensions",(ctx,node)->CompletableFuture.completedFuture(ctx.player().hasPermission("futureui.admin")?List.of(extension(ctx,"currencies",menus.currencies.ids()),extension(ctx,"data",menus.data.ids()),extension(ctx,"actions",menus.actions.ids())):List.of()));
    }
    private Node extension(MenuContext ctx,String type,Set<String> ids){return Node.of(Map.of("name",menus.text.render(ctx.player(),ctx.session().locale,"@menus.admin."+type,ctx.variables()),"value",ids.isEmpty()?"—":String.join(", ",new TreeSet<>(ids))));}
    private List<Node> currencies(MenuContext ctx,Node node) {
        List<Node> result=new ArrayList<>();List<String> ids=node.strings("include");if(ids.isEmpty())ids=menus.currencies.ids().stream().sorted().toList();
        for(String id:ids){CurrencyProvider currency=menus.currencies.get(id);boolean available=currency!=null&&currency.available();Node presentation=node.child("presentation").child(id);
            result.add(Node.of(Map.of("id",id,"name",menus.text.render(ctx.player(),ctx.session().locale,currency==null?"@currencies."+id:currency.label(),ctx.variables()),"balance",available?currency.balance(ctx.player()).stripTrailingZeros().toPlainString():"—","available",available,"icon",presentation.text("icon","emerald"),"category",presentation.text("category","special"))));
        }return result;
    }
}
