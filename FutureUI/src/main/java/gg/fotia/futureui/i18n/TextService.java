package gg.fotia.futureui.i18n;

import gg.fotia.futureui.integration.PlaceholderHook;
import gg.fotia.futureui.condition.ValueResolver;
import gg.fotia.futureui.config.Node;
import gg.fotia.futureui.util.LegacyColors;
import java.util.*;
import java.util.regex.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

/** 按观看者渲染；参数作为文本插入，不能执行玩家输入中的 MiniMessage。 */
public final class TextService {
    private static final Pattern ARG=Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");
    private final MiniMessage mini=MiniMessage.miniMessage();
    private LanguageCatalog catalog;
    private TranslationBackend backend;
    private final boolean translatorMode;
    private final boolean papi;
    private ValueResolver values;
    public TextService(LanguageCatalog catalog,boolean translatorMode,boolean papi){this.catalog=catalog;this.translatorMode=translatorMode;this.papi=papi;}
    public void catalog(LanguageCatalog value){catalog=value;}
    public void backend(TranslationBackend value){backend=value;}
    public TranslationBackend backend(){return backend;}
    public void resolver(ValueResolver value){values=value;}
    public String locale(Player player){String client=LanguageCatalog.normalize(player.locale().toLanguageTag());return translatorMode&&backend!=null&&backend.ready()?backend.locale(player.getUniqueId(),client):client;}
    public Component render(Player player,String locale,String template,Map<String,?> args){
        return render(player,locale,template,args,Node.of(Map.of()));
    }
    public Component render(Player player,String locale,String template,Map<String,?> args,Node policy){
        if(template==null||template.isBlank())return Component.empty();
        String source=template.startsWith("@")?catalog.find(locale,template.substring(1)).orElse("["+template.substring(1)+"]"):template;
        if(values!=null){
            var resolution=values.placeholders().resolve(player,source,key->values.value(player,args,key),policy.merge(Node.of(Map.of("text-output",true))));
            String prepared=resolution.template();TagResolver.Builder tags=TagResolver.builder();int i=0;
            for(var entry:resolution.literals().entrySet()){
                String tag="literal"+(i++);Object value=entry.getValue();tags.resolver(value instanceof Component component?Placeholder.component(tag,component):Placeholder.unparsed(tag,String.valueOf(value)));prepared=prepared.replace(entry.getKey(),"<"+tag+">");
            }
            Pattern rich=Pattern.compile("\uFDD1(\\d+)\uFDEF([^\uFDD1]*?)\uFDD2\\1\uFDEF",Pattern.DOTALL);
            while(true){Matcher spans=rich.matcher(prepared);if(!spans.find())break;StringBuilder joined=new StringBuilder();do{String tag="rich"+(i++);Component value=mini.deserialize(LegacyColors.convert(spans.group(2)),tags.build());tags.resolver(Placeholder.component(tag,value));spans.appendReplacement(joined,"<"+tag+">");}while(spans.find());spans.appendTail(joined);prepared=joined.toString();}
            return mini.deserialize(LegacyColors.convert(prepared),tags.build()).decorationIfAbsent(TextDecoration.ITALIC,TextDecoration.State.FALSE);
        }
        TagResolver.Builder resolvers=TagResolver.builder();Matcher matcher=ARG.matcher(source);StringBuilder prepared=new StringBuilder();int index=0;
        while(matcher.find()){
            String key=matcher.group(1),tag="arg"+(index++);Object value=args.get(key);
            resolvers.resolver(value instanceof Component component?Placeholder.component(tag,component):Placeholder.unparsed(tag,value==null?matcher.group():String.valueOf(value)));
            matcher.appendReplacement(prepared,"<"+tag+">");
        }
        matcher.appendTail(prepared);
        if(papi&&player!=null){
            Matcher pm=Pattern.compile("%[^%\\s]+%").matcher(prepared.toString());StringBuilder replaced=new StringBuilder();
            while(pm.find()){String tag="papi"+(index++);resolvers.resolver(Placeholder.unparsed(tag,PlaceholderHook.parse(player,pm.group())));pm.appendReplacement(replaced,"<"+tag+">");}
            pm.appendTail(replaced);prepared=replaced;
        }
        return mini.deserialize(LegacyColors.convert(prepared.toString()),resolvers.build()).decorationIfAbsent(TextDecoration.ITALIC,TextDecoration.State.FALSE);
    }
    public Component message(Player player,String key,Map<String,?> arguments){return render(player,locale(player),"@"+key,arguments);}
    public String plain(Player player,String locale,String template,Map<String,?> args){return PlainTextComponentSerializer.plainText().serialize(render(player,locale,template,args));}
    public boolean translatorMode(){return translatorMode;}
    public LanguageCatalog catalog(){return catalog;}
}
