package com.caxture.manhuntuniversal.command;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.extension.GameExtensionHooks;
import com.caxture.manhuntuniversal.game.*;
import com.caxture.manhuntuniversal.i18n.LanguageService;
import org.bukkit.command.CommandSender;
import java.util.*;
import java.util.function.*;

/** Role-only adaptation of the reference debug command's routing and fixed-roster player lookup. */
public final class DebugCommandHandler {
    private static final List<String> FEATURES=List.of("role-reshuffle","role-set");
    @FunctionalInterface interface Messages {
        void send(CommandSender sender,String key,Map<String,String> placeholders);
        default String text(CommandSender sender,String key) { return key; }
    }
    private final GameManager games;
    private final Supplier<GameExtensionHooks> hooks;
    private final BooleanSupplier hidden;
    private final Messages messages;
    public DebugCommandHandler(GameManager games,Supplier<GameExtensionHooks> hooks,BooleanSupplier hidden,LanguageService language) {
        this(games,hooks,hidden,new Messages(){
            public void send(CommandSender sender,String key,Map<String,String> placeholders){language.send(sender,key,placeholders);}
            public String text(CommandSender sender,String key){return language.text(sender,key);}
        });
    }
    DebugCommandHandler(GameManager games,Supplier<GameExtensionHooks> hooks,BooleanSupplier hidden,Messages messages) {
        this.games=games;this.hooks=hooks;this.hidden=hidden;this.messages=messages;
    }
    public void handle(CommandSender sender,String[] args) {
        if(!allowed(sender)){send(sender,"command.no-permission");return;}
        if(args.length<2){send(sender,"debug.usage");return;}
        String feature=args[1].toLowerCase(Locale.ROOT);
        if(!FEATURES.contains(feature)){messages.send(sender,"debug.unknown-feature",Map.of("feature",args[1]));return;}
        if(args.length!=(feature.equals("role-set")?4:2)){send(sender,"debug.usage");return;}
        if(hooks.get().mutationActive()){send(sender,"command.busy");return;}
        GameSession session=games.activeSession().filter(value->value.state()==SessionState.RUNNING).orElse(null);
        if(session==null){send(sender,"debug.no-running-game");return;}
        if(feature.equals("role-reshuffle")){
            if(accepted(sender,hooks.get().debugReshuffleRoles(session.id(),session.revision())))send(sender,"debug.role.reshuffled");
            return;
        }
        TeamRole role;
        try { role=TeamRole.valueOf(args[3].toUpperCase(Locale.ROOT)); }
        catch(IllegalArgumentException error){messages.send(sender,"debug.role.invalid",Map.of("role",args[3]));return;}
        Participant target=session.participants().stream().filter(player->player.name().equalsIgnoreCase(args[2])).findFirst().orElse(null);
        if(target==null){messages.send(sender,"debug.player.not-participant",Map.of("player",args[2]));return;}
        if(session.isEliminatedParticipant(target.id())){messages.send(sender,"debug.player.eliminated",Map.of("player",target.name()));return;}
        if(!accepted(sender,hooks.get().debugSetRole(session.id(),session.revision(),target.id(),role)))return;
        if(hidden.getAsBoolean())send(sender,"debug.role.set-hidden");
        else messages.send(sender,"debug.role.set",Map.of("player",target.name(),"role",messages.text(sender,"identity."+role.name().toLowerCase(Locale.ROOT))));
    }
    public List<String> tabComplete(CommandSender sender,String[] args) {
        if(!allowed(sender)||args.length<2)return List.of();
        if(args.length==2)return matching(FEATURES,args[1]);
        if(!args[1].equalsIgnoreCase("role-set"))return List.of();
        GameSession session=games.activeSession().filter(value->value.state()==SessionState.RUNNING).orElse(null);
        if(session==null)return List.of();
        if(args.length==3)return matching(session.participants().stream().map(Participant::name).toList(),args[2]);
        if(args.length==4)return matching(List.of("runner","hunter"),args[3]);
        return List.of();
    }
    private boolean accepted(CommandSender sender,OperationResult result) {
        if(result.successful())return true;
        send(sender,switch(result.status()){
            case BUSY->"command.busy";
            case CANCELLED->"debug.operation-cancelled";
            case PLAYER_UNAVAILABLE->"debug.player.unavailable";
            case STALE_SESSION,STALE_REVISION->"debug.operation-stale";
            case NO_ACTIVE_MATCH->"debug.no-running-game";
            default->"debug.operation-rejected";
        });return false;
    }
    private void send(CommandSender sender,String key){messages.send(sender,key,Map.of());}
    private static boolean allowed(CommandSender sender){return sender.hasPermission(MhuCommand.ADMIN_PERMISSION);}
    private static List<String> matching(List<String> values,String prefix){String normalized=prefix.toLowerCase(Locale.ROOT);return values.stream().filter(value->value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();}
}
