package gg.fotia.futureui.action;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 持久数据、日额度、列表和时间运算；不依赖菜单渲染器。 */
final class DataActions {
    private final MenuController menus;
    DataActions(MenuController menus){this.menus=menus;}
    ActionResult run(MenuContext ctx,Node node){
        Parameters p=new Parameters(ctx,node,menus.values);String type=node.text("type",""),key=p.string("key","value"),scope=p.string("scope","player");
        if(type.equals("calculate")){ctx.variables().put(key,gg.fotia.futureui.condition.Arithmetic.evaluate(p.string("expression","0")));return ActionResult.ok();}
        if(type.equals("date")){ZonedDateTime date=Instant.ofEpochMilli(p.longValue("timestamp",System.currentTimeMillis())).atZone(ZoneId.of(p.string("timezone","UTC")));ctx.variables().put(key,date.plusSeconds(p.longValue("offset-seconds",0)).format(DateTimeFormatter.ofPattern(p.string("format","yyyy-MM-dd"))));return ActionResult.ok();}
        if(type.equals("list"))return list(ctx,node,p,key);
        String owner=scope.equals("global")?"global":ctx.player().getUniqueId().toString();long ttl=ttl(p);
        Object old=scope.equals("session")?ctx.variables().get(key):menus.storage.get(owner,key,null);
        Object value=switch(type){
            case "data-get"->old==null?p.object("default",""):old;
            case "data-set"->p.object("value","");case "data-add"->new BigDecimal(old==null?"0":old.toString()).add(p.decimal("amount","1"));
            case "data-toggle"->!Boolean.parseBoolean(String.valueOf(old));case "data-remove"->null;
            case "quota"->{BigDecimal used=new BigDecimal(old==null?"0":old.toString()),amount=p.decimal("amount","1"),limit=p.decimal("limit","1");if(amount.signum()<0||used.add(amount).compareTo(limit)>0)yield QuotaDenied.INSTANCE;yield used.add(amount);}
            default->throw new IllegalArgumentException("Unknown data action "+type);
        };
        if(value==QuotaDenied.INSTANCE)return ActionResult.fail("messages.quota-exceeded");
        if(type.equals("data-get"))ctx.variables().put(p.string("output",key),value);
        else if(scope.equals("session")){if(value==null)ctx.variables().remove(key);else ctx.variables().put(key,value);}
        else if(value==null)menus.storage.remove(owner,key);else menus.storage.set(owner,key,value,ttl);
        return ActionResult.ok();
    }
    private ActionResult list(MenuContext ctx,Node node,Parameters p,String key){
        Object raw=p.object("value",ctx.variables().getOrDefault(key,List.of()));List<Object> list=new ArrayList<>(raw instanceof List<?> l?l:List.of());String operation=p.string("operation","set");Object result;
        switch(operation){
            case "set"->result=list;case "append"->{list.add(p.object("item",""));result=list;}case "remove"->{Object item=p.object("item","");list.removeIf(v->String.valueOf(v).equals(String.valueOf(item)));result=list;}
            case "unique"->result=new ArrayList<>(new LinkedHashSet<>(list));case "count"->result=list.size();case "clear"->result=List.of();
            case "join"->result=String.join(p.string("separator",", "),list.stream().map(String::valueOf).toList());
            case "split"->result=List.of(String.valueOf(raw).split(java.util.regex.Pattern.quote(p.string("separator",",")),-1));
            case "get"->{int index=p.integer("index",0);result=index<0||index>=list.size()?p.object("default",""):list.get(index);}
            default->throw new IllegalArgumentException("Unknown list operation "+operation);
        }
        if(result instanceof List<?> l&&l.size()>256)throw new IllegalArgumentException("List exceeds 256 entries");ctx.variables().put(key,result);return ActionResult.ok();
    }
    static long ttl(Parameters p){if(p.string("reset","").equals("daily")){ZoneId zone=ZoneId.of(p.string("timezone","UTC"));return Duration.between(Instant.now(),LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()).toMillis();}return Math.multiplyExact(Math.max(0,p.longValue("ttl-seconds",0)),1000);}
    private enum QuotaDenied {INSTANCE}
}
