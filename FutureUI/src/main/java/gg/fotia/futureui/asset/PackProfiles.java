package gg.fotia.futureui.asset;

import gg.fotia.futureui.config.Node;
import java.util.*;

/** 由外置配置定义客户端协议和资源覆盖层；未知协议不会套用最近版本。 */
public record PackProfiles(List<Profile> profiles, String unsupportedClient, Node source) {
    public record Profile(String directory, String clients, Set<Integer> protocols,
                          PackFormat min, PackFormat max, Map<String, String> shaders) {
        public boolean includes(PackFormat version) { return min.compareTo(version) <= 0 && max.compareTo(version) >= 0; }
    }

    public PackProfiles { profiles = List.copyOf(profiles); }

    public static PackProfiles parse(Node source) {
        if (source.integer("config-version", 1) != 1) throw new IllegalArgumentException("assets/versions.yml: unsupported config-version");
        String fallback = source.text("unsupported-client", "native");
        if (!Set.of("native", "deny").contains(fallback)) throw new IllegalArgumentException("unsupported-client must be native or deny");
        List<Profile> profiles = new ArrayList<>();
        Set<Integer> protocols = new HashSet<>();
        for (var entry : source.child("profiles").values().entrySet()) {
            String directory = entry.getKey();
            if (!directory.matches("futureui_[a-z0-9_]+")) throw new IllegalArgumentException("Invalid overlay directory: " + directory);
            Node profile = Node.of(entry.getValue());
            PackFormat min = PackFormat.parse(profile.get("min-format")), max = PackFormat.parse(profile.get("max-format"));
            if (min.compareTo(max) > 0) throw new IllegalArgumentException("Reversed overlay range: " + directory);
            Set<Integer> clients = new LinkedHashSet<>();
            for (Object value : profile.list("protocols")) {
                int protocol = Integer.parseInt(value.toString());
                if (protocol < 771 || !protocols.add(protocol)) throw new IllegalArgumentException("Invalid or duplicate Dialog protocol: " + protocol);
                clients.add(protocol);
            }
            if (clients.isEmpty()) throw new IllegalArgumentException("Missing protocols: " + directory);
            Map<String, String> shaders = new LinkedHashMap<>();
            profile.child("shaders").values().forEach((name, file) -> {
                if (!name.matches("[a-z0-9_]+\\.(vsh|fsh)")) throw new IllegalArgumentException("Invalid core shader name: " + name);
                shaders.put(name, file.toString());
            });
            if (shaders.isEmpty()) throw new IllegalArgumentException("Missing shaders: " + directory);
            for (String name : shaders.keySet()) {
                String pair = name.substring(0, name.length() - 3) + (name.endsWith("vsh") ? "fsh" : "vsh");
                if (!shaders.containsKey(pair)) throw new IllegalArgumentException("Missing shader pair: " + pair);
            }
            for (Profile previous : profiles)
                if (min.compareTo(previous.max()) <= 0 && max.compareTo(previous.min()) >= 0)
                    throw new IllegalArgumentException("Overlapping overlays: " + previous.directory() + " and " + directory);
            profiles.add(new Profile(directory, profile.text("clients", directory), Set.copyOf(clients), min, max, Map.copyOf(shaders)));
        }
        if (profiles.isEmpty()) throw new IllegalArgumentException("assets/versions.yml: no profiles");
        profiles.sort(Comparator.comparing(Profile::min));
        return new PackProfiles(profiles, fallback, source);
    }

    public Profile forProtocol(int protocol) { return profiles.stream().filter(p -> p.protocols().contains(protocol)).findFirst().orElse(null); }
    public PackFormat min() { return profiles.getFirst().min(); }
    public PackFormat max() { return profiles.getLast().max(); }
    public Set<String> directories() { return profiles.stream().map(Profile::directory).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
}
