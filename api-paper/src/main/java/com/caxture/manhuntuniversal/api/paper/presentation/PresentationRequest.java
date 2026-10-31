package com.caxture.manhuntuniversal.api.paper.presentation;
import com.caxture.manhuntuniversal.api.*;
import org.bukkit.command.CommandSender;
import java.util.*;
/** Live receiver plus detached data; only invoke on the main thread. */
public record PresentationRequest(String id, PresentationChannel channel, CommandSender recipient, PresentationPayload payload,
                                  Optional<MatchSnapshot> match, Optional<ResultSnapshot> result, boolean roleSensitive, boolean privateToRecipient) {
    public PresentationRequest { Objects.requireNonNull(id); Objects.requireNonNull(channel); Objects.requireNonNull(recipient);
        Objects.requireNonNull(payload); Objects.requireNonNull(match); Objects.requireNonNull(result); }
    public PresentationRequest(String id,PresentationChannel channel,CommandSender recipient,PresentationPayload payload,
                               Optional<MatchSnapshot> match,Optional<ResultSnapshot> result,boolean sensitive) {
        this(id,channel,recipient,payload,match,result,sensitive,false);
    }
}
