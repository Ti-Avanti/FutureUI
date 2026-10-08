package gg.fotia.futureui.action;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import gg.fotia.futureui.render.InventoryRenderer;
import gg.fotia.futureui.shop.TransactionJournal;
import gg.fotia.futureui.state.DataStore;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.inventory.*;

/** 同一主线程提交可逆操作；外部货币结果不确定时保留审计并锁定复核。 */
public final class TransactionActions {
    public static final Set<String> TYPES=Set.of("give-item","take-item","edit-item","repair-item","enchant-item","take-currency","give-currency","data-set","data-add","data-remove","data-toggle","quota","cooldown","require");
    private final MenuController menus;private final TransactionJournal journal;private final ItemActions items;private final DataActions data;
    private record DataUndo(String scope,String key,DataStore.Entry entry){}
    private record MoneyUndo(CurrencyProvider currency,BigDecimal amount,boolean deposit){}
    private record Outcome(ActionResult result,String status){}
    public TransactionActions(MenuController menus,TransactionJournal journal){this.menus=menus;this.journal=journal;items=new ItemActions(menus);data=new DataActions(menus);}
    public static void validate(Node node){
        if(node.nodes("actions").isEmpty())throw new IllegalArgumentException("Transaction requires actions");
        for(Node action:node.nodes("actions")){if(!TYPES.contains(action.text("type","")))throw new IllegalArgumentException("Non-reversible action in transaction: "+action.text("type",""));for(String key:List.of("on-success","on-failure","finally"))if(action.has(key))throw new IllegalArgumentException("Put transaction handlers on the transaction, not on a step");if(action.text("scope","").equals("session"))throw new IllegalArgumentException("Transactions persist player or global data; session writes belong outside the transaction");}
    }
    public CompletionStage<ActionResult> execute(MenuContext ctx,Node definition){
        validate(definition);UUID player=ctx.player().getUniqueId();if(journal.blocked(player)||!journal.tryLock(player))return StandardActions.done(ActionResult.fail("messages.transaction-pending"));
        UUID id=UUID.randomUUID();long revision=menus.snapshot().revision();Map<String,String> record=new LinkedHashMap<>();record.put("player",player.toString());record.put("shop","transaction");record.put("product",definition.text("id","configured"));record.put("quantity","0");record.put("operations",new com.google.gson.Gson().toJson(definition.plain()));
        for(Node stock:definition.nodes("stock"))record.put("stock:"+stock.text("key",""),stock.text("quantity","0"));
        CompletableFuture<ActionResult> result=new CompletableFuture<>();
        journal.write(id,record,"PREPARED").whenComplete((ignored,error)->menus.threads.run(()->{
            if(error!=null){journal.unlock(player);result.complete(ActionResult.fail("messages.storage-error"));return;}
            Outcome outcome;
            try{outcome=!ctx.player().isOnline()||ctx.session().closed||revision!=menus.snapshot().revision()?new Outcome(ActionResult.fail("messages.cancelled"),"CANCELLED"):settle(ctx,definition);}
            catch(Exception failure){menus.plugin.getLogger().severe("事务需核查 "+id+": "+failure.getMessage());outcome=new Outcome(ActionResult.fail("messages.transaction-pending"),"RECOVERY_REQUIRED");}
            Outcome settled=outcome;
            menus.storage.flush().thenCompose(saved->journal.write(id,record,settled.status())).whenComplete((saved,saveError)->menus.threads.run(()->{journal.unlock(player);ActionResult completed=saveError==null?settled.result():ActionResult.fail("messages.transaction-pending");org.bukkit.Bukkit.getPluginManager().callEvent(new gg.fotia.futureui.api.event.TransactionCompleteEvent(ctx,id,completed,saveError==null?settled.status():"RECOVERY_REQUIRED"));result.complete(completed);}));
        }));return result;
    }
    private Outcome settle(MenuContext ctx,Node definition){
        var event=new gg.fotia.futureui.api.event.TransactionPrepareEvent(ctx,definition);org.bukkit.Bukkit.getPluginManager().callEvent(event);if(event.isCancelled())return new Outcome(ActionResult.fail("messages.cancelled"),"CANCELLED");
        ActionResult quoted=menus.commerce.checkQuotes(ctx,definition.nodes("quotes"));if(!quoted.success())return new Outcome(quoted,"CANCELLED");
        ItemStack[] inventory=clone(ctx.player().getInventory().getContents());ItemStack cursor=copy(ctx.player().getItemOnCursor());Inventory top=ctx.player().getOpenInventory().getTopInventory();
        Map<Integer,ItemStack> inputs=new LinkedHashMap<>();if(top.getHolder() instanceof InventoryRenderer.Holder holder&&holder.context.session()==ctx.session())for(int slot:holder.inputSlots)inputs.put(slot,copy(top.getItem(slot)));
        List<DataUndo> dataUndos=new ArrayList<>();List<MoneyUndo> moneyUndos=new ArrayList<>();ActionResult failure=null;boolean uncertain=false;
        for(Node action:definition.nodes("actions")){
            Parameters p=new Parameters(ctx,action,menus.values);String type=action.text("type","");
            try{
                if(!menus.conditions.test(ctx,action.condition("condition"))){failure=ActionResult.fail("messages.requirement-failed");break;}
                ActionResult value;
                if(type.equals("require"))value=menus.conditions.test(ctx,action.condition("requirements"))?ActionResult.ok():ActionResult.fail("messages.requirement-failed");
                else if(type.endsWith("currency")){
                    CurrencyProvider currency=menus.currencies.get(p.string("currency","vault"));BigDecimal amount=p.decimal("amount","0");if(currency==null||!currency.available()){failure=ActionResult.fail("messages.currency-unavailable");break;}if(amount.signum()<0||!Double.isFinite(amount.doubleValue()))throw new IllegalArgumentException("Invalid currency amount");amount=amount.setScale(currency.scale(),java.math.RoundingMode.UNNECESSARY);
                    boolean take=type.equals("take-currency");try{value=take?currency.withdraw(ctx.player(),amount):currency.deposit(ctx.player(),amount);}catch(Exception error){uncertain=true;throw error;}
                    if(value.success()&&!(currency instanceof gg.fotia.futureui.currency.ItemCurrency))moneyUndos.add(new MoneyUndo(currency,amount,take));
                }else if(type.startsWith("data-")||type.equals("quota")||type.equals("cooldown")){
                    String scope=p.string("scope","player").equals("global")?"global":ctx.player().getUniqueId().toString();String key=(type.equals("cooldown")?"cooldown.":"")+p.string("key","value");dataUndos.add(new DataUndo(scope,key,menus.storage.entry(scope,key)));
                    if(type.equals("cooldown")){long ttl=Math.multiplyExact(Math.max(0,p.longValue("seconds",1)),1000);if(ttl==0)menus.storage.remove(scope,key);else menus.storage.set(scope,key,true,ttl);value=ActionResult.ok();}else value=data.run(ctx,action);
                }else value=items.run(ctx,action);
                if(!value.success()){failure=value;break;}
            }catch(Exception error){menus.plugin.getLogger().warning("事务步骤失败: "+error.getMessage());failure=ActionResult.fail("messages.action-failed");break;}
        }
        if(failure==null){for(Node stock:definition.nodes("stock"))journal.reserve(stock.text("key",""),stock.integer("quantity",0));ctx.variables().put("transaction.success",true);return new Outcome(ActionResult.ok(),"DELIVERED");}
        try{
            for(int i=moneyUndos.size()-1;i>=0;i--){MoneyUndo undo=moneyUndos.get(i);try{ActionResult compensated=undo.deposit()?undo.currency().deposit(ctx.player(),undo.amount()):undo.currency().withdraw(ctx.player(),undo.amount());if(!compensated.success())uncertain=true;}catch(Exception compensationError){uncertain=true;menus.plugin.getLogger().severe("货币回滚失败: "+compensationError.getMessage());}}
            ctx.player().getInventory().setContents(inventory);ctx.player().setItemOnCursor(cursor);inputs.forEach(top::setItem);
            for(int i=dataUndos.size()-1;i>=0;i--){DataUndo undo=dataUndos.get(i);menus.storage.restore(undo.scope(),undo.key(),undo.entry());}
        }catch(Exception error){uncertain=true;menus.plugin.getLogger().severe("事务回滚失败: "+error.getMessage());}
        ctx.variables().put("transaction.success",false);return new Outcome(uncertain?ActionResult.fail("messages.transaction-pending"):failure,uncertain?"RECOVERY_REQUIRED":"REFUNDED");
    }
    private static ItemStack copy(ItemStack item){return item==null?null:item.clone();}private static ItemStack[] clone(ItemStack[] values){return Arrays.stream(values).map(TransactionActions::copy).toArray(ItemStack[]::new);}
}
