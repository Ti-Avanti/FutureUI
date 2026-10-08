package gg.fotia.futureui.form;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import gg.fotia.futureui.placeholder.LiteralValue;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;

/** 输入流程保留原菜单会话；提交校验、重试、取消和超时共用同一状态机。 */
public final class InputCapture implements AutoCloseable {
    public static final class Request {
        public final MenuContext context;public final Node source;public final String mode;public final int bookSlot;
        public final CompletableFuture<ActionResult> result=new CompletableFuture<>();public final AtomicBoolean receiving=new AtomicBoolean();
        public volatile com.github.retrooper.packetevents.util.Vector3i signPosition;
        public UUID signWorld;
        public org.bukkit.inventory.Inventory anvilInventory;
        public String initial;public int attempts;public org.bukkit.scheduler.BukkitTask timeout;
        Request(MenuContext ctx,Node source,String mode,String initial){this.context=ctx;this.source=source;this.mode=mode;this.initial=initial;bookSlot=ctx.player().getInventory().getHeldItemSlot();}
    }
    private final MenuController menus;private final ConcurrentMap<UUID,Request> requests=new ConcurrentHashMap<>();private final NativeInputViews views;
    public InputCapture(MenuController menus){this.menus=menus;views=new NativeInputViews(menus,this);}
    public Request get(UUID id){return requests.get(id);}
    public CompletionStage<ActionResult> start(MenuContext ctx,Node source){
        cancel(ctx.player().getUniqueId(),false);Node options=menus.values.bind(ctx,source,Set.of("prompt","title","hint"));String mode=options.text("mode","dialog");
        if(!Set.of("dialog","chat","sign","anvil","book").contains(mode))throw new IllegalArgumentException("Unknown input mode "+mode);
        int seconds=options.integer("timeout-seconds",120);if(seconds<1||seconds>600)throw new IllegalArgumentException("Input timeout must be 1..600 seconds");
        int length=options.integer("max-length",128);if(length<1||length>32768)throw new IllegalArgumentException("Input length must be 1..32768");
        Request request=new Request(ctx,options,mode,menus.values.resolve(ctx,options.text("initial","")));requests.put(ctx.player().getUniqueId(),request);
        ctx.variables().put("input.cancelled",false);ctx.variables().put("input.timeout",false);
        request.timeout=Bukkit.getScheduler().runTaskLater(menus.plugin,()->finish(request,ActionResult.fail("messages.input-timeout"),true),seconds*20L);
        try{views.show(request,"");}catch(RuntimeException error){finish(request,ActionResult.fail("messages.action-failed"),false);throw error;}return request.result;
    }
    public void submit(Request request,String value){
        if(requests.get(request.context.player().getUniqueId())!=request)return;
        if(!menus.isCurrent(request.context)){finish(request,ActionResult.fail("messages.cancelled"),false);return;}
        if(value.equals(request.source.text("cancel-word","cancel"))){finish(request,ActionResult.fail("messages.input-cancelled"),false);return;}
        Node validation=request.source.child("validation");String error="";
        if(value.isBlank()&&validation.bool("required",true))error="messages.field-required";
        else if(value.length()>request.source.integer("max-length",128)||value.length()<validation.integer("min-length",0))error="messages.field-length";
        else if(validation.has("pattern")&&!com.google.re2j.Pattern.compile(validation.text("pattern","")).matcher(value).matches())error="messages.field-format";
        if(error.isEmpty()&&validation.has("number"))try{NumericRange.of(validation.child("number")).submitted(value,false);}catch(RuntimeException invalid){error="messages.field-range";}
        String key=request.source.text("key","input.value");
        if(error.isEmpty()&&!menus.conditions.test(request.context.scoped(Map.of(key,new LiteralValue(value))),validation.condition("condition")))error="messages.field-format";
        if(!error.isEmpty()){
            request.initial=value;request.receiving.set(false);if(++request.attempts>=request.source.integer("max-attempts",5)){finish(request,ActionResult.fail(error),false);return;}views.show(request,error);return;
        }
        request.context.variables().put(key,new LiteralValue(value));finish(request,ActionResult.ok(),false);
    }
    public void receive(Request request,String value){if(value.length()>32768||!request.receiving.compareAndSet(false,true))return;menus.threads.run(()->submit(request,value));}
    public void cancel(UUID player,boolean timeout){Request request=requests.get(player);if(request!=null)finish(request,ActionResult.fail(timeout?"messages.input-timeout":"messages.input-cancelled"),timeout);}
    private void finish(Request request,ActionResult result,boolean timeout){
        if(!requests.remove(request.context.player().getUniqueId(),request))return;if(request.timeout!=null)request.timeout.cancel();views.restore(request);
        request.context.variables().put("input.cancelled",!result.success()&&!timeout);request.context.variables().put("input.timeout",timeout);request.result.complete(result);
    }
    @Override public void close(){new ArrayList<>(requests.keySet()).forEach(id->cancel(id,false));views.close();}
}
