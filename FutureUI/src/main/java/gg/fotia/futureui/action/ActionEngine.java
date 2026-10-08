package gg.fotia.futureui.action;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.condition.ConditionEngine;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.menu.MenuController;
import gg.fotia.futureui.model.MenuSession;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** 有界异步流程。主动导航可继续后续动作，外部替换会话则取消旧流程。 */
public final class ActionEngine {
    private enum Signal { NONE, BREAK, RETURN, STOP }
    private static final class Flow {
        int steps,depth;Signal signal=Signal.NONE;Object returned="";
        final UUID owner;final boolean closing;MenuSession expected;
        final long started=System.currentTimeMillis();
        Flow(MenuContext ctx,boolean closing){owner=ctx.session().playerId;expected=ctx.session();this.closing=closing;}
    }
    private final MenuController menus;private final StandardActions standard;
    private final Map<UUID,Map<String,org.bukkit.scheduler.BukkitTask>> tasks=new HashMap<>();
    public ActionEngine(MenuController menus){this.menus=menus;standard=new StandardActions(menus);}
    public CompletionStage<ActionResult> execute(MenuContext ctx,List<Node> actions){return execute(ctx,actions,new Flow(ctx,false));}
    public CompletionStage<ActionResult> closing(MenuContext ctx,List<Node> actions){return execute(ctx,actions,new Flow(ctx,true));}
    private CompletionStage<ActionResult> execute(MenuContext ctx,List<Node> actions,Flow flow){
        CompletableFuture<ActionResult> result=new CompletableFuture<>();
        step(ctx,actions,0,result,flow);return result;
    }
    private void step(MenuContext ctx,List<Node> actions,int index,CompletableFuture<ActionResult> result,Flow flow){
        try{stepChecked(ctx,actions,index,result,flow);}catch(RuntimeException error){menus.plugin.getLogger().warning("动作参数无效: "+error.getMessage());result.complete(ActionResult.fail("messages.action-failed"));}
    }
    private void stepChecked(MenuContext ctx,List<Node> actions,int index,CompletableFuture<ActionResult> result,Flow flow){
        if(result.isDone())return;
        if(index>=actions.size()||flow.signal!=Signal.NONE){result.complete(ActionResult.ok());return;}
        if(++flow.steps>menus.snapshot().settings().child("actions").integer("max-steps",1024)||System.currentTimeMillis()-flow.started>600000){result.complete(ActionResult.fail("messages.action-limit"));return;}
        Player owner=Bukkit.getPlayer(flow.owner);
        if(owner==null||!ctx.player().isOnline()||!flow.closing&&menus.sessions().get(flow.owner)!=flow.expected){result.complete(ActionResult.fail("messages.cancelled"));return;}
        Node action=actions.get(index);ctx.variables().putAll(action.child("requirement-context").values());Parameters p=new Parameters(ctx,action,menus.values);
        if(!menus.conditions.test(ctx,action.condition("condition"))||ThreadLocalRandom.current().nextDouble()>=p.decimal("chance","1").doubleValue()){next(ctx,actions,index,result,flow);return;}
        CompletionStage<ActionResult> pending;
        long started=System.nanoTime();
        try{pending=run(ctx,action,flow);}catch(Exception error){menus.plugin.getLogger().warning("动作执行失败 "+action.text("type","")+": "+error.getMessage());pending=StandardActions.done(ActionResult.fail("messages.action-failed"));}
        CompletableFuture<ActionResult> future=pending.toCompletableFuture();
        if(!Set.of("purchase","transaction","cart-checkout","sell","if","call","repeat","for-each","for-players","random","require","input","retry").contains(action.text("type","")))future=future.orTimeout(p.integer("timeout-seconds",30),TimeUnit.SECONDS);
        future.whenComplete((outcome,error)->menus.threads.run(()->{
            if(result.isDone())return;
            ActionResult value=error==null&&outcome!=null?outcome:ActionResult.fail("messages.action-timeout");
            menus.metrics.timing(action.text("type",""),System.nanoTime()-started);
            ctx.variables().put("action.success",value.success());ctx.variables().put("action.error",value.message());
            if(Set.of("open-menu","close","back","input").contains(action.text("type",""))&&ctx.player().getUniqueId().equals(flow.owner)&&value.success())flow.expected=menus.sessions().get(flow.owner);
            List<Node> branch=action.nodes(value.success()?"on-success":"on-failure");
            if(!value.success())menus.metrics.count("failure",action.text("type","unknown"));
            CompletionStage<ActionResult> handled=branch.isEmpty()||flow.signal!=Signal.NONE?StandardActions.ok():execute(ctx,branch,flow);
            handled.whenComplete((branchResult,branchError)->menus.threads.run(()->{
                ActionResult completed=branchError!=null?ActionResult.fail("messages.action-failed"):!branchResult.success()?branchResult:value.success()||p.bool("recover",false)||p.bool("continue-on-failure",false)?ActionResult.ok():value;
                Signal signal=flow.signal;flow.signal=Signal.NONE;
                CompletionStage<ActionResult> cleanup=action.nodes("finally").isEmpty()?StandardActions.ok():execute(ctx,action.nodes("finally"),flow);
                cleanup.whenComplete((finalResult,finalError)->menus.threads.run(()->{
                    if(flow.signal==Signal.NONE)flow.signal=signal;
                    if(finalError!=null)result.complete(ActionResult.fail("messages.action-failed"));
                    else if(!finalResult.success())result.complete(finalResult);
                    else if(!completed.success())result.complete(completed);
                    else next(ctx,actions,index,result,flow);
                }));
            }));
        }));
    }
    private void next(MenuContext ctx,List<Node> actions,int index,CompletableFuture<ActionResult> result,Flow flow){
        if(flow.steps%32==0)Bukkit.getScheduler().runTask(menus.plugin,()->step(ctx,actions,index+1,result,flow));
        else step(ctx,actions,index+1,result,flow);
    }
    private CompletionStage<ActionResult> run(MenuContext ctx,Node action,Flow flow){
        Parameters p=new Parameters(ctx,action,menus.values);
        switch(action.text("type","")){
            case "input":return menus.inputs.start(ctx,action).thenCompose(result->{String branch=Boolean.TRUE.equals(ctx.variables().get("input.timeout"))?"on-timeout":Boolean.TRUE.equals(ctx.variables().get("input.cancelled"))?"on-cancel":"";return branch.isEmpty()||action.nodes(branch).isEmpty()?StandardActions.done(result):execute(ctx,action.nodes(branch),flow).thenApply(ignored->result);});
            case "transaction":return menus.transactions.execute(ctx,action);
            case "if":return execute(ctx,action.nodes(menus.conditions.test(ctx,action.condition("when"))?"then":"else"),flow);
            case "break":flow.signal=Signal.BREAK;return StandardActions.ok();
            case "stop":flow.signal=Signal.STOP;return StandardActions.ok();
            case "return":flow.returned=p.object("value","");flow.signal=Signal.RETURN;return StandardActions.ok();
            case "call":{
                if(++flow.depth>24){flow.depth--;return StandardActions.done(ActionResult.fail("messages.action-limit"));}
                Node function=menus.snapshot().functions().get(p.string("function",""));if(function==null){flow.depth--;return StandardActions.done(ActionResult.fail("messages.unknown-function"));}
                Map<String,Object> args=new LinkedHashMap<>();function.child("parameters").values().forEach((key,v)->args.put("arg."+key,menus.values.object(ctx,v)));
                action.child("arguments").values().forEach((key,v)->args.put("arg."+key,menus.values.object(ctx,v)));
                MenuContext scoped=ctx.scoped(args);return execute(scoped,function.nodes("actions"),flow).whenComplete((ignored,error)->flow.depth--).thenApply(result->{if(flow.signal==Signal.RETURN){flow.signal=Signal.NONE;ctx.variables().put(p.string("output","result"),flow.returned);}for(String key:action.strings("exports"))if(scoped.variables().containsKey(key))ctx.variables().put(key,scoped.variables().get(key));return result;});
            }
            case "repeat":{
                int count=p.integer("times",1);if(count<0||count>256)throw new IllegalArgumentException("Repeat count must be 0..256");
                List<Object> values=new ArrayList<>();for(int i=0;i<count;i++)values.add(i);return loop(ctx,action,flow,values,0);
            }
            case "for-each":{
                Object source=p.object("values",List.of());if(!(source instanceof List<?> list)||list.size()>256)throw new IllegalArgumentException("for-each requires a list of at most 256 entries");return loop(ctx,action,flow,list,0);
            }
            case "for-players":{
                String selector=p.string("selector","viewer");List<Player> selected;
                if(selector.equals("all"))selected=new ArrayList<>(Bukkit.getOnlinePlayers());
                else{Player player=selector.equals("viewer")?ctx.player():Bukkit.getPlayerExact(selector);if(player==null)try{player=Bukkit.getPlayer(UUID.fromString(selector));}catch(IllegalArgumentException ignored){}selected=player==null?List.of():List.of(player);}
                List<MenuContext> contexts=new ArrayList<>();for(Player player:selected){Map<String,Object> variables=new LinkedHashMap<>(ctx.variables());variables.put("viewer.name",ctx.player().getName());variables.put("viewer.uuid",ctx.player().getUniqueId().toString());variables.put("target.name",player.getName());variables.put("target.uuid",player.getUniqueId().toString());MenuContext target=new MenuContext(player,ctx.session(),variables);if(menus.conditions.test(target,action.condition("filter")))contexts.add(target);}
                return players(contexts,action.nodes("actions"),flow,0);
            }
            case "random":{
                List<Node> options=action.nodes("choices");double sum=0;for(Node option:options){double weight=new Parameters(ctx,option,menus.values).decimal("weight","1").doubleValue();if(!Double.isFinite(weight)||weight<0)throw new IllegalArgumentException("Random weight must be finite and non-negative");sum+=weight;}
                if(!Double.isFinite(sum)||sum<=0)throw new IllegalArgumentException("Random choices require positive total weight");double pick=ThreadLocalRandom.current().nextDouble(sum);
                for(Node option:options){pick-=new Parameters(ctx,option,menus.values).decimal("weight","1").doubleValue();if(pick<0)return execute(ctx,option.nodes("actions"),flow);}return StandardActions.ok();
            }
            case "require":{
                ConditionEngine.Result report=menus.conditions.evaluate(ctx,action.condition("requirements"));List<Node> handlers=new ArrayList<>();collect(report,ctx,handlers);
                return execute(ctx,handlers,flow).thenApply(result->!result.success()?result:report.success()?ActionResult.ok():ActionResult.fail(p.string("message","messages.requirement-failed")));
            }
            case "schedule":{
                String key=p.string("key","default");cancel(ctx.session().playerId,key);int interval=p.integer("interval-ticks",20),count=p.integer("times",1);if(interval<1||count<1||count>1200)throw new IllegalArgumentException("Invalid scheduled action interval or count");
                final int[] runs={0};final boolean[] running={false};
                org.bukkit.scheduler.BukkitTask task=Bukkit.getScheduler().runTaskTimer(menus.plugin,()->{
                    if(!menus.isCurrent(ctx)||!ctx.player().isOnline()||runs[0]>=count){cancel(ctx.session().playerId,key);return;}if(running[0]||ctx.session().busy)return;
                    running[0]=true;ctx.session().busy=true;runs[0]++;execute(ctx,action.nodes("actions")).whenComplete((result,error)->menus.threads.run(()->{running[0]=false;ctx.session().busy=false;if(error!=null||!result.success())cancel(ctx.session().playerId,key);if(menus.isCurrent(ctx))menus.refresh(ctx.player());}));
                },Math.max(1,p.integer("delay-ticks",interval)),interval);
                tasks.computeIfAbsent(ctx.session().playerId,id->new HashMap<>()).put(key,task);return StandardActions.ok();
            }
            case "cancel-task":cancel(ctx.session().playerId,p.string("key","default"));return StandardActions.ok();
            case "retry":return retry(ctx,action,flow,0);
            default:return standard.run(ctx,action);
        }
    }
    private CompletionStage<ActionResult> retry(MenuContext ctx,Node action,Flow flow,int attempt){
        int count=new Parameters(ctx,action,menus.values).integer("attempts",2);if(count<1||count>3)throw new IllegalArgumentException("Retry attempts must be 1..3");
        return execute(ctx,action.nodes("actions"),flow).thenCompose(result->!result.success()&&attempt+1<count&&!result.message().equals("messages.transaction-pending")&&flow.signal==Signal.NONE?retry(ctx,action,flow,attempt+1):StandardActions.done(result));
    }
    private CompletionStage<ActionResult> loop(MenuContext ctx,Node action,Flow flow,List<?> list,int index){
        if(index>=list.size())return StandardActions.ok();String key=new Parameters(ctx,action,menus.values).string("as","loop.value");
        ctx.variables().put(key,list.get(index));ctx.variables().put("loop.index",index);ctx.variables().put("loop.first",index==0);ctx.variables().put("loop.last",index==list.size()-1);
        return execute(ctx,action.nodes("actions"),flow).thenCompose(result->{if(flow.signal==Signal.BREAK){flow.signal=Signal.NONE;return StandardActions.done(result);}if(!result.success()||flow.signal!=Signal.NONE)return StandardActions.done(result);return loop(ctx,action,flow,list,index+1);});
    }
    private CompletionStage<ActionResult> players(List<MenuContext> contexts,List<Node> actions,Flow flow,int index){
        if(index>=contexts.size())return StandardActions.ok();return execute(contexts.get(index),actions,flow).thenCompose(result->result.success()&&flow.signal==Signal.NONE?players(contexts,actions,flow,index+1):StandardActions.done(result));
    }
    private static void collect(ConditionEngine.Result result,MenuContext ctx,List<Node> actions){
        for(ConditionEngine.Result child:result.children())collect(child,ctx,actions);
        Map<String,Object> values=Map.of("requirement.id",result.id(),"requirement.actual",result.actual(),"requirement.expected",result.expected(),"requirement.success",result.success());
        for(Node action:result.source().nodes(result.success()?"on-success":"on-failure"))actions.add(action.merge(Node.of(Map.of("requirement-context",values))));
        ctx.variables().putAll(values);
    }
    public void cancel(UUID player,String key){Map<String,org.bukkit.scheduler.BukkitTask> owned=tasks.get(player);if(owned==null)return;var task=owned.remove(key);if(task!=null)task.cancel();if(owned.isEmpty())tasks.remove(player);}
    public void cancelAll(UUID player){Map<String,org.bukkit.scheduler.BukkitTask> owned=tasks.remove(player);if(owned!=null)owned.values().forEach(org.bukkit.scheduler.BukkitTask::cancel);}
    public CompletionStage<ActionResult> requirementHandlers(MenuContext ctx,ConditionEngine.Result report){List<Node> handlers=new ArrayList<>();collect(report,ctx,handlers);return menus.isCurrent(ctx)?execute(ctx,handlers):closing(ctx,handlers);}
}
