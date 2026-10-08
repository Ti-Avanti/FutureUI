package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.condition.ValueResolver;
import gg.fotia.futureui.config.Node;

/** 可由数据位置驱动，也可用会话时间插值；不接触奖励或点击业务。 */
final class ViewportMotion {
    private ViewportMotion() {}
    static double position(MenuContext context, Node node, ValueResolver values) {
        Node motion = node.child("motion");
        if (motion.empty()) return number(context, node, values, "position", 0);
        double duration = number(context, motion, values, "duration-ticks", 80);
        double delay = number(context, motion, values, "delay-ticks", 0);
        if (duration < 1 || duration > 12000 || delay < 0 || delay > 12000 || duration != Math.floor(duration) || delay != Math.floor(delay)) throw new IllegalArgumentException("Invalid viewport motion duration/delay");
        double elapsed = Math.max(0, (System.currentTimeMillis() - context.session().openedAt) / 50.0 - delay);
        double progress = elapsed / duration;
        progress = motion.bool("loop", false) ? progress % 1 : Math.min(1, progress);
        double eased = switch (values.resolve(context, motion.text("easing", "linear"))) {
            case "linear" -> progress;
            case "ease-in" -> progress * progress * progress;
            case "ease-out" -> 1 - Math.pow(1 - progress, 3);
            case "ease-in-out" -> progress < 0.5 ? 4 * progress * progress * progress : 1 - Math.pow(-2 * progress + 2, 3) / 2;
            default -> throw new IllegalArgumentException("Invalid viewport motion easing");
        };
        double from = number(context, motion, values, "from", 0), to = number(context, motion, values, "to", 1);
        if (Math.abs(from) > 65536 || Math.abs(to) > 65536) throw new IllegalArgumentException("Viewport motion endpoints exceed range");
        return from + (to - from) * eased;
    }
    private static double number(MenuContext context, Node node, ValueResolver values, String key, double fallback) {
        double value = Double.parseDouble(values.resolve(context, node.text(key, Double.toString(fallback))));
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Viewport motion requires finite numbers");
        return value;
    }
}
