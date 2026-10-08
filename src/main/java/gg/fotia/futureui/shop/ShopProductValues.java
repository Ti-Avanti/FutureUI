package gg.fotia.futureui.shop;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.menu.MenuController;
import java.util.*;

/** 商品卡片与详情表单共享的展示值和报价来源。 */
public final class ShopProductValues {
    private ShopProductValues() {}
    public static String quantityKey(String shop,String product){return "shop.quantity."+shop+"."+product;}
    public static Map<String,Object> describe(MenuController menus,MenuContext ctx,ShopDefinition shop,ShopDefinition.Product p,int quantity){
        ShopQuote quote=menus.shops.quote(ctx,shop.id(),p.id(),quantity);var currency=quote.currency();Map<String,Object> row=new LinkedHashMap<>();
        row.put("id",p.id());row.put("shop",shop.id());row.put("item",p.item());row.put("name",menus.text.render(ctx.player(),ctx.session().locale,p.name(),ctx.variables()));row.put("icon",p.item().text("icon",""));
        row.put("price",quote.unitPrice().stripTrailingZeros().toPlainString());row.put("amount",p.bundle());row.put("minimum",p.quantity().min());row.put("maximum",p.maximum());row.put("initial",p.quantity().initial());row.put("step",p.quantity().step());row.put("decrement",-p.quantity().step());row.put("presets",p.quantity().presets());row.put("daily-remaining",menus.shops.pricing.remaining(ctx,shop.id(),p,false));
        row.put("quantity",quantity);row.put("quantity-key",quantityKey(shop.id(),p.id()));row.put("total",quote.total().stripTrailingZeros().toPlainString());row.put("total-items",quote.items());row.put("available-maximum",quote.availableMaximum());row.put("any-available",quote.availableMaximum()>0);row.put("available",quote.available());
        row.put("balance",currency==null||!currency.available()?"—":currency.balance(ctx.player()).stripTrailingZeros().toPlainString());row.put("currency",menus.text.render(ctx.player(),ctx.session().locale,currency==null?"@currencies."+p.currency():currency.label(),ctx.variables()));
        row.put("status",menus.text.render(ctx.player(),ctx.session().locale,quote.available()?"@menus.purchase.ready":"@"+quote.failure(),ctx.variables()));return row;
    }
}
