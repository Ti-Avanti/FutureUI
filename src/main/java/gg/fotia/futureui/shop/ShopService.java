package gg.fotia.futureui.shop;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.condition.ConditionEngine;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.util.ThreadGate;
import java.math.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 主线程串行结算；扣款和发货之间不让出线程，失败执行退款并保留审计。 */
public final class ShopService {
    private final Map<String,ShopDefinition> shops;
    private final ExtensionRegistry<CurrencyProvider> currencies;
    private final ConditionEngine conditions;private final TransactionJournal journal;private final ThreadGate threads;
    private final BiFunction<MenuContext,Node,ItemStack> items;private final Logger logger;
    private final PurchaseLimits limits;
    public final ShopPricing pricing;private final gg.fotia.futureui.state.DataStore storage;
    private final Map<gg.fotia.futureui.model.MenuSession,Map<String,ShopQuote>> quotes=new WeakHashMap<>();
    private final java.util.function.Supplier<ShopService> current;
    public ShopService(Map<String,Node> source,ExtensionRegistry<CurrencyProvider> currencies,ConditionEngine conditions,TransactionJournal journal,ThreadGate threads,BiFunction<MenuContext,Node,ItemStack> items,Logger logger,int maximumItems,java.util.function.Supplier<ShopService> current,gg.fotia.futureui.condition.ValueResolver values,gg.fotia.futureui.state.DataStore storage){
        Map<String,ShopDefinition> parsed=new LinkedHashMap<>();source.forEach((id,node)->parsed.put(id,ShopDefinition.parse(id,node)));this.shops=Map.copyOf(parsed);
        this.currencies=currencies;this.conditions=conditions;this.journal=journal;this.threads=threads;this.items=items;this.logger=logger;
        pricing=new ShopPricing(values,conditions,storage);this.storage=storage;limits=new PurchaseLimits(currencies,conditions,journal,items,maximumItems,pricing);this.current=current;
    }
    public ShopDefinition get(String id){return shops.get(id);}
    public Collection<ShopDefinition> all(){return shops.values();}
    public ShopQuote quote(MenuContext ctx,String shopId,String productId,int quantity){ShopDefinition shop=shops.get(shopId);var product=shop==null?null:shop.products().get(productId);if(product==null)return null;ShopQuote quote=limits.quote(ctx,shopId,product,quantity);quotes.computeIfAbsent(ctx.session(),ignored->new HashMap<>()).put(shopId+":"+productId,quote);return quote;}
    public CompletionStage<ActionResult> buy(MenuContext context,String shopId,String productId,int quantity){
        return buy(context,shopId,productId,quantity,null);
    }
    public CompletionStage<ActionResult> buy(MenuContext context,String shopId,String productId,int quantity,BigDecimal quotedPrice){
        UUID playerId=context.player().getUniqueId();ShopDefinition shop=shops.get(shopId);ShopDefinition.Product product=shop==null?null:shop.products().get(productId);
        if(product==null)return done("messages.invalid-product");
        if(!product.quantity().accepts(quantity))return done("messages.quantity-invalid");
        ShopQuote shown=quotes.getOrDefault(context.session(),Map.of()).get(shopId+":"+productId);
        if(shown==null&&quotedPrice==null)shown=quote(context,shopId,productId,quantity);
        if(shown==null||shown.quantity()!=quantity||!shown.product().equals(product))return done("messages.quote-changed");
        final ShopQuote offer=shown;
        if(quotedPrice!=null&&quotedPrice.compareTo(offer.unitPrice())!=0)return done("messages.price-changed");
        if(journal.blocked(playerId))return done("messages.transaction-pending");
        if(!journal.tryLock(playerId))return done("messages.busy");
        CurrencyProvider currency=currencies.get(product.currency());
        if(currency==null||!currency.available()){journal.unlock(playerId);return done("messages.currency-unavailable");}
        BigDecimal price;
        try{price=offer.total().setScale(currency.scale(),RoundingMode.UNNECESSARY);if(!Double.isFinite(price.doubleValue())||price.signum()<0)throw new ArithmeticException();}
        catch(ArithmeticException error){journal.unlock(playerId);return done("messages.invalid-price");}
        UUID transaction=UUID.randomUUID();Map<String,String> record=Map.of("player",playerId.toString(),"shop",shopId,"product",productId,"quantity",String.valueOf(quantity),"currency",product.currency(),"amount",price.toPlainString(),"bundle",String.valueOf(product.bundle()),"items",String.valueOf((long)product.bundle()*quantity),"unit-price",offer.unitPrice().toPlainString());
        CompletableFuture<ActionResult> result=new CompletableFuture<>();
        journal.write(transaction,record,"PREPARED").whenComplete((ignored,error)->threads.run(()->{
            if(error!=null){journal.unlock(playerId);logger.severe("交易预写失败: "+error.getMessage());result.complete(ActionResult.fail("messages.storage-error"));return;}
            Outcome outcome;
            try{outcome=settle(context,shopId,product,quantity,currency,price,offer);}
            catch(Exception failure){logger.severe("交易结果需核查 "+transaction+": "+failure.getMessage());outcome=new Outcome(ActionResult.fail("messages.transaction-pending"),"RECOVERY_REQUIRED");}
            Outcome settled=outcome;
            storage.flush().thenCompose(saved->journal.write(transaction,record,settled.status)).whenComplete((saved,saveError)->threads.run(()->{
                journal.unlock(playerId);if(saveError!=null){logger.severe("交易结算记录失败 "+transaction+": "+saveError.getMessage());result.complete(ActionResult.fail("messages.transaction-pending"));}
                else{org.bukkit.Bukkit.getPluginManager().callEvent(new gg.fotia.futureui.api.event.TransactionCompleteEvent(context,transaction,settled.result,settled.status));result.complete(settled.result);}
            }));
        }));return result;
    }
    private Outcome settle(MenuContext context,String shopId,ShopDefinition.Product product,int quantity,CurrencyProvider currency,BigDecimal price,ShopQuote offer){
        Player player=context.player();String stockKey=shopId+":"+product.id();
        if(!player.isOnline()||context.session().closed)return cancelled("messages.cancelled");
        var event=new gg.fotia.futureui.api.event.TransactionPrepareEvent(context,Node.of(Map.of("type","purchase","shop",shopId,"product",product.id(),"quantity",quantity,"amount",price)));org.bukkit.Bukkit.getPluginManager().callEvent(event);if(event.isCancelled())return cancelled("messages.cancelled");
        if(current.get()!=this||currencies.get(product.currency())!=offer.currency())return cancelled("messages.quote-changed");
        ShopQuote checked=limits.quote(context,shopId,product,quantity);
        if(!checked.available())return cancelled(checked.failure());
        if(offer.item()==null||!checked.item().isSimilar(offer.item())||checked.total().compareTo(offer.total())!=0)return cancelled("messages.quote-changed");
        if(!conditions.test(context,product.requirement()))return cancelled("messages.requirement-failed");
        long available=product.stock()<0?-1:product.stock()-journal.sold(stockKey);if(available>=0&&available<quantity||product.stock()>=0&&available<0)return cancelled("messages.out-of-stock");
        ItemStack item;ItemStack[] delivered;
        try{item=checked.item();ItemStack[] paid=currency.previewWithdrawal(player,price);delivered=paid==null?null:InventoryDelivery.simulate(paid,item,Math.multiplyExact(product.bundle(),quantity));}
        catch(Exception error){return cancelled("messages.invalid-product");}
        if(delivered==null)return cancelled("messages.inventory-full");
        if(currency.balance(player).compareTo(price)<0)return cancelled("messages.insufficient-funds");
        ActionResult debit;
        try{debit=currency.withdraw(player,price);}catch(Exception error){logger.severe("货币扣款结果不确定: "+error.getMessage());return new Outcome(ActionResult.fail("messages.transaction-pending"),"RECOVERY_REQUIRED");}
        if(!debit.success())return new Outcome(debit,"CANCELLED");
        ItemStack[] afterDebit=Arrays.stream(player.getInventory().getStorageContents()).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
        try{
            // 物品货币扣除后必须重新计算，避免旧背包副本把已扣货币放回去。
            delivered=InventoryDelivery.simulate(player.getInventory().getStorageContents(),item,Math.multiplyExact(product.bundle(),quantity));
            if(delivered==null)throw new IllegalStateException("Inventory changed during withdrawal");
            player.getInventory().setStorageContents(delivered);pricing.record(context,shopId,product,quantity,false);journal.reserve(stockKey,quantity);
            context.variables().put("purchase.quantity",quantity);context.variables().put("purchase.items",Math.multiplyExact(product.bundle(),quantity));context.variables().put("purchase.total",price.toPlainString());context.variables().put("purchase.product",product.name());return new Outcome(ActionResult.ok(),"DELIVERED");
        }catch(Exception error){
            try{player.getInventory().setStorageContents(afterDebit);}catch(Exception restoreError){return new Outcome(ActionResult.fail("messages.transaction-pending"),"RECOVERY_REQUIRED");}
            try{ActionResult refund=currency.deposit(player,price);return new Outcome(ActionResult.fail(refund.success()?"messages.delivery-failed":"messages.refund-failed"),refund.success()?"REFUNDED":"RECOVERY_REQUIRED");}
            catch(Exception refundError){logger.severe("退款失败: "+refundError.getMessage());return new Outcome(ActionResult.fail("messages.refund-failed"),"RECOVERY_REQUIRED");}
        }
    }
    private static CompletionStage<ActionResult> done(String key){return CompletableFuture.completedFuture(ActionResult.fail(key));}
    private static Outcome cancelled(String key){return new Outcome(ActionResult.fail(key),"CANCELLED");}
    private record Outcome(ActionResult result,String status){}
}
