package gg.fotia.futureui.shop;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

/** 购物车和回收均生成通用可回滚事务，与单品购买共享价格及额度规则。 */
public final class ShopOperations {
    public record Line(String shop,String product,int quantity){}
    private final MenuController menus;
    public ShopOperations(MenuController menus){this.menus=menus;}
    public List<Line> lines(MenuContext ctx){Object raw=menus.storage.get(ctx.player().getUniqueId().toString(),"cart",List.of());if(!(raw instanceof List<?> list))return List.of();List<Line> result=new ArrayList<>();for(Object entry:list){String[] fields=entry.toString().split("\\|",-1);if(fields.length==3)result.add(new Line(fields[0],fields[1],Integer.parseInt(fields[2])));}return List.copyOf(result);}
    private void save(MenuContext ctx,List<Line> lines){menus.storage.set(ctx.player().getUniqueId().toString(),"cart",lines.stream().map(l->l.shop()+"|"+l.product()+"|"+l.quantity()).toList(),0);}
    public CompletionStage<ActionResult> run(MenuContext ctx,Node node){
        String type=node.text("type",""),shopId=resolve(ctx,node,"shop","system"),productId=resolve(ctx,node,"product","");
        if(type.equals("cart-clear")){save(ctx,List.of());return done(ActionResult.ok());}
        if(type.equals("cart-checkout"))return checkout(ctx);
        List<Line> cart=new ArrayList<>(lines(ctx));
        if(type.equals("cart-remove")){cart.removeIf(line->line.shop().equals(shopId)&&line.product().equals(productId));save(ctx,cart);return done(ActionResult.ok());}
        var shop=menus.shops.get(shopId);var product=shop==null?null:shop.products().get(productId);if(product==null)return done(ActionResult.fail("messages.invalid-product"));
        int quantity=new BigDecimal(resolve(ctx,node,"quantity","1")).intValueExact();
        if(type.equals("sell"))return sell(ctx,node,shopId,product,quantity);
        int previous=cart.stream().filter(l->l.shop().equals(shopId)&&l.product().equals(productId)).mapToInt(Line::quantity).sum();int next=Math.addExact(previous,quantity);
        if(quantity<1||!product.quantity().accepts(next))return done(ActionResult.fail("messages.quantity-invalid"));
        cart.removeIf(line->line.shop().equals(shopId)&&line.product().equals(productId));cart.add(new Line(shopId,productId,next));if(cart.size()>menus.snapshot().settings().child("transactions").integer("max-cart-lines",16))return done(ActionResult.fail("messages.cart-full"));save(ctx,cart);return done(ActionResult.ok());
    }
    public List<Node> rows(MenuContext ctx,boolean selling){
        List<Node> rows=new ArrayList<>();
        if(selling){String shopId=String.valueOf(ctx.variables().getOrDefault("shop","system"));var shop=menus.shops.get(shopId);if(shop==null)return List.of();for(var product:shop.products().values())if(product.options().has("sell-price")){
            var currency=menus.currencies.get(product.currency());if(currency==null||!currency.available())continue;
            BigDecimal price=menus.shops.pricing.unit(ctx,product,true,currency.scale());int count=menus.itemMatcher.count(ctx,sellMatch(product));Map<String,Object> row=new LinkedHashMap<>();row.put("shop",shopId);row.put("id",product.id());row.put("name",menus.text.render(ctx.player(),ctx.session().locale,product.name(),ctx.variables()));row.put("icon",product.item().text("icon","shop-emblem"));row.put("price",price.toPlainString());row.put("amount",product.bundle());row.put("owned",count);row.put("available",count>=product.bundle()&&menus.shops.pricing.remaining(ctx,shopId,product,true)>0);row.put("currency",menus.text.render(ctx.player(),ctx.session().locale,currency.label(),ctx.variables()));rows.add(Node.of(row));
        }return List.copyOf(rows);}
        Map<String,BigDecimal> totals=new LinkedHashMap<>();long items=0;
        for(Line line:lines(ctx)){var shop=menus.shops.get(line.shop());var p=shop==null?null:shop.products().get(line.product());if(p==null){Map<String,Object> missing=new LinkedHashMap<>();missing.put("shop",line.shop());missing.put("id",line.product());missing.put("name",menus.text.message(ctx.player(),"messages.invalid-product",Map.of()));missing.put("icon","shop-emblem");missing.put("quantity",line.quantity());missing.put("total-items",0);missing.put("total","—");missing.put("currency","");missing.put("available",false);rows.add(Node.of(missing));continue;}Map<String,Object> row=new LinkedHashMap<>(ShopProductValues.describe(menus,ctx,shop,p,line.quantity()));rows.add(Node.of(row));totals.merge(p.currency(),new BigDecimal(row.get("total").toString()),BigDecimal::add);items+=(long)p.bundle()*line.quantity();}
        ctx.variables().put("cart.lines",rows.size());ctx.variables().put("cart.items",items);List<String> cost=new ArrayList<>();totals.forEach((currency,amount)->{var provider=menus.currencies.get(currency);String label=provider==null?currency:menus.text.plain(ctx.player(),ctx.session().locale,provider.label(),ctx.variables());cost.add(amount.stripTrailingZeros().toPlainString()+" "+label);});ctx.variables().put("cart.total",String.join("\n",cost));return List.copyOf(rows);
    }
    private CompletionStage<ActionResult> checkout(MenuContext ctx){
        List<Line> cart=lines(ctx);if(cart.isEmpty())return done(ActionResult.fail("messages.cart-empty"));List<Node> actions=new ArrayList<>(),quotes=new ArrayList<>(),stock=new ArrayList<>();Map<String,BigDecimal> amounts=new LinkedHashMap<>();long count=0;
        for(Line line:cart){var shop=menus.shops.get(line.shop());var p=shop==null?null:shop.products().get(line.product());if(p==null)return done(ActionResult.fail("messages.invalid-product"));ShopQuote quote=menus.shops.quote(ctx,line.shop(),line.product(),line.quantity());if(!quote.available())return done(ActionResult.fail(quote.failure()));count=Math.addExact(count,quote.items());amounts.merge(p.currency(),quote.total(),BigDecimal::add);
            String item=Base64.getEncoder().encodeToString(quote.item().serializeAsBytes());quotes.add(Node.of(Map.of("shop",line.shop(),"product",line.product(),"quantity",line.quantity(),"price",quote.unitPrice(),"item",item)));
            stock.add(Node.of(Map.of("key",line.shop()+":"+line.product(),"quantity",line.quantity())));actions.add(Node.of(Map.of("type","give-item","amount",quote.items(),"item",Map.of("serialized",item))));actions.add(quota(line.shop(),p,line.quantity(),false));
        }
        if(count>menus.snapshot().settings().child("transactions").integer("max-total-items",65536))return done(ActionResult.fail("messages.purchase-limit"));
        List<Node> debit=new ArrayList<>();amounts.forEach((currency,amount)->debit.add(Node.of(Map.of("type","take-currency","currency",currency,"amount",amount))));debit.addAll(actions);debit.add(Node.of(Map.of("type","data-remove","key","cart")));
        ctx.variables().put("purchase.items",count);ctx.variables().put("purchase.quantity",cart.size());ctx.variables().put("purchase.total",ctx.variables().getOrDefault("cart.total",""));
        return menus.transactions.execute(ctx,Node.of(Map.of("id","cart","actions",debit,"quotes",quotes,"stock",stock)));
    }
    private CompletionStage<ActionResult> sell(MenuContext ctx,Node action,String shop,ShopDefinition.Product p,int quantity){
        if(!p.options().has("sell-price")||!p.quantity().accepts(quantity))return done(ActionResult.fail("messages.quantity-invalid"));var currency=menus.currencies.get(p.currency());if(currency==null||!currency.available())return done(ActionResult.fail("messages.currency-unavailable"));BigDecimal price=menus.shops.pricing.unit(ctx,p,true,currency.scale());
        if(action.has("quoted-price")&&price.compareTo(new BigDecimal(resolve(ctx,action,"quoted-price","0")))!=0)return done(ActionResult.fail("messages.price-changed"));
        int items=Math.multiplyExact(p.bundle(),quantity);if(items>menus.snapshot().settings().child("transactions").integer("max-total-items",65536))return done(ActionResult.fail("messages.purchase-limit"));
        List<Node> operations=List.of(Node.of(Map.of("type","require","requirements",p.requirement())),quota(shop,p,quantity,true),Node.of(Map.of("type","take-item","amount",items,"match",sellMatch(p))),Node.of(Map.of("type","give-currency","currency",p.currency(),"amount",price.multiply(BigDecimal.valueOf(quantity)))));
        ctx.variables().put("sale.items",items);ctx.variables().put("sale.total",price.multiply(BigDecimal.valueOf(quantity)));return menus.transactions.execute(ctx,Node.of(Map.of("id","sell-"+p.id(),"actions",operations,"quotes",List.of(Node.of(Map.of("shop",shop,"product",p.id(),"quantity",quantity,"price",price,"sell",true))))));
    }
    private Node quota(String shop,ShopDefinition.Product p,int quantity,boolean sell){return Node.of(Map.of("type","quota","key",menus.shops.pricing.key(shop,p,sell),"limit",p.options().integer(sell?"daily-sell-limit":"daily-limit",-1)<0?Integer.MAX_VALUE:p.options().integer(sell?"daily-sell-limit":"daily-limit",-1),"amount",quantity,"reset","daily","timezone",p.options().text("reset-timezone","UTC")));}
    private Node sellMatch(ShopDefinition.Product p){if(p.options().has("sell-match"))return p.options().child("sell-match");return p.item().merge(Node.of(Map.of("plain",p.item().text("provider","vanilla").equals("vanilla")&&p.item().values().keySet().stream().allMatch(k->Set.of("material","icon").contains(k)))));}
    public ActionResult checkQuotes(MenuContext ctx,List<Node> lines){for(Node line:lines){var shop=menus.shops.get(line.text("shop",""));var p=shop==null?null:shop.products().get(line.text("product",""));if(p==null)return ActionResult.fail("messages.invalid-product");boolean sell=line.bool("sell",false);var currency=menus.currencies.get(p.currency());if(currency==null||!currency.available())return ActionResult.fail("messages.currency-unavailable");BigDecimal price=menus.shops.pricing.unit(ctx,p,sell,currency.scale());if(price.compareTo(line.decimal("price","0"))!=0)return ActionResult.fail("messages.price-changed");if(!menus.conditions.test(ctx,p.requirement()))return ActionResult.fail("messages.requirement-failed");if(!sell){ShopQuote quote=menus.shops.quote(ctx,line.text("shop",""),p.id(),line.integer("quantity",1));if(!quote.available())return ActionResult.fail(quote.failure());if(line.has("item")&&!quote.item().isSimilar(org.bukkit.inventory.ItemStack.deserializeBytes(Base64.getDecoder().decode(line.text("item","")))))return ActionResult.fail("messages.quote-changed");}}return ActionResult.ok();}
    private String resolve(MenuContext ctx,Node node,String key,String fallback){return menus.values.resolve(ctx,node.text(key,fallback));}private static CompletionStage<ActionResult> done(ActionResult result){return CompletableFuture.completedFuture(result);}
}
