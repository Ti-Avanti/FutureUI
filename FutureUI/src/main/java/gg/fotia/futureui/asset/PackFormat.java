package gg.fotia.futureui.asset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.List;

/** 资源包格式是主、次版本二元组，97.1 不能按浮点数比较。 */
public record PackFormat(int major, int minor) implements Comparable<PackFormat> {
    public PackFormat {
        if (major < 18 || minor < 0) throw new IllegalArgumentException("Invalid overlay format: " + major + "." + minor);
    }

    public static PackFormat parse(Object value) {
        if (value instanceof List<?> parts && parts.size() == 2)
            return new PackFormat(Integer.parseInt(parts.get(0).toString()), Integer.parseInt(parts.get(1).toString()));
        throw new IllegalArgumentException("Resource format requires [major, minor]: " + value);
    }

    public static PackFormat json(JsonElement value, boolean upper) {
        if (value.isJsonArray()) {
            JsonArray parts = value.getAsJsonArray();
            return new PackFormat(parts.get(0).getAsInt(), parts.size() > 1 ? parts.get(1).getAsInt() : upper ? Integer.MAX_VALUE : 0);
        }
        return new PackFormat(value.getAsInt(), upper ? Integer.MAX_VALUE : 0);
    }

    public List<Integer> parts() { return List.of(major, minor); }
    public int compareTo(PackFormat other) {
        int result = Integer.compare(major, other.major);
        return result == 0 ? Integer.compare(minor, other.minor) : result;
    }
    public String toString() { return major + "." + minor; }
}
