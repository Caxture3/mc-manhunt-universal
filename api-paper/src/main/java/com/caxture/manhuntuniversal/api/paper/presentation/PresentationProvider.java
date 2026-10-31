package com.caxture.manhuntuniversal.api.paper.presentation;
/** Fast synchronous renderer. Returning null preserves the current output. */
@FunctionalInterface public interface PresentationProvider { PresentationDecision present(PresentationRequest request); }
