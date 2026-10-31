package compatibility;
import com.caxture.manhuntuniversal.api.*;
import java.util.Set;
public final class LegacyApiConsumer implements ManhuntUniversalApi {
    public String apiVersion() { return "legacy"; }
    public Set<Capability> capabilities() { return Set.of(Capability.MATCH_QUERY); }
    public MatchQuery matches() { return java.util.Optional::empty; }
    public MatchController control() { return null; }
    public RoleController roles() { return null; }
    public ParticipantController participants() { return null; }
    public static String probe() {
        ExtensionOptions options = new ExtensionOptions("fixture:legacy", Set.of());
        return options.id() + ":" + new LegacyApiConsumer().matches().current().isEmpty();
    }
}
