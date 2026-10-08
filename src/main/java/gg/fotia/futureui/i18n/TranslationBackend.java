package gg.fotia.futureui.i18n;

import java.util.*;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;

/** 可选翻译插件边界，主类不引用外部共享 API。 */
public interface TranslationBackend extends AutoCloseable {
    String locale(UUID player,String fallback);
    Optional<Component> render(UUID player,String locale,String template,Map<String,?> arguments);
    CompletionStage<Void> reloadCatalog();
    CompletionStage<Void> choose(UUID player,String language);
    Set<String> locales();
    boolean ready();
    void close();
}
