package com.caxture.manhuntuniversal.fabric.ui;

/** Built-in cards always show Chinese above English, independent of the client locale. */
public record SelectorTargetLabels(String primary, String secondary) {
    public static SelectorTargetLabels resolve(String targetId, String primary, String secondary) {
        return switch (targetId) {
            case "core:village" -> new SelectorTargetLabels("村庄", "Village");
            case "core:nether_fortress" -> new SelectorTargetLabels("下界堡垒", "Nether Fortress");
            case "core:bastion_remnant" -> new SelectorTargetLabels("堡垒遗迹", "Bastion Remnant");
            case "core:trial_chambers" -> new SelectorTargetLabels("试炼密室", "Trial Chambers");
            default -> new SelectorTargetLabels(primary, secondary);
        };
    }
}
