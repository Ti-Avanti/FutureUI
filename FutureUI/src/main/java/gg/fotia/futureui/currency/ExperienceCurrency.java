package gg.fotia.futureui.currency;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.condition.ConditionEngine;
import java.math.BigDecimal;
import org.bukkit.entity.Player;

/** 经验点与经验等级使用独立 ID，避免混用单位。 */
public final class ExperienceCurrency implements CurrencyProvider {
    private final boolean levels;
    public ExperienceCurrency(boolean levels){this.levels=levels;}
    public String id(){return levels?"levels":"experience";}public String label(){return "@currencies."+id();}public int scale(){return 0;}
    public BigDecimal balance(Player player){return BigDecimal.valueOf(levels?player.getLevel():ConditionEngine.experience(player));}
    public ActionResult withdraw(Player player,BigDecimal amount){int points=amount.intValueExact();if(balance(player).compareTo(amount)<0)return ActionResult.fail("messages.insufficient-funds");if(levels)player.giveExpLevels(-points);else player.giveExp(-points);return ActionResult.ok();}
    public ActionResult deposit(Player player,BigDecimal amount){if(!player.isOnline())return ActionResult.fail("messages.refund-failed");if(levels)player.giveExpLevels(amount.intValueExact());else player.giveExp(amount.intValueExact());return ActionResult.ok();}
}
