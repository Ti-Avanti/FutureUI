package gg.fotia.futureui.asset;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.zip.ZipFile;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

/** 后台验证实际分发 ZIP，完整版本资源通过后才接受客户端的加载回执。 */
public final class PackState implements Listener, AutoCloseable {
    public record Offer(UUID uuid, String sha1) {}
    private record Source(Path zip, PackManifest manifest, UUID configured) {}
    private record Verified(Source source, Offer offer) {}
    private final ConcurrentMap<UUID, Set<UUID>> loaded = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watcher = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("FutureUI-pack-state").factory());
    private volatile Source source;
    private final Logger logger;
    private final java.util.function.Consumer<UUID> changed;
    private volatile Verified verified;
    private volatile String problem = "Waiting for resource pack verification";
    private volatile Runnable onVerified = () -> {};
    private volatile boolean closed;
    private long lastModified = -1, lastSize = -1;
    private Source lastSource;

    public PackState(Path zip, PackManifest manifest, String configured, Logger logger, java.util.function.Consumer<UUID> changed) {
        this.source = new Source(zip, manifest, configured.isBlank() ? null : UUID.fromString(configured));
        this.logger = logger; this.changed = changed;
        watcher.scheduleWithFixedDelay(this::scan, 0, 2, TimeUnit.SECONDS);
    }

    private void scan() {
        if (closed) return;
        Source current = source;
        PackManifest target = current.manifest();
        Path zip = current.zip();
        UUID configured = current.configured();
        try {
            if (!Files.isRegularFile(zip)) { verified = null; problem = "Waiting for distributed ZIP: " + zip; return; }
            long modified = Files.getLastModifiedTime(zip).toMillis(), size = Files.size(zip);
            if (current == lastSource && modified == lastModified && size == lastSize && verified != null) return;
            Verified previous = verified;
            verified = null;
            try (ZipFile archive = new ZipFile(zip.toFile())) { PackVerifier.verify(archive, target); }
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            try (var stream = Files.newInputStream(zip)) {
                byte[] bytes = new byte[65536];
                for (int read; (read = stream.read(bytes)) != -1;) sha.update(bytes, 0, read);
            }
            if (closed || modified != Files.getLastModifiedTime(zip).toMillis() || size != Files.size(zip)
                    || current != source) return;
            String hash = HexFormat.of().formatHex(sha.digest());
            UUID id = configured == null ? UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8)) : configured;
            if (previous != null && !previous.offer().sha1().equals(hash))
                loaded.values().forEach(ids -> ids.remove(previous.offer().uuid()));
            verified = new Verified(current, new Offer(id, hash));
            lastModified = modified; lastSize = size; lastSource = current; problem = "";
            logger.info("资源包校验通过：" + target.profiles().profiles().size() + " 个版本覆盖层，格式 " + target.profiles().min() + "–" + target.profiles().max());
            loaded.forEach((player, ids) -> { if (ids.contains(id)) changed.accept(player); });
            onVerified.run();
        } catch (Exception error) {
            if (closed || current != source) return;
            String reason = String.valueOf(error.getMessage());
            if (!reason.equals(problem)) logger.warning("等待兼容资源包构建完成: " + reason);
            problem = reason;
        }
    }

    public void onVerified(Runnable listener) { onVerified = Objects.requireNonNull(listener); }
    public void update(PackManifest next, Path zip, String configured) {
        UUID fixed = configured.isBlank() ? null : UUID.fromString(configured);
        UUID previous = expected();
        Source old = source;
        if (fixed != null && fixed.equals(previous) && (!old.manifest().json().equals(next.json()) || !old.zip().equals(zip)))
            loaded.values().forEach(ids -> ids.remove(fixed));
        source = new Source(zip, next, fixed); verified = null;
        problem = "Waiting for resource pack verification";
    }
    public Path zip() { return source.zip(); }
    public boolean ready(UUID player) { UUID target = expected(); return target != null && loaded.getOrDefault(player, Set.of()).contains(target); }
    public Offer offer() { Verified result = verified; return result != null && result.source() == source ? result.offer() : null; }
    public UUID expected() { Offer offer = offer(); return offer == null ? null : offer.uuid(); }
    public String problem() { return problem; }
    public void receipt(UUID player, UUID pack, String status) {
        if (pack == null) return;
        if (status.equals("SUCCESSFULLY_LOADED")) {
            boolean firstReceipt = loaded.computeIfAbsent(player, id -> ConcurrentHashMap.newKeySet()).add(pack);
            if (firstReceipt && ready(player)) changed.accept(player);
        } else if (Set.of("DECLINED", "FAILED_DOWNLOAD", "FAILED_RELOAD", "INVALID_URL", "DISCARDED").contains(status))
            loaded.computeIfAbsent(player, id -> ConcurrentHashMap.newKeySet()).remove(pack);
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event) { receipt(event.getPlayer().getUniqueId(), event.getID(), event.getStatus().name()); }
    @EventHandler public void quit(PlayerQuitEvent event) { loaded.remove(event.getPlayer().getUniqueId()); }
    public void close() { closed = true; onVerified = () -> {}; watcher.shutdownNow(); HandlerList.unregisterAll(this); loaded.clear(); }
}
