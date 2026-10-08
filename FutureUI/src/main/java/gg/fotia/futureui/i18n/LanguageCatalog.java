package gg.fotia.futureui.i18n;

import gg.fotia.futureui.config.*;
import java.nio.file.*;
import java.util.*;

/** 本地词库采用和 FotiaTranslator 相同的文档前缀，逐键回退。 */
public final class LanguageCatalog {
    private final Map<String,Map<String,String>> languages;
    private final String fallback;
    private final Node aliases;
    public LanguageCatalog(Path root,String fallback,Node aliases) throws Exception {
        this.fallback=normalize(fallback);this.aliases=aliases;
        Map<String,Map<String,String>> loaded=new LinkedHashMap<>();
        try(var paths=Files.walk(root)){
            for(Path file:paths.filter(p->p.toString().endsWith(".yml")).sorted().toList()){
                Path relative=root.relativize(file);if(relative.getNameCount()<2)throw new IllegalArgumentException(file+": expected locale/document.yml");
                String locale=normalize(relative.getName(0).toString());String prefix=relative.subpath(1,relative.getNameCount()).toString().replace('\\','.').replace('/','.').replaceFirst("\\.yml$","");
                Map<String,String> entries=loaded.computeIfAbsent(locale,key->new LinkedHashMap<>());
                flatten(prefix,ConfigRepository.read(file),entries);
            }
        }
        if(!loaded.containsKey(this.fallback))throw new IllegalArgumentException("Missing default locale "+fallback);
        Map<String,Map<String,String>> snapshot=new LinkedHashMap<>();loaded.forEach((k,v)->snapshot.put(k,Map.copyOf(v)));languages=Map.copyOf(snapshot);
    }
    private static void flatten(String prefix,Node node,Map<String,String> result){
        node.values().forEach((key,value)->{
            String path=prefix+"."+key;
            if(value instanceof Node child)flatten(path,child,result);
            else if(value instanceof List<?> list){for(int i=0;i<list.size();i++)result.put(path+"."+i,String.valueOf(list.get(i)));}
            else if(result.putIfAbsent(path,String.valueOf(value))!=null)throw new IllegalArgumentException("Duplicate translation: "+path);
        });
    }
    public Optional<String> find(String locale,String key){
        LinkedHashSet<String> candidates=new LinkedHashSet<>();String current=normalize(locale);
        for(int n=0;n<8&&candidates.add(current);n++){String alias=aliases.text(current,"");if(alias.isEmpty())break;current=normalize(alias);}
        String base=normalize(locale).split("_")[0];String baseAlias=aliases.text(base,"");if(!baseAlias.isEmpty())candidates.add(normalize(baseAlias));
        candidates.add(base);candidates.add(fallback);candidates.add("en_us");
        for(String candidate:candidates){String value=languages.getOrDefault(candidate,Map.of()).get(key);if(value!=null)return Optional.of(value);}return Optional.empty();
    }
    public String fallback(){return fallback;}
    public Set<String> locales(){return languages.keySet();}
    public static String normalize(String locale){return locale==null||locale.isBlank()?"en_us":locale.replace('-','_').toLowerCase(Locale.ROOT);}
}
