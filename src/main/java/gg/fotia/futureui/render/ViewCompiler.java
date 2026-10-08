package gg.fotia.futureui.render;

import gg.fotia.futureui.api.*;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.form.FormInputs;
import gg.fotia.futureui.layout.ListLayout;
import gg.fotia.futureui.menu.MenuController;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 数据列表和模板先展开为普通组件，所有渲染器共享同一个组件树。 */
public final class ViewCompiler {
    private final MenuController menus;private final ShopViewBuilder shop;
    public ViewCompiler(MenuController menus){this.menus=menus;shop=new ShopViewBuilder(menus);}
    public List<Node> compile(MenuContext ctx){MenuValues.populate(ctx,menus);if(ctx.session().menu.source().bool("cart-summary",false))menus.commerce.rows(ctx,false);MenuBindings.populate(ctx,menus);shop.productContext(ctx);return expand(ctx,ctx.session().menu.components(),Map.of(),0);}
    private List<Node> expand(MenuContext ctx,List<Node> nodes,Map<String,Object> inherited,int depth){
        if(depth>20)throw new IllegalArgumentException("Component nesting exceeds limit");List<Node> result=new ArrayList<>();
        for(Node original:nodes){
            original=AnimationFrames.apply(ctx,original);Node node=ComponentBindings.bind(ctx,original,inherited,menus.values);MenuContext scoped=ctx.scoped(node.child("context").values());
            if(!node.nodes("states").isEmpty()){
                MenuContext stateContext=scoped;
                Node state=node.nodes("states").stream().sorted(Comparator.comparingInt(n->n.integer("priority",0))).filter(n->menus.conditions.test(stateContext,n.condition("visible"))).findFirst().orElse(null);if(state==null)continue;
                node=ComponentBindings.bind(ctx,original.merge(state),inherited,menus.values);scoped=ctx.scoped(node.child("context").values());
            }
            if(!menus.conditions.test(scoped,node.condition("visible")))continue;
            String type=node.text("type","text");
            if(type.equals("shop")){result.addAll(shop.build(scoped,node));continue;}
            if(type.equals("list")){result.addAll(list(scoped,node,depth));continue;}
            if(FormInputs.TYPES.contains(type)){result.addAll(FormInputs.compile(scoped,node,menus.values));continue;}
            Map<String,Object> value=new LinkedHashMap<>(node.values());
            if(node.has("children"))value.put("children",expand(ctx,node.nodes("children"),node.child("context").values(),depth+1));
            result.add(new Node(value));
        }return result;
    }
    private List<Node> list(MenuContext ctx,Node node,int depth){
        String key="list."+node.text("id","");Object loaded=ctx.session().variables.get(key);MenuDataSource provider=menus.data.get(node.text("source",""));
        if(provider==null)return status(ctx,node,"@messages.data-unavailable",depth);
        if(loaded==null||!node.bool("cache",true)&&loaded instanceof List<?>){
            CompletableFuture<List<Node>> request=provider.load(ctx,node).toCompletableFuture();
            if(request.isDone()){
                try{loaded=request.join();if(node.bool("cache",true))ctx.session().variables.put(key,loaded);}catch(Exception error){return status(ctx,node,"@messages.data-failed",depth);}
            }else{
                UUID marker=UUID.randomUUID();long revision=menus.snapshot().revision();ctx.session().variables.put(key,marker);
                request.whenComplete((rows,error)->menus.threads.run(()->{
                    if(!menus.isCurrent(ctx)||!marker.equals(ctx.session().variables.get(key)))return;
                    if(revision!=menus.snapshot().revision()){ctx.session().variables.remove(key);return;}
                    ctx.session().variables.put(key,error==null&&rows!=null?List.copyOf(rows):"error");if(!ctx.session().menu.form())menus.refresh(ctx.player());
                }));return status(ctx,node,"@messages.loading",depth);
            }
        }
        if(!(loaded instanceof List<?> values))return status(ctx,node,"error".equals(loaded)?"@messages.data-failed":"@messages.loading",depth);
        List<?> page=values;int size=node.integer("page-size",9);
        if(node.bool("paginate",true)){int pages=Math.max(1,(values.size()+size-1)/size);ctx.session().page=Math.min(ctx.session().page,pages-1);ctx.session().variables.put("page",ctx.session().page+1);ctx.session().variables.put("pages",pages);page=values.stream().skip((long)ctx.session().page*size).limit(size).toList();}
        List<Node> expanded=new ArrayList<>();int index=0;
        for(Object value:page){Map<String,Object> context=new LinkedHashMap<>(node.child("context").values());Node.of(value).values().forEach((k,v)->context.put("item."+k,v));Node row=bind(node.child("item"),node.text("id","")+"_"+index++);expanded.addAll(expand(ctx,List.of(row),context,depth+1));}
        // 固定操作可跟随动态数据参与同一个网格，例如“最大值”或“添加”。
        expanded.addAll(expand(ctx,node.nodes("after-items"),node.child("context").values(),depth+1));
        if(expanded.isEmpty())return status(ctx,node,"@messages.empty",depth);
        return ListLayout.grouped(node)?List.of(ListLayout.wrap(node,expanded)):expanded;
    }
    private Node bind(Node template,String prefix){
        Map<String,Object> item=new LinkedHashMap<>(template.values());item.put("id",prefix+"_"+template.text("id","item"));
        for(String key:List.of("children","after-items"))if(template.has(key))item.put(key,template.nodes(key).stream().map(child->bind(child,prefix)).toList());return Node.of(item);
    }
    private List<Node> status(MenuContext ctx,Node node,String text,int depth){
        Node message=Node.of(Map.of("id",node.text("id","")+"_status","type","text","text",text,"context",node.child("context")));
        List<Node> content=new ArrayList<>();content.add(message);content.addAll(expand(ctx,node.nodes("after-items"),node.child("context").values(),depth+1));
        return ListLayout.grouped(node)?List.of(ListLayout.wrap(node,content)):content;
    }
    public static List<Node> flatten(List<Node> nodes){List<Node> result=new ArrayList<>();for(Node node:nodes){if(node.nodes("children").isEmpty())result.add(node);else result.addAll(flatten(node.nodes("children")));}return result;}
    /** 表单中的画布展示区作为一个正文保留，内部组件不展开成纵向原生正文。 */
    public static List<Node> dialogComponents(List<Node> nodes){List<Node> result=new ArrayList<>();for(Node node:nodes){if(node.text("type","").equals("canvas")||node.nodes("children").isEmpty())result.add(node);else result.addAll(dialogComponents(node.nodes("children")));}return result;}
}
