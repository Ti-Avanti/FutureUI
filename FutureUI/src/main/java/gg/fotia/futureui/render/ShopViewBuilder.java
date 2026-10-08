package gg.fotia.futureui.render;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import gg.fotia.futureui.shop.ShopCategorySelection;
import gg.fotia.futureui.shop.ShopDefinition;
import java.util.*;

/** 把商店数据展开为通用组件与动作；价格和商品均来自配置。 */
public final class ShopViewBuilder {
    private final MenuController menus;
    public ShopViewBuilder(MenuController menus){this.menus=menus;}
    public List<Node> build(MenuContext ctx,Node component){
        String shopId=component.text("shop","system");ShopDefinition shop=menus.shops.get(shopId);if(shop==null)return List.of(node("text",component.text("id","shop"),"text","@messages.invalid-product"));
        List<String> selected=ShopCategorySelection.resolve(shop,ctx.variables().get("category"));
        List<Node> result=new ArrayList<>();
        result.add(node("text","shop_welcome","text",component.text("heading","@menus.shop.welcome")));
        CurrencyProvider vault=menus.currencies.get("vault");ctx.variables().put("balance",vault==null||!vault.available()?"—":vault.balance(ctx.player()).toPlainString());
        result.add(node("text","shop_balance","text","@menus.shop.balance"));
        for(var category:shop.categories()){
            Map<String,Object> map=new LinkedHashMap<>();map.put("id","category_"+category.id());map.put("type","button");map.put("text",category.name());map.put("icon",category.icon());map.put("selected",selected.contains(category.id()));
            map.put("actions",List.of(Node.of(Map.of("type","set-variable","key","category","value",category.id())),Node.of(Map.of("type","set-variable","key","shop.search","value","")),Node.of(Map.of("type","refresh"))));result.add(Node.of(map));
        }
        String query=String.valueOf(ctx.variables().getOrDefault("shop.search","")).toLowerCase(Locale.ROOT);
        List<ShopDefinition.Product> products=new ArrayList<>(shop.products().values().stream().filter(p->selected.isEmpty()||selected.contains(p.category())).filter(p->query.isBlank()||menus.text.plain(ctx.player(),ctx.session().locale,p.name(),ctx.variables()).toLowerCase(Locale.ROOT).contains(query)||p.id().contains(query)).toList());
        if(String.valueOf(ctx.variables().getOrDefault("shop.sort",menus.state.get(ctx.player().getUniqueId(),"input.theme","default"))).equals("price"))products.sort(Comparator.comparing(ShopDefinition.Product::price));
        int pageSize=component.integer("page-size",9),pages=Math.max(1,(products.size()+pageSize-1)/pageSize);ctx.session().page=Math.min(ctx.session().page,pages-1);ctx.variables().put("pages",pages);
        for(var product:products.stream().skip((long)ctx.session().page*pageSize).limit(pageSize).toList()){
            Map<String,Object> map=new LinkedHashMap<>();map.put("id","product_"+product.id());map.put("type","button");map.put("text",product.name());map.put("icon",product.item().text("icon",""));map.put("suffix","  "+menus.shops.quote(ctx,shopId,product.id(),product.quantity().initial()).unitPrice().stripTrailingZeros().toPlainString());map.put("tooltip","@menus.shop.product-tooltip");
            map.put("context",Map.of("product",product.id(),"shop",shopId));map.put("actions",List.of(Node.of(Map.of("type","open-menu","menu",component.text("detail-menu","purchase")))));result.add(Node.of(map));
        }
        if(products.isEmpty())result.add(node("text","shop_empty","text","@menus.shop.empty"));
        result.add(Node.of(Map.of("type","button","id","shop_previous","text","@common.previous","actions",List.of(Node.of(Map.of("type","page","offset",-1))))));
        result.add(Node.of(Map.of("type","button","id","shop_search","text","@menus.shop.search","actions",List.of(Node.of(Map.of("type","open-menu","menu","search"))))));
        result.add(Node.of(Map.of("type","button","id","shop_next","text","@common.next","actions",List.of(Node.of(Map.of("type","page","offset",1))))));
        return result;
    }
    public void productContext(MenuContext ctx){
        String shopId=String.valueOf(ctx.variables().getOrDefault("shop","system")),productId=String.valueOf(ctx.variables().getOrDefault("product",""));ShopDefinition shop=menus.shops.get(shopId);var product=shop==null?null:shop.products().get(productId);if(product==null)return;
        int quantity=product.quantity().selected(ctx.variables().getOrDefault("input.quantity",product.quantity().initial()));
        gg.fotia.futureui.shop.ShopProductValues.describe(menus,ctx,shop,product,quantity).forEach((key,value)->ctx.variables().put("product."+key,value));
        if(ctx.session().menu.form())ctx.variables().putIfAbsent("input.quantity",quantity);else ctx.variables().put("input.quantity",quantity);

    }
    private static Node node(String type,String id,String key,Object value){return Node.of(Map.of("type",type,"id",id,key,value));}
}
