package gg.fotia.futureui.condition;

import com.google.re2j.Pattern;
import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** 通用条件树，显示条件和点击条件共用，但执行动作前必须重新检查。 */
public final class ConditionEngine {
    private final ValueResolver resolver;private final ExtensionRegistry<MenuCondition> extensions;
    private final ExtensionRegistry<CurrencyProvider> currencies;private final Logger logger;
    private final Set<String> reported=new HashSet<>();
    private gg.fotia.futureui.item.ItemMatcher itemMatcher;private gg.fotia.futureui.state.DataStore storage;
    private Map<String,Node> rules=Map.of();
    public void items(gg.fotia.futureui.item.ItemMatcher matcher){itemMatcher=matcher;}
    public void storage(gg.fotia.futureui.state.DataStore value){storage=value;}
    public void rules(Map<String,Node> value){rules=Map.copyOf(value);}
    public record Result(String id,String type,boolean success,String actual,String expected,List<Result> children,Node source,String error){}
    public ConditionEngine(ValueResolver resolver,ExtensionRegistry<MenuCondition> extensions,ExtensionRegistry<CurrencyProvider> currencies,Logger logger){this.resolver=resolver;this.extensions=extensions;this.currencies=currencies;this.logger=logger;}
    public boolean test(MenuContext context,Node node){
        return evaluate(context,node).success();
    }
    public Result evaluate(MenuContext ctx,Node node){try{return evaluate(ctx,node,0);}catch(RuntimeException error){String key=node.toString()+error.getMessage();if(reported.add(key))logger.warning("条件执行失败: "+error.getMessage());return new Result(node.text("id",""),node.text("type","all"),false,"","",List.of(),node,error.getMessage());}}
    private Result evaluate(MenuContext ctx,Node source,int depth){
        if(depth>24)throw new IllegalArgumentException("Condition nesting exceeds 24");
        Node node=resolver.bind(ctx,source,Set.of("expression","pattern","name","lore"));String type=node.text("type","all"),id=node.text("id",type);
        if(type.equals("named")){String name=node.text("rule","");Node rule=rules.get(name);if(rule==null)throw new IllegalArgumentException("Unknown named requirement "+name);Map<String,Object> args=new LinkedHashMap<>();node.child("arguments").values().forEach((k,v)->args.put("arg."+k,resolver.object(ctx,v)));Result nested=evaluate(ctx.scoped(args),rule,depth+1);return new Result(id,type,nested.success(),nested.actual(),nested.expected(),List.of(nested),node,nested.error());}
        boolean invert=type.startsWith("!");String base=invert?type.substring(1):type;List<Result> children=new ArrayList<>();boolean success;
        if(Set.of("all","any","at-least","requirements").contains(base)){
            List<Node> tests=node.nodes("conditions");int minimum=node.integer("minimum",base.equals("any")||base.equals("at-least")?1:(int)tests.stream().filter(n->!n.bool("optional",false)).count());int passed=0;boolean mandatory=true;
            for(int i=0;i<tests.size();i++){Node test=tests.get(i);Result result=evaluate(ctx,test,depth+1);children.add(result);if(result.success())passed++;else if(!test.bool("optional",false)&&Set.of("all","requirements").contains(base))mandatory=false;
                if(node.bool("stop-at-success",false)&&mandatory&&passed>=minimum&&(!Set.of("all","requirements").contains(base)||tests.subList(i+1,tests.size()).stream().allMatch(n->n.bool("optional",false))))break;
            }
            success=mandatory&&passed>=minimum;return new Result(id,type,invert?!success:success,String.valueOf(passed),String.valueOf(minimum),List.copyOf(children),node,"");
        }
        if(base.equals("not")){Result child=evaluate(ctx,node.condition("condition"),depth+1);children.add(child);success=invert?child.success():!child.success();}
        else success=check(ctx,node,depth);
        return new Result(id,type,success,actual(ctx,node),node.text("output",node.text("amount",node.text("value",""))),List.copyOf(children),node,"");
    }
    private String actual(MenuContext ctx,Node node){return switch(node.text("type","")){
        case "item","has-item"->String.valueOf(itemMatcher.count(ctx,node));case "currency","has-money"->{CurrencyProvider c=currencies.get(node.text("currency","vault"));yield c==null||!c.available()?"unavailable":c.balance(ctx.player()).toPlainString();}
        case "cooldown"->String.valueOf(storage.remaining(ctx.player().getUniqueId().toString(),"cooldown."+node.text("key","default"))/1000);
        default->node.text("input","");};}
    private boolean check(MenuContext ctx,Node node,int depth){
        if(depth>24)throw new IllegalArgumentException("Condition nesting exceeds 24");if(node.empty())return true;
        String type=node.text("type","all");boolean invert=type.startsWith("!");if(invert)type=type.substring(1);
        if(type.equals("not"))return !check(ctx,node.condition("condition"),depth+1);
        List<Node> children=node.nodes("conditions");
        final int nextDepth=depth+1;
        boolean result=switch(type){
            case "all"->children.stream().allMatch(n->check(ctx,n,nextDepth));
            case "any"->children.stream().anyMatch(n->check(ctx,n,nextDepth));
            case "at-least"->children.stream().filter(n->check(ctx,n,nextDepth)).count()>=node.integer("minimum",1);
            case "permission","has-permission"->ctx.player().hasPermission(resolve(ctx,node,"permission",""));
            case "permissions"->node.strings("permissions").stream().filter(ctx.player()::hasPermission).count()>=node.integer("minimum",node.strings("permissions").size());
            case "currency","has-money"->money(ctx,node);
            case "item","has-item"->itemMatcher.count(ctx,node)>=node.integer("amount",1);
            case "experience"->(node.bool("levels",true)?ctx.player().getLevel():experience(ctx.player()))>=decimal(ctx,node,"amount","0").doubleValue();
            case "world"->node.strings("worlds").contains(ctx.player().getWorld().getName());
            case "gamemode"->node.strings("modes").stream().anyMatch(m->m.equalsIgnoreCase(ctx.player().getGameMode().name()));
            case "health"->range(ctx.player().getHealth(),ctx,node);
            case "food"->range(ctx.player().getFoodLevel(),ctx,node);
            case "level"->range(ctx.player().getLevel(),ctx,node);
            case "world-time"->range(ctx.player().getWorld().getTime(),ctx,node);
            case "weather"->node.text("weather","clear").equals(ctx.player().getWorld().isThundering()?"thunder":ctx.player().getWorld().hasStorm()?"rain":"clear");
            case "sneaking"->ctx.player().isSneaking();
            case "flying"->ctx.player().isFlying();
            case "op"->ctx.player().isOp();
            case "empty-slots"->Arrays.stream(ctx.player().getInventory().getStorageContents()).filter(i->i==null||i.getType().isAir()).count()>=node.integer("amount",1);
            case "cooldown"->storage.remaining(ctx.player().getUniqueId().toString(),"cooldown."+node.text("key","default"))==0;
            case "equals","string-equals"->input(ctx,node).equals(output(ctx,node));
            case "equals-ignore-case"->input(ctx,node).equalsIgnoreCase(output(ctx,node));
            case "contains"->input(ctx,node).contains(output(ctx,node));
            case "starts-with"->input(ctx,node).startsWith(output(ctx,node));
            case "ends-with"->input(ctx,node).endsWith(output(ctx,node));
            case "length"->range(input(ctx,node).codePointCount(0,input(ctx,node).length()),ctx,node);
            case "regex"->Pattern.compile(node.text("pattern","")).matcher(input(ctx,node)).matches();
            case "in"->node.strings("values").contains(input(ctx,node));
            case "exists"->!input(ctx,node).isBlank()&&!input(ctx,node).matches("[\\{%].*[}%]");
            case "data"->{String scope=node.text("scope","player").equals("global")?"global":ctx.player().getUniqueId().toString();Object value=storage.get(scope,node.text("key",""),null);yield value!=null&&(!node.has("value")||String.valueOf(value).equals(node.text("value","")));}
            case "plugin"->Bukkit.getPluginManager().isPluginEnabled(node.text("plugin",""));
            case "list-contains"->{Object value=resolver.object(ctx,node.get("list"));yield value instanceof List<?> list&&list.stream().anyMatch(v->String.valueOf(v).equals(node.text("value","")));}
            case "number"->isNumber(input(ctx,node));
            case "integer"->input(ctx,node).matches("[-+]?\\d+");
            case "uuid"->isUuid(input(ctx,node));
            case "compare","==","!=",">",">=","<","<="->compare(ctx,node,type);
            case "expression"->Expression.evaluate(node.text("expression","false"),s->resolver.resolve(ctx,s));
            case "pdc"->pdc(ctx,node);
            case "online-player"->Bukkit.getPlayerExact(input(ctx,node))!=null;
            case "scoreboard-tag"->ctx.player().getScoreboardTags().contains(resolve(ctx,node,"tag",""));
            case "biome"->node.strings("biomes").contains(ctx.player().getLocation().getBlock().getBiome().getKey().toString());
            case "distance"->{var position=ctx.player().getLocation();double dx=position.getX()-node.number("x",0),dy=position.getY()-node.number("y",0),dz=position.getZ()-node.number("z",0);yield (!node.has("world")||position.getWorld().getName().equals(node.text("world","")))&&range(Math.sqrt(dx*dx+dy*dy+dz*dz),ctx,node);}
            case "day-of-week"->node.strings("days").stream().anyMatch(day->day.equalsIgnoreCase(java.time.LocalDate.now(java.time.ZoneId.of(node.text("timezone","UTC"))).getDayOfWeek().name()));
            case "true"->true;case "false"->false;
            default->{MenuCondition extension=extensions.get(type);if(extension==null)throw new IllegalArgumentException("Unknown condition: "+type);yield extension.test(ctx,node);}
        };
        return invert?!result:result;
    }
    private boolean money(MenuContext ctx,Node node){CurrencyProvider currency=currencies.get(node.text("currency","vault"));return currency!=null&&currency.available()&&currency.balance(ctx.player()).compareTo(decimal(ctx,node,"amount","0"))>=0;}
    private boolean items(MenuContext ctx,Node node){
        Material material=Material.matchMaterial(node.text("material","AIR"));if(material==null||material.isAir())return false;
        int count=0;for(ItemStack item:ctx.player().getInventory().getStorageContents())if(item!=null&&item.getType()==material){
            if(node.has("custom-model-data")&&(!item.getItemMeta().hasCustomModelData()||item.getItemMeta().getCustomModelData()!=node.integer("custom-model-data",0)))continue;
            count+=item.getAmount();
        }return count>=node.integer("amount",1);
    }
    private boolean pdc(MenuContext ctx,Node node){NamespacedKey key=NamespacedKey.fromString(node.text("key",""));if(key==null)return false;var data=ctx.player().getPersistentDataContainer();
        return switch(node.text("data-type","string")){
            case "integer"->data.has(key,PersistentDataType.INTEGER)&&data.get(key,PersistentDataType.INTEGER)>=node.integer("value",0);
            case "double"->data.has(key,PersistentDataType.DOUBLE)&&data.get(key,PersistentDataType.DOUBLE)>=node.number("value",0);
            case "boolean"->data.has(key,PersistentDataType.BYTE)&&(data.get(key,PersistentDataType.BYTE)!=0)==node.bool("value",true);
            default->Objects.equals(data.get(key,PersistentDataType.STRING),resolve(ctx,node,"value",""));
        };}
    private boolean compare(MenuContext ctx,Node node,String type){int value=new BigDecimal(input(ctx,node)).compareTo(new BigDecimal(output(ctx,node)));String op=type.equals("compare")?node.text("operator","=="):type;return switch(op){case "=="->value==0;case "!="->value!=0;case ">"->value>0;case ">="->value>=0;case "<"->value<0;case "<="->value<=0;default->throw new IllegalArgumentException("Invalid comparator "+op);};}
    private boolean range(double value,MenuContext ctx,Node node){return value>=decimal(ctx,node,"min","-1E100").doubleValue()&&value<=decimal(ctx,node,"max","1E100").doubleValue();}
    private String resolve(MenuContext c,Node n,String key,String fallback){return n.text(key,fallback);}
    private String input(MenuContext c,Node n){return resolve(c,n,"input","");}
    private String output(MenuContext c,Node n){return resolve(c,n,"output","");}
    private BigDecimal decimal(MenuContext c,Node n,String key,String fallback){return new BigDecimal(resolve(c,n,key,fallback));}
    private static boolean isNumber(String value){try{new BigDecimal(value);return true;}catch(NumberFormatException error){return false;}}
    private static boolean isUuid(String value){try{UUID.fromString(value);return true;}catch(IllegalArgumentException error){return false;}}
    public static int experience(org.bukkit.entity.Player player){int level=player.getLevel();double base=level<=16?level*level+6*level:level<=31?2.5*level*level-40.5*level+360:4.5*level*level-162.5*level+2220;return (int)Math.round(base+player.getExp()*player.getExpToLevel());}
}
