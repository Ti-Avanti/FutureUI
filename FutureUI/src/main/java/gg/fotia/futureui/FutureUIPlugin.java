package gg.fotia.futureui;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.asset.*;
import gg.fotia.futureui.command.FutureUICommand;
import gg.fotia.futureui.config.*;
import gg.fotia.futureui.currency.*;
import gg.fotia.futureui.i18n.*;
import gg.fotia.futureui.integration.FotiaTranslatorBackend;
import gg.fotia.futureui.menu.MenuController;
import gg.fotia.futureui.shop.*;
import gg.fotia.futureui.state.*;
import gg.fotia.futureui.util.ThreadGate;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;

/** 插件装配与生命周期；业务逻辑位于独立模块。 */
public final class FutureUIPlugin extends JavaPlugin {
    private final ExecutorService io=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("FutureUI-config").factory());
    private final AtomicBoolean reloading=new AtomicBoolean();private final GlyphRegistry glyphs=new GlyphRegistry();
    private ConfigRepository repository;private ThreadGate threads;private MenuController menus;private PlayerStateStore state;private MetricsStore metrics;
    private TransactionJournal journal;private PackState packs;private long revision;
    private gg.fotia.futureui.integration.PackReceiptListener receipts;
    private PackDelivery delivery;private Path packArchive;
    private String assetHash="";
    private record Prepared(ConfigurationSnapshot configuration,LanguageCatalog language,ResourcePackCompiler.Result assets,PackDeliverySettings delivery,Path verificationZip){}
    @Override public void onEnable(){
        repository=new ConfigRepository(this);threads=new ThreadGate(this);FutureUICommand command=new FutureUICommand(this);Objects.requireNonNull(getCommand("futureui")).setExecutor(command);getCommand("futureui").setTabCompleter(command);
        reloadAll().whenComplete((ignored,error)->{if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"FutureUI 初始化失败",error);});
    }
    public MenuController menus(){return menus;}
    public PackState packs(){return packs;}
    public TransactionJournal journal(){return journal;}
    public ThreadGate threads(){return threads;}
    public String packBackend(){return delivery==null?"unavailable":delivery.settings().id();}
    public Path packArchive(){return packArchive;}
    public CompletionStage<Void> reloadAll(){return reloadAll(false);}
    private CompletionStage<Void> reloadAll(boolean forceBuild){
        if(!Bukkit.isPrimaryThread())return threads.call(()->reloadAll(forceBuild)).thenCompose(stage->stage);
        if(!reloading.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("Reload already running"));
        long next=++revision;Path ce=craftEngineRoot();
        CompletableFuture<Prepared> prepared=CompletableFuture.supplyAsync(()->{try{
            repository.installDefaults(ce);ConfigurationSnapshot config=repository.load(next);config.shops().forEach(ShopDefinition::parse);
            PackDeliverySettings target=PackDeliverySettings.parse(config.settings().child("resources"),repository.root(),ce);
            if(!target.external())CraftEnginePackPublisher.prepare(repository.root());
            Node language=config.settings().child("language");LanguageCatalog catalog=new LanguageCatalog(repository.root().resolve("languages"),language.text("default","zh_cn"),language.child("aliases"));
            ResourcePackCompiler.Result assets=new ResourcePackCompiler().compile(repository.root(),config.assets());
            Path verification=target.external()?target.externalZip():CraftEnginePackPublisher.publish(repository.root(),target.craftEngine(),assets);
            return new Prepared(config,catalog,assets,target,verification);
        }catch(Exception error){throw new CompletionException(error);}},io);
        return prepared.thenCompose(value->threads.<Void>call(()->{if(menus==null)initialize(value,forceBuild);else replace(value,forceBuild);return null;})).whenComplete((unused,error)->reloading.set(false));
    }
    private Path craftEngineRoot(){var craftEngine=Bukkit.getPluginManager().getPlugin("CraftEngine");return craftEngine==null||!craftEngine.isEnabled()?null:craftEngine.getDataFolder().toPath();}
    private void initialize(Prepared prepared,boolean forceBuild){
        assetHash=prepared.assets.hash();
        packArchive=prepared.assets.distributedZip();
        ConfigurationSnapshot config=prepared.configuration;glyphs.replace(prepared.assets.glyphs(),prepared.assets.viewports());gg.fotia.futureui.render.PixelCanvas.metrics(prepared.assets.metrics());
        boolean translator=config.settings().child("language").bool("fotiatranslator",true)&&Bukkit.getPluginManager().getPlugin("FotiaTranslator")!=null;
        TextService text=new TextService(prepared.language,translator,Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"));
        state=new PlayerStateStore(repository.root().resolve("players"),getLogger());metrics=new MetricsStore(repository.root().resolve("statistics.json"),config.settings().child("metrics").bool("enabled",true),getLogger());
        journal=new TransactionJournal(repository.root().resolve("transactions"));
        menus=new MenuController(this,threads,text,state,metrics,glyphs,config);currencies(config.settings());
        menus.transactions=new gg.fotia.futureui.action.TransactionActions(menus,journal);
        gg.fotia.futureui.integration.items.ItemIntegrations.register(menus);
        menus.shops=new ShopService(config.shops(),menus.currencies,menus.conditions,journal,threads,menus.items::create,getLogger(),config.settings().child("transactions").integer("max-total-items",65536),()->menus.shops,menus.values,menus.storage);
        journal.load().thenCombine(menus.storage.ready(),(a,b)->null).whenComplete((ignored,error)->threads.run(()->{if(error!=null){getLogger().log(java.util.logging.Level.SEVERE,"菜单或交易数据读取失败，禁止交易",error);return;}menus.start();Bukkit.getServicesManager().register(FutureUIService.class,menus,this,ServicePriority.Normal);getLogger().info("FutureUI 就绪，已加载 "+config.menus().size()+" 个菜单");}));
        if(translator)connectTranslator(0);
        packs=new PackState(prepared.verificationZip,prepared.assets.manifest(),prepared.delivery.expectedUuid(),getLogger(),menus::resourcesReady);Bukkit.getPluginManager().registerEvents(packs,this);
        delivery=new PackDelivery(this,menus,packs,prepared.delivery);
        receipts=new gg.fotia.futureui.integration.PackReceiptListener(packs);
        if(forceBuild||config.settings().child("resources").bool("build-on-start",true))delivery.build();
    }
    private void currencies(Node config){
        if(Bukkit.getPluginManager().isPluginEnabled("Vault"))menus.registerCurrency(this,new VaultCurrency());
        if(Bukkit.getPluginManager().isPluginEnabled("PlayerPoints"))menus.registerCurrency(this,new PointsCurrency());
        menus.registerCurrency(this,new ExperienceCurrency(false));menus.registerCurrency(this,new ExperienceCurrency(true));
        config.child("currencies").values().forEach((id,value)->{Node node=Node.of(value);if(!node.text("type","item").equals("item"))throw new IllegalArgumentException("Unknown currency type "+id);Material material=Material.matchMaterial(node.text("material",""));if(material==null||material.isAir())throw new IllegalArgumentException("Unknown currency material "+id);menus.registerCurrency(this,new ItemCurrency(id,node.text("label",id),new ItemStack(material)));});
    }
    private void replace(Prepared value,boolean forceBuild){
        menus.currencies.removeOwner(this);currencies(value.configuration.settings());
        gg.fotia.futureui.integration.items.ItemIntegrations.register(menus);
        boolean changed=!assetHash.equals(value.assets.hash())||!packs.zip().equals(value.verificationZip)||!delivery.settings().equals(value.delivery);
        // 先更新资源包状态，再刷新菜单；外部后端可能仍在等待上传或玩家下载。
        if(changed){assetHash=value.assets.hash();packs.update(value.assets.manifest(),value.verificationZip,value.delivery.expectedUuid());delivery.update(value.delivery);}
        ShopService shops=new ShopService(value.configuration.shops(),menus.currencies,menus.conditions,journal,threads,menus.items::create,getLogger(),value.configuration.settings().child("transactions").integer("max-total-items",65536),()->menus.shops,menus.values,menus.storage);
        glyphs.replace(value.assets.glyphs(),value.assets.viewports());gg.fotia.futureui.render.PixelCanvas.metrics(value.assets.metrics());menus.text.catalog(value.language);menus.replace(value.configuration,shops);
        if(menus.text.backend()!=null)menus.text.backend().reloadCatalog().whenComplete((ignored,error)->{if(error!=null)getLogger().warning("FotiaTranslator 词库重载失败: "+error.getMessage());});
        else if(menus.text.translatorMode())connectTranslator(0);
        packArchive=value.assets.distributedZip();
        if(changed||forceBuild)delivery.build();
    }
    public CompletionStage<ConfigurationSnapshot> validateConfiguration(){
        if(!Bukkit.isPrimaryThread())return threads.call(this::validateConfiguration).thenCompose(stage->stage);
        Path ce=craftEngineRoot();return CompletableFuture.supplyAsync(()->{try{var snapshot=repository.load(revision+1);PackDeliverySettings.parse(snapshot.settings().child("resources"),repository.root(),ce);return snapshot;}catch(Exception error){throw new CompletionException(error);}},io);
    }
    public CompletionStage<Void> reloadMenu(String id){
        if(menus==null)return CompletableFuture.failedFuture(new IllegalStateException("FutureUI is initializing"));
        if(!reloading.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("Reload already running"));
        return validateConfiguration().thenCompose(next->threads.<Void>call(()->{
            var definition=next.menus().get(id);if(definition==null)throw new IllegalArgumentException("Unknown menu "+id);
            var old=menus.snapshot();Map<String,gg.fotia.futureui.model.MenuDefinition> selected=new LinkedHashMap<>(old.menus());selected.put(id,definition);MenuValidator.validateLinks(selected);
            menus.replace(new ConfigurationSnapshot(++revision,old.settings(),selected,old.shops(),old.huds(),old.assets(),next.functions(),next.rules()),menus.shops);return null;
        })).whenComplete((ignored,error)->reloading.set(false));
    }
    private void connectTranslator(int attempt){
        if(!isEnabled()||attempt>60)return;
        try{
            if(!Bukkit.getPluginManager().isPluginEnabled("FotiaTranslator"))throw new IllegalStateException("Not enabled");
            TranslationBackend backend=new FotiaTranslatorBackend(repository.root().resolve("languages"),menus.text.catalog().fallback(),menus::languageChanged);menus.text.backend(backend);
            backend.reloadCatalog().whenComplete((ignored,error)->threads.run(()->{if(error!=null){getLogger().warning("FotiaTranslator 注册失败: "+error.getMessage());backend.close();menus.text.backend(null);}else Bukkit.getOnlinePlayers().forEach(p->menus.languageChanged(p.getUniqueId()));}));
        }catch(IllegalStateException error){Bukkit.getScheduler().runTaskLater(this,()->connectTranslator(attempt+1),20L);}
    }
    public CompletionStage<Void> buildPack(){return reloadAll(true);}
    @Override public void onDisable(){
        Bukkit.getServicesManager().unregisterAll(this);if(delivery!=null)delivery.close();if(receipts!=null)receipts.close();if(packs!=null)packs.close();
        if(menus!=null){menus.close();menus.storage.close();if(menus.text.backend()!=null)menus.text.backend().close();}if(state!=null)state.close();if(metrics!=null)metrics.close();if(journal!=null)journal.close();io.shutdown();
    }
}
