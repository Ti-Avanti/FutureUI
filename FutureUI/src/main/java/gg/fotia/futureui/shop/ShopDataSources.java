package gg.fotia.futureui.shop;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 仅输出商品数据；不决定卡片、导航、图片或按钮如何排版。 */
public final class ShopDataSources {
    private final MenuController menus;
    public ShopDataSources(MenuController menus){this.menus=menus;}
    public void register(){
        menus.data.register(menus.plugin,"shop-categories",(ctx,node)->CompletableFuture.completedFuture(categories(ctx,node)));
        menus.data.register(menus.plugin,"shop-products",(ctx,node)->CompletableFuture.completedFuture(products(ctx,node)));
    }
    private ShopDefinition shop(Node node){return menus.shops.get(node.text("shop","system"));}
    private List<Node> categories(MenuContext ctx,Node node){
        ShopDefinition shop=shop(node);if(shop==null)return List.of();
        List<String> selected=ShopCategorySelection.resolve(shop,ctx.variables().get("category"));
        return shop.categories().stream().map(c->Node.of(Map.of("id",c.id(),"name",menus.text.render(ctx.player(),ctx.session().locale,c.name(),ctx.variables()),"selected",selected.contains(c.id()),"selected-categories",selected,"icon",c.icon()))).toList();
    }
    private List<Node> products(MenuContext ctx,Node node){
        CurrencyProvider balance=menus.currencies.get(node.text("balance-currency","vault"));ctx.session().variables.put("shop.balance",balance==null||!balance.available()?"—":balance.balance(ctx.player()).stripTrailingZeros().toPlainString());
        ShopDefinition shop=shop(node);if(shop==null)return List.of();
        List<String> selected=ShopCategorySelection.resolve(shop,ctx.variables().get("category"));String query=String.valueOf(ctx.variables().getOrDefault("shop.search","")).toLowerCase(Locale.ROOT);
        List<ShopDefinition.Product> products=new ArrayList<>(shop.products().values().stream().filter(p->selected.isEmpty()||selected.contains(p.category())).filter(p->query.isBlank()||p.id().contains(query)||menus.text.plain(ctx.player(),ctx.session().locale,p.name(),ctx.variables()).toLowerCase(Locale.ROOT).contains(query)).toList());
        if(String.valueOf(ctx.variables().getOrDefault("shop.sort",menus.state.get(ctx.player().getUniqueId(),"input.theme","default"))).equals("price"))products.sort(Comparator.comparing(ShopDefinition.Product::price));
        List<Node> result=new ArrayList<>();for(var p:products){
            String key=ShopProductValues.quantityKey(shop.id(),p.id());
            int quantity=p.quantity().selected(ctx.variables().getOrDefault(key,p.quantity().initial()));
            ctx.session().variables.put(key,quantity);
            Map<String,Object> row=ShopProductValues.describe(menus,ctx,shop,p,quantity);
            result.add(Node.of(row));
        }return result;
    }
}
