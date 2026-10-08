package gg.fotia.futureui.model;

import gg.fotia.futureui.config.Node;
import java.util.List;

/** 一个配置版本内的完整页面定义。 */
public record MenuDefinition(String id, String title, String renderer, Node layout,
        List<Node> components, Node requirements, List<Node> onOpen, List<Node> onClose,
        int refreshTicks, int timeoutSeconds, String permission, Node source) {
    public MenuDefinition { components=List.copyOf(components); onOpen=List.copyOf(onOpen); onClose=List.copyOf(onClose); }
    public boolean form() { return components.stream().anyMatch(MenuDefinition::input); }
    /** 自动刷新页面的旧回调尽快释放；输入表单保留整个会话的提交时间。 */
    public int callbackLifetimeSeconds() { return !form()&&refreshTicks>0&&refreshTicks%20==0?Math.min(timeoutSeconds,refreshTicks/20+10):timeoutSeconds; }
    public static boolean input(Node node) { return List.of("text-input","number-input","slider","checkbox","select","multi-select").contains(node.text("type","")) || node.nodes("children").stream().anyMatch(MenuDefinition::input) || node.nodes("after-items").stream().anyMatch(MenuDefinition::input) || node.text("type","").equals("list")&&input(node.child("item")); }
}
