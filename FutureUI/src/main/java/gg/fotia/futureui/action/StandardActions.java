package gg.fotia.futureui.action;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;

/** 无控制流的业务动作；参数解析与反馈交给共享服务。 */
final class StandardActions {
    private final MenuController menus;private final DataActions data;final ItemActions items;
    StandardActions(MenuController menus){this.menus=menus;data=new DataActions(menus);items=new ItemActions(menus);}
    CompletionStage<ActionResult> run(MenuContext ctx,Node action){
        Parameters p=new Parameters(ctx,action,menus.values);String type=action.text("type","");
        switch(type){
            case "cart-add","cart-remove","cart-clear","cart-checkout","sell"->{return menus.commerce.run(ctx,action);}
            case "open-menu"->{Map<String,Object> parameters=new LinkedHashMap<>(ctx.variables());action.child("arguments").values().forEach((key,v)->parameters.put(key,menus.values.object(ctx,v)));return done(menus.navigate(ctx.player(),p.string("menu",""),parameters,true)?ActionResult.ok():ActionResult.fail("messages.menu-unavailable"));}
            case "back"->{Map<String,Object> values=new LinkedHashMap<>();action.child("values").values().forEach((key,v)->values.put(menus.values.resolve(ctx,key),menus.values.object(ctx,v)));menus.back(ctx.player(),values);return ok();}
            case "close"->{menus.close(ctx.player());return ok();}case "refresh"->{menus.refresh(ctx.player());return ok();}
            case "message"->{ctx.player().sendMessage(menus.text.render(ctx.player(),menus.text.locale(ctx.player()),action.text("text",""),ctx.variables(),action.child("parse")));return ok();}
            case "actionbar"->{ctx.player().sendActionBar(menus.text.render(ctx.player(),menus.text.locale(ctx.player()),action.text("text",""),ctx.variables(),action.child("parse")));return ok();}
            case "title"->{ctx.player().showTitle(Title.title(menus.text.render(ctx.player(),menus.text.locale(ctx.player()),action.text("title",""),ctx.variables(),action.child("parse")),menus.text.render(ctx.player(),menus.text.locale(ctx.player()),action.text("subtitle",""),ctx.variables(),action.child("parse")),Title.Times.times(java.time.Duration.ofMillis(p.longValue("fade-in-ticks",10)*50),java.time.Duration.ofMillis(p.longValue("stay-ticks",70)*50),java.time.Duration.ofMillis(p.longValue("fade-out-ticks",20)*50))));return ok();}
            case "sound"->{float preference;try{preference=Float.parseFloat(menus.state.get(ctx.player().getUniqueId(),"input.volume","60"))/100f;}catch(NumberFormatException error){preference=0.6f;}ctx.player().playSound(ctx.player().getLocation(),p.string("sound","minecraft:ui.button.click"),p.decimal("volume","0.5").floatValue()*Math.max(0,Math.min(1,preference)),p.decimal("pitch","1").floatValue());return ok();}
            case "player-command","console-command"->{String command=p.string("command","");if(command.contains("\n")||command.contains("\r")||command.length()>4096)return done(ActionResult.fail("messages.invalid-command"));return done(Bukkit.dispatchCommand(type.equals("console-command")?Bukkit.getConsoleSender():ctx.player(),command.replaceFirst("^/",""))?ActionResult.ok():ActionResult.fail("messages.command-failed"));}
            case "set-variable"->{String key=p.string("key","value");ctx.variables().put(key,p.object("value",""));clearError(ctx,key);return ok();}
            case "adjust-number"->{String key=p.string("key","value");BigDecimal min=p.decimal("min","0"),max=p.decimal("max","100"),initial=p.decimal("initial",min.toPlainString()),step=p.decimal("step","1");var range=new gg.fotia.futureui.form.NumericRange(min,max,step,initial,false);BigDecimal current;try{current=gg.fotia.futureui.form.NumericRange.parse(ctx.variables().getOrDefault(key,initial));}catch(RuntimeException invalid){current=initial;}ctx.variables().put(key,range.clamp(current.add(p.decimal("delta","1"))));clearError(ctx,key);return ok();}
            case "toggle"->{String key=p.string("key","enabled");if(!key.matches("[a-zA-Z0-9_.-]{1,80}"))return done(ActionResult.fail("messages.field-format"));boolean next=!Boolean.parseBoolean(String.valueOf(ctx.variables().getOrDefault(key,"false")));ctx.variables().put(key,next);if(p.bool("persist",false))menus.state.set(ctx.player().getUniqueId(),key,String.valueOf(next));return ok();}
            case "save-preference"->{String key=p.string("key",""),value=p.string("value","");if(!key.matches("[a-zA-Z0-9_.-]{1,80}")||value.length()>4096)return done(ActionResult.fail("messages.field-format"));menus.state.set(ctx.player().getUniqueId(),key,value);return ok();}
            case "cooldown"->{long seconds=Math.max(0,p.longValue("seconds",1));menus.storage.set(ctx.player().getUniqueId().toString(),"cooldown."+p.string("key","default"),true,Math.multiplyExact(seconds,1000));if(seconds==0)menus.storage.remove(ctx.player().getUniqueId().toString(),"cooldown."+p.string("key","default"));return ok();}
            case "delay"->{CompletableFuture<ActionResult> wait=new CompletableFuture<>();Bukkit.getScheduler().runTaskLater(menus.plugin,()->wait.complete(ActionResult.ok()),Math.max(1,Math.min(12000,p.integer("ticks",1))));return wait;}
            case "purchase"->{String shop=p.string("shop","system"),product=p.string("product","");var definition=menus.shops.get(shop);var item=definition==null?null:definition.products().get(product);if(item!=null)ctx.variables().put("product.name",menus.text.render(ctx.player(),ctx.session().locale,item.name(),ctx.variables()));return menus.shops.buy(ctx,shop,product,p.integer("quantity",1),action.has("quoted-price")?p.decimal("quoted-price","0"):null);}
            case "give-item","take-item","repair-item","edit-item","enchant-item","count-item"->{return done(items.run(ctx,action));}
            case "data-get","data-set","data-add","data-toggle","data-remove","quota","calculate","list","date"->{return done(data.run(ctx,action));}
            case "take-currency","give-currency"->{CurrencyProvider currency=menus.currencies.get(p.string("currency","vault"));if(currency==null||!currency.available())return done(ActionResult.fail("messages.currency-unavailable"));BigDecimal amount=p.decimal("amount","0");if(amount.signum()<0||!Double.isFinite(amount.doubleValue()))return done(ActionResult.fail("messages.invalid-price"));return done(type.equals("take-currency")?currency.withdraw(ctx.player(),amount):currency.deposit(ctx.player(),amount));}
            case "page"->{ctx.session().page=Math.max(0,action.has("absolute")?p.integer("absolute",0):ctx.session().page+p.integer("offset",1));menus.refresh(ctx.player());return ok();}
            case "language"->{if(menus.text.backend()==null||!menus.text.backend().ready())return done(ActionResult.fail("messages.translator-unavailable"));return menus.text.backend().choose(ctx.player().getUniqueId(),p.string("language","auto")).thenApply(ignored->ActionResult.ok());}
            case "hud"->{menus.hud.show(ctx,p.string("hud","status"));return ok();}case "hide-hud"->{menus.hud.hide(ctx.player());return ok();}
            case "open-url","copy-text"->{var component=menus.text.render(ctx.player(),ctx.session().locale,action.text("label","@messages.click-link"),ctx.variables(),action.child("parse"));ctx.player().sendMessage(component.clickEvent(type.equals("open-url")?ClickEvent.openUrl(p.string("url","")):ClickEvent.copyToClipboard(p.string("value",""))));return ok();}
            case "connect"->{try{var bytes=new java.io.ByteArrayOutputStream();var out=new java.io.DataOutputStream(bytes);out.writeUTF("Connect");out.writeUTF(p.string("server",""));ctx.player().sendPluginMessage(menus.plugin,"BungeeCord",bytes.toByteArray());return ok();}catch(java.io.IOException error){return done(ActionResult.fail("messages.action-failed"));}}
            case "fail"->{return done(ActionResult.fail(p.string("message","messages.action-failed")));}
            default->{MenuAction extension=menus.actions.get(type);if(extension==null)return done(ActionResult.fail("messages.unknown-action"));return extension.execute(ctx,action);}
        }
    }
    private static void clearError(MenuContext ctx,String key){if(key.startsWith("input."))ctx.session().errors.remove(key.substring(6));}
    static CompletionStage<ActionResult> ok(){return done(ActionResult.ok());}static CompletionStage<ActionResult> done(ActionResult result){return CompletableFuture.completedFuture(result);}
}
