package gg.fotia.futureui.render;

import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.form.FormValidator;
import gg.fotia.futureui.i18n.TextService;
import gg.fotia.futureui.menu.MenuController;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.*;
import java.util.*;
import net.kyori.adventure.text.Component;

/** 原生表单控件与已提交值恢复。 */
public final class DialogFormRenderer {
    private final TextService text;private final MenuController menus;
    public DialogFormRenderer(MenuController menus){this.menus=menus;this.text=menus.text;}
    public List<DialogInput> render(MenuContext ctx,List<Node> components){
        List<DialogInput> result=new ArrayList<>();for(Node node:FormValidator.fields(components)){
            MenuContext scoped=ctx.scoped(node.child("context").values());
            String id=node.text("id",""),type=node.text("type","");Object saved=ctx.variables().get("input."+id);
            Component label=text.render(ctx.player(),ctx.session().locale,node.text("label",""),scoped.variables());
            if(saved instanceof String value){if(type.equals("slider")){try{float number=Float.parseFloat(value);saved=Float.isFinite(number)?number:null;}catch(NumberFormatException ignored){saved=null;}}else if(type.equals("checkbox"))saved=Boolean.parseBoolean(value);}
            if(type.equals("slider")&&saved instanceof Number number&&!Double.isFinite(number.doubleValue()))saved=null;
            int width=node.integer("width",260);
            switch(type){
                case "text-input","number-input"->result.add(DialogInput.text(id,width,label,node.bool("label-visible",true),saved==null?node.text("initial",""):String.valueOf(saved),node.integer("max-length",type.equals("number-input")?32:128),node.bool("multiline",false)?TextDialogInput.MultilineOptions.create(node.integer("max-lines",4),node.integer("height",48)):null));
                case "slider"->{var range=gg.fotia.futureui.form.NumericRange.of(node);float initial=range.clamp(saved instanceof Number n?java.math.BigDecimal.valueOf(n.doubleValue()):range.initial()).floatValue();result.add(DialogInput.numberRange(id,width,label,"options.generic_value",range.min().floatValue(),range.max().floatValue(),initial,range.step().floatValue()));}
                case "checkbox"->result.add(DialogInput.bool(id,label,saved instanceof Boolean b?b:node.bool("initial",false),"true","false"));
                case "select"->{String previous=saved==null?node.text("initial",""):saved.toString();
                    String selected=node.nodes("options").stream().anyMatch(n->n.text("value","").equals(previous))?previous:node.text("initial","");
                    List<SingleOptionDialogInput.OptionEntry> entries=node.nodes("options").stream().map(option->SingleOptionDialogInput.OptionEntry.create(option.text("value",""),text.render(ctx.player(),ctx.session().locale,option.text("label",""),scoped.variables()),option.text("value","").equals(selected))).toList();result.add(DialogInput.singleOption(id,width,entries,label,node.bool("label-visible",true)));}
                default->throw new IllegalArgumentException("Unknown input "+type);
            }
        }return result;
    }
    /** 原生输入标签不会自动换行，校验反馈使用可换行正文，保持输入框和按钮对齐。 */
    public record Feedback(List<DialogBody> bodies,int height){}
    public Feedback feedback(MenuContext ctx,List<Node> components){
        Node style=ctx.session().menu.layout().child("validation-feedback");
        List<Component> messages=new ArrayList<>();int width=0;
        for(Node field:FormValidator.fields(components)){
            String error=ctx.session().errors.get(field.text("id",""));if(error==null)continue;
            width=Math.max(width,field.integer("width",260));MenuContext scoped=ctx.scoped(field.child("context").values());
            Component message=text.render(ctx.player(),ctx.session().locale,error.startsWith("@")?error:"@"+error,scoped.variables());
            if(style.bool("show-label",true))message=text.render(ctx.player(),ctx.session().locale,field.text("label",""),scoped.variables()).append(Component.text(": ")).append(message);
            messages.add(message);
        }
        if(messages.isEmpty()&&style.integer("min-height",0)==0&&!style.has("text"))return new Feedback(List.of(),0);
        if(messages.isEmpty())messages.add(text.render(ctx.player(),ctx.session().locale,style.text("text",""),ctx.variables()));
        Component message=Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(),messages);
        Map<String,Object> parameters=new LinkedHashMap<>(ctx.variables());parameters.put("feedback",message);
        Node panel=style.merge(Node.of(Map.of("id","validation_feedback","type","text","text","{feedback}","width",style.integer("width",Math.max(260,width)))));
        int padding=panel.integer("padding",panel.has("skin")?9:0);
        int height=Math.max(panel.integer("min-height",0),TextFlow.lines(message,panel.integer("width",260)-2*padding,64).size()*9+padding*2);
        return new Feedback(List.of(DialogTextPanel.create(menus,ctx,panel,parameters)),height+18);
    }
    public Map<String,Object> collect(DialogResponseView response,List<Node> components){Map<String,Object> values=new LinkedHashMap<>();for(Node field:FormValidator.fields(components)){
        String id=field.text("id","");Object value=switch(field.text("type","")){case "slider"->response.getFloat(id);case "checkbox"->response.getBoolean(id);default->response.getText(id);};if(value!=null)values.put(id,value);
    }return values;}
}
