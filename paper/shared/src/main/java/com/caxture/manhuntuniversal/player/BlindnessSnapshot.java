package com.caxture.manhuntuniversal.player;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public record BlindnessSnapshot(
        int duration,
        int amplifier,
        boolean ambient,
        boolean particles,
        boolean icon
) {
    public static BlindnessSnapshot from(PotionEffect effect) {
        return new BlindnessSnapshot(
                effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon());
    }

    public PotionEffect toPotionEffect() {
        return new PotionEffect(PotionEffectType.BLINDNESS, duration, amplifier, ambient, particles, icon);
    }
}
