package gg.fotia.futureui.api;

import gg.fotia.futureui.config.Node;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** 数据源返回独立数据快照；异步线程不可访问 Bukkit 非线程安全 API。 */
@FunctionalInterface public interface MenuDataSource {
    CompletionStage<List<Node>> load(MenuContext context,Node options);
}
