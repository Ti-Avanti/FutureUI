package gg.fotia.futureui.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/** 使用独立的透明提示皮肤承载悬停图层，不覆盖原版其他物品的提示背景。 */
final class CanvasTooltip {
    private CanvasTooltip() {}
    static HoverEvent<?> create(Component content) {
        ItemStack carrier=new ItemStack(Material.PAPER);
        carrier.editMeta(meta->{meta.itemName(content);meta.setTooltipStyle(new NamespacedKey("futureui","canvas"));});
        return carrier.asHoverEvent();
    }
}
