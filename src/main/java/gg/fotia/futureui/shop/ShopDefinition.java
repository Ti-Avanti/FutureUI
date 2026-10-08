package gg.fotia.futureui.shop;

import gg.fotia.futureui.config.Node;
import java.math.BigDecimal;
import java.util.*;

/** 商店是可配置业务模块，不向通用菜单核心注入特定插件逻辑。 */
public record ShopDefinition(String id,String title,List<Category> categories,Map<String,Product> products) {
    public ShopDefinition{categories=List.copyOf(categories);products=Collections.unmodifiableMap(new LinkedHashMap<>(products));}
    public record Category(String id,String name,String icon){}
    public record Product(String id,String category,String name,Node item,String currency,BigDecimal price,int bundle,QuantityRule quantity,Node requirement,int stock,Node options){public int maximum(){return quantity.max();}}
    public static ShopDefinition parse(String id,Node node){
        List<Category> categories=node.nodes("categories").stream().map(c->new Category(c.text("id",""),c.text("name",""),c.text("icon","CHEST"))).toList();
        Set<String> categoryIds=new HashSet<>();for(Category c:categories)if(c.id().isBlank()||!categoryIds.add(c.id()))throw new IllegalArgumentException("Duplicate/empty category in "+id);
        if(categories.isEmpty())throw new IllegalArgumentException("Shop requires at least one category: "+id);
        Map<String,Product> products=new LinkedHashMap<>();for(Node p:node.nodes("products")){
            String key=p.text("id","");String configured=p.text("price","0");BigDecimal price=configured.contains("{")||configured.contains("%")?BigDecimal.ZERO:p.decimal("price","0");int bundle=p.integer("amount",1);QuantityRule quantity=QuantityRule.parse(p);
            gg.fotia.futureui.config.ConditionValidator.validate(p.condition("requirements"),"shops/"+id+"/"+key,0);
            if(key.isBlank()||products.containsKey(key)||!categoryIds.contains(p.text("category",""))||price.signum()<0||bundle<1||p.integer("stock",-1)<-1)throw new IllegalArgumentException("Invalid shop product "+id+":"+key);
            if(!key.matches("[a-z0-9_.-]+")||p.integer("daily-limit",-1)<-1||p.integer("daily-sell-limit",-1)<-1)throw new IllegalArgumentException("Invalid product id or daily limit "+id+":"+key);
            for(Node discount:p.nodes("discounts"))gg.fotia.futureui.config.ConditionValidator.validate(discount.condition("condition"),"shops/"+id+"/"+key+".discounts",0);
            products.put(key,new Product(key,p.text("category",""),p.text("name",""),p.child("item"),p.text("currency","vault"),price,bundle,quantity,p.condition("requirements"),p.integer("stock",-1),p));
        }return new ShopDefinition(id,node.text("title","@menus.shop.title"),categories,products);
    }
}
