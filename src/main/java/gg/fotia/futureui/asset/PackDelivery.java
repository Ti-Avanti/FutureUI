package gg.fotia.futureui.asset;

import gg.fotia.futureui.FutureUIPlugin;
import gg.fotia.futureui.menu.MenuController;
import java.util.*;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/** 主线程分发入口；下载地址模式只管理自己发送的资源包，不清空其它插件的包。 */
public final class PackDelivery implements Listener, AutoCloseable {
    private final FutureUIPlugin plugin;
    private final MenuController menus;
    private final PackState packs;
    private final Set<UUID> pending = new LinkedHashSet<>();
    private final Map<UUID, UUID> sent = new HashMap<>();
    private PackDeliverySettings settings;
    private BukkitTask buildTask;
    private boolean closed;

    public PackDelivery(FutureUIPlugin plugin, MenuController menus, PackState packs, PackDeliverySettings settings) {
        this.plugin = plugin; this.menus = menus; this.packs = packs; this.settings = settings;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        packs.onVerified(() -> plugin.threads().run(this::available));
        announce();
        if (packs.offer() != null) available();
    }

    public PackDeliverySettings settings() { return settings; }

    public void update(PackDeliverySettings next) {
        if (buildTask != null) { buildTask.cancel(); buildTask = null; }
        if (settings.external() && !next.external()) {
            sent.forEach((id, pack) -> { Player player = Bukkit.getPlayer(id); if (player != null) player.removeResourcePack(pack); });
            sent.clear();
        }
        settings = next;
        announce();
    }

    private void announce() {
        if (settings.external() && settings.url().isBlank())
            plugin.getLogger().warning(menus.text.plain(null, menus.text.catalog().fallback(),
                    "@messages.external-pack-unconfigured", Map.of("path", plugin.getDataFolder().toPath().resolve(ResourcePackArchive.FILE).toString())));
    }

    public void request(Player player) {
        if (closed || !player.isOnline()) return;
        if (!settings.external()) {
            if (!settings.resendCommand().isBlank())
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), settings.resendCommand().replace("{player}", player.getName()));
            return;
        }
        if (settings.url().isBlank()) {
            player.sendMessage(menus.text.message(player, "messages.pack-download-unconfigured", Map.of()));
            return;
        }
        pending.add(player.getUniqueId());
        sendPending();
    }

    public void build() {
        if (closed) return;
        if (settings.external()) { available(); return; }
        if (buildTask != null) buildTask.cancel();
        if (!settings.buildCommand().isBlank()) {
            PackDeliverySettings requested = settings;
            buildTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                buildTask = null;
                if (!closed && settings.equals(requested)) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), requested.buildCommand());
            }, 20L);
        }
    }

    private void available() {
        if (closed) return;
        if (!settings.external()) {
            for (UUID id : List.copyOf(pending)) {
                pending.remove(id); Player player = Bukkit.getPlayer(id);
                if (player != null) request(player);
            }
            return;
        }
        if (settings.url().isBlank()) return;
        if (settings.sendOnJoin()) for (Player player : Bukkit.getOnlinePlayers())
            if (!menus.packProfile(player).equals("native")) pending.add(player.getUniqueId());
        sendPending();
    }

    private void sendPending() {
        PackState.Offer offer = packs.offer();
        if (offer == null || !settings.external()) return;
        for (UUID id : List.copyOf(pending)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || packs.ready(id)) { pending.remove(id); continue; }
            if (!offer.equals(packs.offer())) return;
            try {
                String prompt = LegacyComponentSerializer.legacySection().serialize(
                        menus.text.render(player, menus.text.locale(player), settings.prompt(), Map.of()));
                UUID previous = sent.get(id);
                if (previous != null && !previous.equals(offer.uuid())) player.removeResourcePack(previous);
                player.addResourcePack(offer.uuid(), settings.downloadUrl(offer), HexFormat.of().parseHex(offer.sha1()), prompt, settings.required());
                sent.put(id, offer.uuid());
            } catch (RuntimeException error) {
                player.sendMessage(menus.text.message(player, "messages.pack-send-failed", Map.of()));
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Resource pack request failed", error);
            }
            pending.remove(id);
        }
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        if (settings.external() && settings.sendOnJoin() && !settings.url().isBlank()
                && !menus.packProfile(event.getPlayer()).equals("native")) request(event.getPlayer());
    }
    @EventHandler public void quit(PlayerQuitEvent event) { pending.remove(event.getPlayer().getUniqueId()); sent.remove(event.getPlayer().getUniqueId()); }
    public void close() {
        closed = true; packs.onVerified(() -> {});
        if (buildTask != null) buildTask.cancel();
        HandlerList.unregisterAll(this); pending.clear(); sent.clear();
    }
}
