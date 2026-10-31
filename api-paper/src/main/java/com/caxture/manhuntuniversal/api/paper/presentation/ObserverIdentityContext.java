package com.caxture.manhuntuniversal.api.paper.presentation;
import com.caxture.manhuntuniversal.api.MatchSnapshot;
import org.bukkit.entity.Player;
import java.util.*;
/** Main-thread observer-specific context. Match is absent when identity privacy redacts it. */
public record ObserverIdentityContext(Player observer, UUID targetId, String realName, Optional<MatchSnapshot> match, ObserverIdentityStyle fallback) { }
