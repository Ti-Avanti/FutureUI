package gg.fotia.futureui.api;

import gg.fotia.futureui.config.Node;
import java.util.concurrent.CompletionStage;

/** 外部业务动作支持异步完成；FutureUI 负责回到主线程继续动作链。 */
@FunctionalInterface public interface MenuAction {
    CompletionStage<ActionResult> execute(MenuContext context,Node options);
}
