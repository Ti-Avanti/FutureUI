package gg.fotia.futureui.shop;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.condition.ConditionEngine;
import gg.fotia.futureui.config.Node;
import java.math.*;
import java.util.function.BiFunction;
import org.bukkit.inventory.ItemStack;

/** 在主线程预演余额、库存和扣款后的格子容量，不产生业务副作用。 */
final class PurchaseLimits {
    private final ExtensionRegistry<CurrencyProvider> currencies;
    private final ConditionEngine conditions;
    private final TransactionJournal journal;
    private final BiFunction<MenuContext,Node,ItemStack> items;
    private final int maximumItems;
    private final ShopPricing pricing;
    PurchaseLimits(ExtensionRegistry<CurrencyProvider> currencies,ConditionEngine conditions,TransactionJournal journal,BiFunction<MenuContext,Node,ItemStack> items,int maximumItems,ShopPricing pricing){if(maximumItems<1)throw new IllegalArgumentException("transactions.max-total-items must be positive");this.currencies=currencies;this.conditions=conditions;this.journal=journal;this.items=items;this.maximumItems=maximumItems;this.pricing=pricing;}
    ShopQuote quote(MenuContext ctx,String shop,ShopDefinition.Product p,int quantity){
        long count=(long)p.bundle()*quantity;BigDecimal total=p.price().multiply(BigDecimal.valueOf(quantity));CurrencyProvider currency=currencies.get(p.currency());ItemStack item=null;int maximum=0;String failure="";
        if(!p.quantity().accepts(quantity))failure="messages.quantity-invalid";
        if(currency==null||!currency.available())return new ShopQuote(p,quantity,count,total,0,"messages.currency-unavailable",null,currency);
        try{
            BigDecimal unit=pricing.unit(ctx,p,false,currency.scale());total=unit.multiply(BigDecimal.valueOf(quantity));
            if(!Double.isFinite(total.doubleValue())||total.signum()<0)throw new ArithmeticException();
            item=items.apply(ctx,p.item());if(item==null||item.getType().isAir())throw new IllegalArgumentException();
            long stock=p.stock()<0?Long.MAX_VALUE:Math.max(0,(long)p.stock()-journal.sold(shop+":"+p.id()));
            BigDecimal balance=currency.balance(ctx.player()).max(BigDecimal.ZERO);
            int cap=Math.min(p.maximum(),maximumItems/p.bundle());
            int daily=pricing.remaining(ctx,shop,p,false);cap=(int)Math.min(Math.min(cap,daily),stock);
            if(unit.signum()>0)cap=balance.divide(unit,0,RoundingMode.FLOOR).min(BigDecimal.valueOf(cap)).intValueExact();
            maximum=p.quantity().floor(cap);
            while(maximum>0){int next=p.quantity().floor(currency.maximumDelivery(ctx.player(),item,p.bundle(),unit,maximum));if(next==maximum)break;maximum=next;}
            if(!conditions.test(ctx,p.requirement())){maximum=0;failure="messages.requirement-failed";}
            else if(failure.isEmpty()){
                if(count>maximumItems)failure="messages.purchase-limit";
                else if(quantity>daily)failure="messages.quota-exceeded";
                else if(quantity>stock)failure="messages.out-of-stock";
                else if(total.compareTo(balance)>0)failure="messages.insufficient-funds";
                else {ItemStack[] paid=currency.previewWithdrawal(ctx.player(),total);if(paid==null||InventoryDelivery.simulate(paid,item,Math.toIntExact(count))==null)failure="messages.inventory-full";}
            }
        }catch(RuntimeException invalid){maximum=0;failure="messages.invalid-product";}
        return new ShopQuote(p,quantity,count,total,maximum,failure,item,currency);
    }
}
