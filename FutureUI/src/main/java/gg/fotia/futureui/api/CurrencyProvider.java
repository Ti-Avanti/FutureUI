package gg.fotia.futureui.api;

import java.math.BigDecimal;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 同步适配器在主线程调用。实现不得执行磁盘/网络等待，异步货币请通过业务动作 API 接入。 */
public interface CurrencyProvider {
    String id();
    String label();
    BigDecimal balance(Player player);
    ActionResult withdraw(Player player,BigDecimal amount);
    ActionResult deposit(Player player,BigDecimal amount);
    default boolean available() { return true; }
    default int scale() { return 2; }
    /** 无副作用地预演扣款后的存储背包；会占用/释放物品格的适配器应覆盖此方法。 */
    default ItemStack[] previewWithdrawal(Player player,BigDecimal amount){return java.util.Arrays.stream(player.getInventory().getStorageContents()).map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);}
    /** 上界已满足余额等约束；返回能完整发货的最大份数。 */
    default int maximumDelivery(Player player,ItemStack item,int bundle,BigDecimal unitPrice,int upper){return (int)Math.min(upper,gg.fotia.futureui.shop.InventoryDelivery.capacity(player.getInventory().getStorageContents(),item)/bundle);}
}
