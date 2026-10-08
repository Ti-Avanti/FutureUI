package gg.fotia.futureui.asset;

import gg.fotia.futureui.config.Node;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/** 资源生成与分发后端分离；这里只解析配置，不访问 Bukkit 或网络。 */
public record PackDeliverySettings(Backend backend, Path craftEngine, Path externalZip, String url,
        boolean sendOnJoin, boolean required, String prompt, String expectedUuid,
        String buildCommand, String resendCommand) {
    public enum Backend { CRAFTENGINE, EXTERNAL }

    public static PackDeliverySettings parse(Node resources, Path root, Path craftEngine) {
        String mode = resources.text("backend", "auto").toLowerCase(Locale.ROOT);
        Backend backend = switch (mode) {
            case "auto" -> craftEngine == null ? Backend.EXTERNAL : Backend.CRAFTENGINE;
            case "craftengine" -> Backend.CRAFTENGINE;
            case "external" -> Backend.EXTERNAL;
            default -> throw new IllegalArgumentException("resources.backend must be auto, craftengine or external");
        };
        if (backend == Backend.CRAFTENGINE && craftEngine == null)
            throw new IllegalArgumentException("resources.backend=craftengine requires an enabled CraftEngine; use auto or external without it");
        Node external = resources.child("external");
        String url = external.text("url", "").trim();
        if (backend == Backend.EXTERNAL && !url.isEmpty()) validateUrl(url);
        String uuid = backend == Backend.CRAFTENGINE ? resources.text("expected-pack-uuid", "").trim() : "";
        if (!uuid.isEmpty()) UUID.fromString(uuid);
        String zip = backend == Backend.EXTERNAL ? external.text("verify-pack-path", "").trim() : "";
        Path local = root.resolve(zip.isEmpty() ? ResourcePackArchive.FILE : zip).toAbsolutePath().normalize();
        boolean standalone = backend == Backend.EXTERNAL;
        return new PackDeliverySettings(backend, standalone ? null : craftEngine, local, standalone ? url : "",
                standalone && external.bool("send-on-join", true), standalone && external.bool("required", false),
                standalone ? external.text("prompt", "@messages.pack-prompt") : "", uuid,
                standalone ? "" : resources.text("build-command", "ce reload all"),
                standalone ? "" : resources.text("resend-command", "ce feature send-pack {player} --silent"));
    }

    private static void validateUrl(String value) {
        URI uri;
        try { uri = URI.create(value.replace("{sha1}", "0".repeat(40)).replace("{uuid}", new UUID(0, 0).toString())); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid resources.external.url", error); }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("resources.external.url must be an HTTP(S) download URL without user-info or fragment");
    }

    public boolean external() { return backend == Backend.EXTERNAL; }
    public String id() { return backend.name().toLowerCase(Locale.ROOT); }
    public String downloadUrl(PackState.Offer offer) {
        return url.replace("{sha1}", offer.sha1()).replace("{uuid}", offer.uuid().toString());
    }
}
