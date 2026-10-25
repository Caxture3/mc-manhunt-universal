package com.caxture.manhuntuniversal.presentation;

import org.bukkit.entity.Player;

record PresentationSoundCue(String soundKey, float volume, float pitch) {
    static PresentationSoundCue countdown() {
        return new PresentationSoundCue("ui.button.click", 1.0F, 1.0F);
    }

    static PresentationSoundCue roleReveal() {
        return new PresentationSoundCue("block.bell.use", 1.0F, 1.0F);
    }

    void play(Player player) {
        player.playSound(player.getLocation(), soundKey, volume, pitch);
    }
}
