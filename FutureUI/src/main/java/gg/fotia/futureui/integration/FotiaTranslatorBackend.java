package gg.fotia.futureui.integration;

import gg.fotia.futureui.i18n.TranslationBackend;
import gg.fotia.translator.api.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

/** 仅在依赖已启用时创建，直接使用公共 API，不使用反射。 */
public final class FotiaTranslatorBackend implements TranslationBackend {
    private final TranslationService service;
    private final CatalogSource source;
    private final AutoCloseable subscription;
    private volatile boolean registered;
    public FotiaTranslatorBackend(Path path,String fallback,Consumer<UUID> changed){
        service=FotiaTranslatorAPI.service().orElseThrow(()->new IllegalStateException("FotiaTranslator is not ready"));
        source=new CatalogSource("futureui",path,fallback,true,false);
        subscription=service.onLocaleChange(change->changed.accept(change.playerId()));
    }
    public String locale(UUID player,String fallback){return registered?service.locale(player):fallback;}
    public Optional<Component> render(UUID player,String locale,String template,Map<String,?> arguments){return registered?Optional.of(service.renderForLocale("futureui",locale,template,arguments)):Optional.empty();}
    public CompletionStage<Void> reloadCatalog(){return service.register(source).thenRun(()->registered=true);}
    public CompletionStage<Void> choose(UUID player,String language){return service.choose(player,language).thenApply(ignored->null);}
    public Set<String> locales(){return service.locales();}
    public boolean ready(){return registered;}
    public void close(){try{subscription.close();}catch(Exception error){throw new IllegalStateException(error);}finally{if(registered)service.unregister("futureui");registered=false;}}
}
