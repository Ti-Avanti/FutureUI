package gg.fotia.futureui.asset;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** 保留历史字形编号；基本私用区用满后使用补充私用区，避免主题共享 6400 字形上限。 */
final class GlyphAllocation extends LinkedHashMap<String, Integer> {
    private static final int FIRST = 0xe000, BMP_END = 0xf8ff;
    private static final int SUPPLEMENTARY_FIRST = 0xf0000, LAST = 0xffffd;
    private final BitSet used = new BitSet();
    private int next = FIRST;

    GlyphAllocation(Map<String, Integer> existing) {
        for (var entry : existing.entrySet()) {
            int code = entry.getValue();
            if (!valid(code) || used.get(code)) throw new IllegalArgumentException("Invalid or duplicate glyph allocation: " + entry.getKey());
            super.put(entry.getKey(), code); used.set(code);
        }
    }

    static int code(Map<String, Integer> allocation, String id) {
        if (allocation instanceof GlyphAllocation managed) return managed.allocate(id);
        // 保留公开编译器的 Map 参数接口；正常资源构建全程复用一个分配器。
        GlyphAllocation managed = new GlyphAllocation(allocation);
        int code = managed.allocate(id);
        allocation.putIfAbsent(id, code);
        return code;
    }

    private int allocate(String id) {
        Integer existing = get(id);
        if (existing != null) return existing;
        while (next <= LAST) {
            if (next > BMP_END && next < SUPPLEMENTARY_FIRST) next = SUPPLEMENTARY_FIRST;
            if (!used.get(next)) {
                int code = next++; used.set(code); super.put(id, code); return code;
            }
            next++;
        }
        throw new IllegalArgumentException("Glyph private-use ranges exhausted");
    }

    private static boolean valid(int code) {
        return code >= FIRST && code <= BMP_END || code >= SUPPLEMENTARY_FIRST && code <= LAST;
    }
}
