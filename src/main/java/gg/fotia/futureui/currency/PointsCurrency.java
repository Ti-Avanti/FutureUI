package gg.fotia.futureui.currency;

import gg.fotia.futureui.api.*;
import java.math.BigDecimal;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.entity.Player;

/** PlayerPoints 官方 API，拒绝非整数金额和溢出。 */
public final class PointsCurrency implements CurrencyProvider {
    public String id(){return "playerpoints";}public String label(){return "@currencies.playerpoints";}public int scale(){return 0;}
    public BigDecimal balance(Player player){return BigDecimal.valueOf(PlayerPoints.getInstance().getAPI().look(player.getUniqueId()));}
    public ActionResult withdraw(Player player,BigDecimal amount){return PlayerPoints.getInstance().getAPI().take(player.getUniqueId(),amount.intValueExact())?ActionResult.ok():ActionResult.fail("messages.insufficient-funds");}
    public ActionResult deposit(Player player,BigDecimal amount){return PlayerPoints.getInstance().getAPI().give(player.getUniqueId(),amount.intValueExact())?ActionResult.ok():ActionResult.fail("messages.refund-failed");}
}
