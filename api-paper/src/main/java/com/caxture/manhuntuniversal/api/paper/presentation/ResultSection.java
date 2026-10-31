package com.caxture.manhuntuniversal.api.paper.presentation;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.function.Function;
public record ResultSection(String id, int order, boolean revealRoles, Function<PresentationRequest, List<Component>> content) {
    public ResultSection { Objects.requireNonNull(id); Objects.requireNonNull(content); }
}
