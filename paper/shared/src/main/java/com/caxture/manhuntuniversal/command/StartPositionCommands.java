package com.caxture.manhuntuniversal.command;

import com.caxture.manhuntuniversal.api.config.ConfigSnapshot;
import com.caxture.manhuntuniversal.config.*;
import com.caxture.manhuntuniversal.extension.config.ConfigurationRegistryService;
import com.caxture.manhuntuniversal.i18n.LanguageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;

/** Specialized editing/view over the same atomic, lockable startup configuration root. */
final class StartPositionCommands {
    private final ConfigurationRegistryService registry;private final LanguageService language;
    StartPositionCommands(ConfigurationRegistryService registry,LanguageService language){this.registry=registry;this.language=language;}
    static boolean owns(String key){return key.equals(PlayerStartPositions.KEY)||key.startsWith(PlayerStartPositions.KEY+".");}
    boolean handle(CommandSender sender,String[] args){
        String key=args[1].toLowerCase(Locale.ROOT);if(!owns(key))return false;
        try{
            String suffix=key.equals(PlayerStartPositions.KEY)?"":key.substring(PlayerStartPositions.KEY.length()+1);
            if(suffix.isEmpty()&&args.length==2){show(sender,null);return true;}
            String[] path=suffix.split("\\.",-1);if(path.length==0||!List.of("hunter","runner").contains(path[0]))return false;
            if(path.length==1){if(args.length==2)show(sender,path[0]);else if(args.length==3)change(sender,key+".mode",args[2]);else throw new IllegalArgumentException(language.text(sender,"start-pos.usage"));return true;}
            if(path.length!=2||!List.of("add-custom","remove-custom").contains(path[1]))return false;
            if(!allowed(sender))return true;
            if(args.length!=5)throw new IllegalArgumentException(language.text(sender,"start-pos.usage"));
            World world=sender instanceof Player player?player.getWorld():sender.getServer().getWorlds().getFirst();
            var point=new StartPosition(world.getUID(),world.getName(),Double.parseDouble(args[2]),Double.parseDouble(args[3]),Double.parseDouble(args[4]));
            String listKey=PlayerStartPositions.KEY+"."+path[0]+".custom";var snapshot=registry.find(listKey).orElseThrow();
            var points=points(snapshot.savedValue());
            if(path[1].equals("add-custom")){if(points.stream().anyMatch(value->value.sameCoordinates(point)))throw new IllegalArgumentException(language.text(sender,"start-pos.duplicate"));points.add(point);}
            else if(!points.removeIf(value->value.sameCoordinates(point)))throw new IllegalArgumentException(language.text(sender,"start-pos.not-found"));
            update(sender,listKey,points.stream().map(StartPosition::toMap).toList());return true;
        }catch(RuntimeException error){language.send(sender,"config.invalid",Map.of("reason",Objects.toString(error.getMessage(),"Invalid start position")));return true;}
    }
    private boolean allowed(CommandSender sender){if(sender.hasPermission(MhuCommand.ADMIN_PERMISSION))return true;language.send(sender,"command.no-permission");return false;}
    private void change(CommandSender sender,String key,String mode){if(!allowed(sender))return;update(sender,key,registry.parse(key,mode));}
    private void update(CommandSender sender,String key,Object value){
        var result=registry.updateCommand(key,value);if(!result.successful()){language.send(sender,result.status()==com.caxture.manhuntuniversal.api.OperationStatus.SERVICE_UNAVAILABLE?"config.save-failed":"config.invalid",Map.of("reason",result.message()));return;}
        show(sender,null);
    }
    void show(CommandSender sender,String onlyRole){
        var root=registry.find(PlayerStartPositions.KEY).orElseThrow();
        var config=PlayerStartPositions.fromMap((Map<?,?>)root.nextMatchValue());
        send(sender,"title",language.chatComponent(sender,"config.map-start",Map.of("key",language.text(sender,"config.description.player-start-pos.title")))
                .hoverEvent(Component.text(PlayerStartPositions.KEY)));
        for(String role:List.of("hunter","runner")){
            if(onlyRole!=null&&!onlyRole.equals(role))continue;
            var team=config.team(role);String base=PlayerStartPositions.KEY+"."+role;
            var metadata=registry.find(base).orElseThrow();
            send(sender,role,Component.text("    "+language.text(sender,"start-pos.role."+role)+":",role.equals("hunter")?NamedTextColor.AQUA:NamedTextColor.RED));
            for(String mode:PlayerStartPositions.MODES){
                Component line=Component.text("        "+language.text(sender,"start-pos.mode."+mode)+(team.mode().equals(mode)?" ✓":""),team.mode().equals(mode)?NamedTextColor.GREEN:NamedTextColor.GRAY);
                if(sender instanceof Player)line=line.clickEvent(ClickEvent.runCommand("/mhu config "+base+" "+mode));
                send(sender,base+"."+mode,line.hoverEvent(Component.text(base)));
                if(!mode.equals("custom"))continue;
                send(sender,base+".open",Component.text("        {",NamedTextColor.GRAY));
                long revision=registry.find(base+".custom").orElseThrow().revision();
                for(var point:team.custom()){
                    Component remove=language.component(sender,"start-pos.remove",Map.of()).color(NamedTextColor.RED);
                    if(sender instanceof Player player){UUID viewer=player.getUniqueId();remove=remove.clickEvent(ClickEvent.callback(audience->{
                        if(!(audience instanceof Player clicked)||!viewer.equals(clicked.getUniqueId())||!allowed(clicked))return;
                        var current=registry.find(base+".custom").orElseThrow();
                        if(current.revision()!=revision){language.send(clicked,"start-pos.stale");show(clicked,null);return;}
                        var entries=points(current.savedValue());if(!entries.removeIf(value->value.sameCoordinates(point))){show(clicked,null);return;}
                        update(clicked,base+".custom",entries.stream().map(StartPosition::toMap).toList());
                    }));}
                    send(sender,base+".point",Component.text("            ("+point.x()+", "+point.y()+", "+point.z()+") ",NamedTextColor.GRAY).append(remove)
                            .hoverEvent(Component.text(point.worldName()+" ("+point.worldId()+")")));
                }
                Component add=language.component(sender,"start-pos.add",Map.of()).color(NamedTextColor.GREEN);
                if(sender instanceof Player)add=add.clickEvent(ClickEvent.suggestCommand("/mhu config "+base+".add-custom "));
                send(sender,base+".add",Component.text("            ").append(add));send(sender,base+".close",Component.text("        }",NamedTextColor.GRAY));
            }
            if(metadata.lockedBy().isPresent())send(sender,base+".locked",language.component(sender,"config.locked-detail",Map.of("owner",metadata.lockedBy().get(),"reason",metadata.lockReason().orElse(""))));
        }
        send(sender,"end",Component.text("}",NamedTextColor.GRAY).append(language.component(sender,"config.next-match",Map.of())));
    }
    private void send(CommandSender sender,String id,Component line){language.sendComponent(sender,"core:config.player-start-pos."+id,line);}
    private static List<StartPosition> points(Object raw){List<StartPosition> result=new ArrayList<>();for(Object value:(List<?>)raw)result.add(StartPosition.fromMap((Map<?,?>)value));return result;}
    List<String> complete(String[] args){String prefix=args[args.length-1].toLowerCase(Locale.ROOT);if(args.length==2){List<String> keys=new ArrayList<>();for(String role:List.of("hunter","runner")){String base=PlayerStartPositions.KEY+"."+role;keys.add(base);keys.add(base+".add-custom");keys.add(base+".remove-custom");}return keys.stream().filter(value->value.startsWith(prefix)).toList();}
        return args.length==3&&List.of(PlayerStartPositions.KEY+".hunter",PlayerStartPositions.KEY+".runner").contains(args[1])?PlayerStartPositions.MODES.stream().filter(value->value.startsWith(prefix)).toList():List.of();}
}
