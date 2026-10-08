package gg.fotia.futureui.shop;

import gg.fotia.futureui.api.CurrencyProvider;
import java.math.BigDecimal;
import org.bukkit.inventory.ItemStack;

/** 服务端生成的报价，独立于客户端提交值保存。 */
public record ShopQuote(ShopDefinition.Product product,int quantity,long items,BigDecimal total,int availableMaximum,String failure,ItemStack item,CurrencyProvider currency) {
    public ShopQuote {item=item==null?null:item.clone();}
    @Override public ItemStack item(){return item==null?null:item.clone();}
    public boolean available(){return failure.isEmpty();}
    public BigDecimal unitPrice(){return quantity>0?total.divide(BigDecimal.valueOf(quantity)):product.price();}
}
