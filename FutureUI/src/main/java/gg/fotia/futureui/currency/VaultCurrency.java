package gg.fotia.futureui.currency;

import gg.fotia.futureui.api.*;
import java.math.BigDecimal;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Vault 由实际经济插件提供，缺少服务时明确不可用。 */
public final class VaultCurrency implements CurrencyProvider {
    private Economy service(){var registration=Bukkit.getServicesManager().getRegistration(Economy.class);return registration==null?null:registration.getProvider();}
    public String id(){return "vault";}public String label(){return "@currencies.vault";}
    public boolean available(){return service()!=null;}
    public BigDecimal balance(Player player){Economy economy=service();return economy==null?BigDecimal.ZERO:BigDecimal.valueOf(economy.getBalance(player));}
    public ActionResult withdraw(Player player,BigDecimal amount){Economy economy=service();if(economy==null)return ActionResult.fail("messages.currency-unavailable");return economy.withdrawPlayer(player,amount.doubleValue()).transactionSuccess()?ActionResult.ok():ActionResult.fail("messages.insufficient-funds");}
    public ActionResult deposit(Player player,BigDecimal amount){Economy economy=service();return economy!=null&&economy.depositPlayer(player,amount.doubleValue()).transactionSuccess()?ActionResult.ok():ActionResult.fail("messages.refund-failed");}
}
