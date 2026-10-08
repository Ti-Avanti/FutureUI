package gg.fotia.futureui.currency;

import gg.fotia.futureui.api.*;
import java.math.BigDecimal;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 按完整物品元数据比较，避免把同材质的自定义道具误当货币。 */
public final class ItemCurrency implements CurrencyProvider {
    private final String id,label;private final ItemStack unit;
    public ItemCurrency(String id,String label,ItemStack unit){this.id=id;this.label=label;this.unit=unit.clone();this.unit.setAmount(1);}
    public String id(){return id;}public String label(){return label;}public int scale(){return 0;}
    public BigDecimal balance(Player player){long count=0;for(ItemStack item:player.getInventory().getStorageContents())if(item!=null&&item.isSimilar(unit))count+=item.getAmount();return BigDecimal.valueOf(count);}
    public ItemStack[] previewWithdrawal(Player player,BigDecimal amount){int remaining=amount.intValueExact();if(remaining<0)return null;ItemStack[] items=CurrencyProvider.super.previewWithdrawal(player,amount);for(int i=0;i<items.length&&remaining>0;i++){ItemStack item=items[i];if(item==null||!item.isSimilar(unit))continue;int take=Math.min(item.getAmount(),remaining);remaining-=take;items[i]=item.getAmount()==take?null:item.asQuantity(item.getAmount()-take);}return remaining==0?items:null;}
    public int maximumDelivery(Player player,ItemStack item,int bundle,BigDecimal unitPrice,int upper){
        if(item.isSimilar(unit)){BigDecimal net=BigDecimal.valueOf(bundle).subtract(unitPrice);return net.signum()<=0?upper:Math.min(upper,BigDecimal.valueOf(gg.fotia.futureui.shop.InventoryDelivery.capacity(player.getInventory().getStorageContents(),item)).divide(net,0,java.math.RoundingMode.FLOOR).min(BigDecimal.valueOf(upper)).intValueExact());}
        // 每轮跳过必定放不下的份数区间；不同物品货币只在腾空格子时增加容量。
        int candidate=upper;while(candidate>0){ItemStack[] paid=previewWithdrawal(player,unitPrice.multiply(BigDecimal.valueOf(candidate)));if(paid==null)return 0;int capacity=(int)Math.min(candidate,gg.fotia.futureui.shop.InventoryDelivery.capacity(paid,item)/bundle);if(capacity==candidate)return candidate;candidate=capacity;}return 0;
    }
    public ActionResult withdraw(Player player,BigDecimal amount){ItemStack[] paid=previewWithdrawal(player,amount);if(paid==null)return ActionResult.fail("messages.insufficient-funds");player.getInventory().setStorageContents(paid);return ActionResult.ok();}
    public ActionResult deposit(Player player,BigDecimal amount){int remaining=amount.intValueExact();if(!player.isOnline())return ActionResult.fail("messages.refund-failed");
        ItemStack[] simulated=gg.fotia.futureui.shop.InventoryDelivery.simulate(player.getInventory().getStorageContents(),unit,remaining);if(simulated==null)return ActionResult.fail("messages.refund-failed");player.getInventory().setStorageContents(simulated);return ActionResult.ok();}
}
