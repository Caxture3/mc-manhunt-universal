package com.caxture.manhuntuniversal.command;

import com.caxture.manhuntuniversal.api.*;
import com.caxture.manhuntuniversal.extension.GameExtensionHooks;
import com.caxture.manhuntuniversal.game.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DebugCommandHandlerTest {
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
    private final GameManager games=new GameManager(new RoleAssigner(new Random(1)));
    private final Set<String> permissions=new HashSet<>(Set.of("manhuntuniversal.admin"));
    private final List<String> keys=new ArrayList<>();
    private final List<Map<String,String>> placeholders=new ArrayList<>();
    private final CommandSender sender=(CommandSender)Proxy.newProxyInstance(CommandSender.class.getClassLoader(),new Class[]{CommandSender.class},(proxy,method,args)->switch(method.getName()){
        case "hasPermission"->permissions.contains((String)args[0]);
        case "isOp"->false;
        default->throw new AssertionError(method.getName());
    });
    private boolean busy,hidden;
    private int sets,reshuffles;
    private UUID target;
    private TeamRole role;
    private OperationStatus outcome=OperationStatus.OK;
    private final GameExtensionHooks hooks=new GameExtensionHooks(){
        @Override public boolean mutationActive(){return busy;}
        @Override public OperationResult debugSetRole(UUID session,long revision,UUID id,TeamRole next){sets++;target=id;role=next;assertEquals(games.activeSession().orElseThrow().id(),session);assertEquals(games.activeSession().orElseThrow().revision(),revision);return result();}
        @Override public OperationResult debugReshuffleRoles(UUID session,long revision){reshuffles++;return result();}
        private OperationResult result(){return new OperationResult(outcome,"test",Optional.empty());}
    };
    private final DebugCommandHandler commands=new DebugCommandHandler(games,()->hooks,()->hidden,(receiver,key,args)->{assertSame(sender,receiver);keys.add(key);placeholders.add(args);});
    private GameSession start(boolean running){games.teamSelections().choose(a,TeamPreference.RUNNER,1);var session=games.start(List.of(new Participant(a,"Alice"),new Participant(b,"Bob"),new Participant(c,"Offline")),new MatchSettings(1,false,new TrackingSettingsSnapshot(512,30,4096)),Instant.EPOCH).session().orElseThrow();if(running)games.activate(session.id(),Instant.EPOCH);return session;}
    private String last(){return keys.getLast();}
    @Test void onlyTheNewPermissionIsAcceptedAndItControlsCompletion(){
        start(true);permissions.clear();permissions.add("randomanhunt.admin");commands.handle(sender,new String[]{"debug","role-reshuffle"});assertEquals("command.no-permission",last());assertEquals(0,reshuffles);assertTrue(commands.tabComplete(sender,new String[]{"debug",""}).isEmpty());
        permissions.add("manhuntuniversal.admin");commands.handle(sender,new String[]{"debug","role-reshuffle"});assertEquals(1,reshuffles);assertEquals("debug.role.reshuffled",last());
    }
    @Test void metadataRegistersOnlyTheNewNode() throws Exception {
        assertEquals("manhuntuniversal.admin",MhuCommand.ADMIN_PERMISSION);
        try(var input=getClass().getClassLoader().getResourceAsStream("plugin.yml")){
            assertNotNull(input);var yaml=new YamlConfiguration();yaml.options().pathSeparator('/');yaml.load(new InputStreamReader(input,StandardCharsets.UTF_8));
            assertEquals(Set.of("manhuntuniversal.admin"),yaml.getConfigurationSection("permissions").getKeys(false));assertEquals("op",yaml.getString("permissions/manhuntuniversal.admin/default"));
        }
    }
    @Test void syntaxUnknownFeaturesAndIdleOrCountdownGamesCannotMutateRoles(){
        commands.handle(sender,new String[]{"debug"});assertEquals("debug.usage",last());
        commands.handle(sender,new String[]{"debug","role-runner-set","Alice"});assertEquals("debug.unknown-feature",last());
        commands.handle(sender,new String[]{"debug","role-reshuffle"});assertEquals("debug.no-running-game",last());
        start(false);commands.handle(sender,new String[]{"debug","role-set","Alice","runner"});assertEquals("debug.no-running-game",last());
        assertEquals(0,sets);assertEquals(0,reshuffles);
    }
    @Test void roleSetUsesFixedRosterNamesCaseInsensitivelyWithoutAnOnlinePlayerLookup(){
        start(true);commands.handle(sender,new String[]{"debug","ROLE-SET","oFfLiNe","RuNnEr"});
        assertEquals(c,target);assertEquals(TeamRole.RUNNER,role);assertEquals("debug.role.set",last());assertEquals("Offline",placeholders.getLast().get("player"));
        commands.handle(sender,new String[]{"debug","role-set","Alice","hunter"});assertEquals(a,target);assertEquals(TeamRole.HUNTER,role);
    }
    @Test void invalidArityRoleParticipantAndEliminatedPlayerAreRejected(){
        var session=start(true);commands.handle(sender,new String[]{"debug","role-set","Alice"});assertEquals("debug.usage",last());
        commands.handle(sender,new String[]{"debug","role-set","Alice","random"});assertEquals("debug.role.invalid",last());
        commands.handle(sender,new String[]{"debug","role-set","Stranger","runner"});assertEquals("debug.player.not-participant",last());
        games.eliminateParticipant(session.id(),c,Optional.empty(),Instant.EPOCH);commands.handle(sender,new String[]{"debug","role-set","Offline","runner"});assertEquals("debug.player.eliminated",last());
        commands.handle(sender,new String[]{"debug","role-reshuffle","extra"});assertEquals("debug.usage",last());assertEquals(0,sets);assertEquals(0,reshuffles);
    }
    @Test void hiddenFeedbackDoesNotIncludeTheNewRoleOrPlayer(){
        start(true);hidden=true;commands.handle(sender,new String[]{"debug","role-set","Offline","runner"});assertEquals("debug.role.set-hidden",last());assertTrue(placeholders.getLast().isEmpty());
    }
    @Test void busyCancelledUnavailableAndStaleOperationsUseLocalizedFeedback(){
        start(true);busy=true;commands.handle(sender,new String[]{"debug","role-reshuffle"});assertEquals("command.busy",last());assertEquals(0,reshuffles);busy=false;
        for(var entry:Map.of(OperationStatus.CANCELLED,"debug.operation-cancelled",OperationStatus.PLAYER_UNAVAILABLE,"debug.player.unavailable",OperationStatus.STALE_REVISION,"debug.operation-stale",OperationStatus.INVALID_ASSIGNMENT,"debug.operation-rejected").entrySet()){
            outcome=entry.getKey();commands.handle(sender,new String[]{"debug","role-reshuffle"});assertEquals(entry.getValue(),last());
        }
    }
    @Test void completionIncludesOnlyTheTwoFeaturesRosterPlayersAndRoleNames(){
        assertEquals(List.of("role-reshuffle","role-set"),commands.tabComplete(sender,new String[]{"debug","role-"}));
        assertTrue(commands.tabComplete(sender,new String[]{"debug","role-set",""}).isEmpty());start(true);
        assertEquals(List.of("Offline"),commands.tabComplete(sender,new String[]{"debug","role-set","OF"}));
        assertEquals(List.of("runner","hunter"),commands.tabComplete(sender,new String[]{"debug","role-set","Offline",""}));
        assertTrue(commands.tabComplete(sender,new String[]{"debug","role-reshuffle",""}).isEmpty());
    }
}
