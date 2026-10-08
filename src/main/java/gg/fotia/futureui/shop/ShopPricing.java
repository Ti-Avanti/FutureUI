package gg.fotia.futureui.shop;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.condition.*;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.state.DataStore;
import java.math.*;
import java.time.*;

/** 动态价格、折扣和每日额度的单一来源，报价与结算均重新检查。 */
public final class ShopPricing {
    private final ValueResolver values;private final ConditionEngine conditions;private final DataStore storage;
    public ShopPricing(ValueResolver values,ConditionEngine conditions,DataStore storage){this.values=values;this.conditions=conditions;this.storage=storage;}
    public BigDecimal unit(MenuContext ctx,ShopDefinition.Product product,boolean sell,int scale){
        Node config=product.options();BigDecimal price=new BigDecimal(values.resolve(ctx,config.text(sell?"sell-price":"price",sell?"-1":"0")));
        if(price.signum()<0)throw new IllegalArgumentException("Price cannot be negative");
        if(!sell)for(Node discount:config.nodes("discounts"))if(conditions.test(ctx,discount.condition("condition"))){BigDecimal multiplier=new BigDecimal(values.resolve(ctx,discount.text("multiplier","1"))),reduction=new BigDecimal(values.resolve(ctx,discount.text("subtract","0")));if(multiplier.signum()<0||reduction.signum()<0)throw new IllegalArgumentException("Discount cannot be negative");price=price.multiply(multiplier).subtract(reduction).max(BigDecimal.ZERO);if(!config.bool("stack-discounts",false))break;}
        if(!Double.isFinite(price.doubleValue()))throw new IllegalArgumentException("Price exceeds limit");return price.setScale(scale,RoundingMode.valueOf(config.text("rounding","UNNECESSARY").toUpperCase(java.util.Locale.ROOT)));
    }
    public String key(String shop,ShopDefinition.Product p,boolean sell){return "shop."+shop.replace('/','.')+"."+p.id()+"."+(sell?"sold":"bought");}
    public int remaining(MenuContext ctx,String shop,ShopDefinition.Product p,boolean sell){int limit=p.options().integer(sell?"daily-sell-limit":"daily-limit",-1);if(limit<0)return Integer.MAX_VALUE;return Math.max(0,limit-new BigDecimal(String.valueOf(storage.get(ctx.player().getUniqueId().toString(),key(shop,p,sell),0))).intValueExact());}
    public void record(MenuContext ctx,String shop,ShopDefinition.Product p,int quantity,boolean sell){storage.add(ctx.player().getUniqueId().toString(),key(shop,p,sell),BigDecimal.valueOf(quantity),ttl(p));}
    public long ttl(ShopDefinition.Product p){ZoneId zone=ZoneId.of(p.options().text("reset-timezone","UTC"));return Duration.between(Instant.now(),LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()).toMillis();}
}
