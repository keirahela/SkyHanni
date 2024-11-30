package at.hannibal2.skyhanni.config.features.rift.area.dreadfarm;

import at.hannibal2.skyhanni.config.FeatureToggle;
import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

public class WiltedBerberisConfig {

    @Expose
    @ConfigOption(name = "Enabled", desc = "Show Wilted Berberis helper.")
    @ConfigEditorBoolean
    @FeatureToggle
    public boolean enabled = true;

    @Expose
    @ConfigOption(name = "Berberis Helper Range", desc = "Determines in which range around you can you see the berberis. (lower this if auto farm goes to the other plots)")
    @ConfigEditorSlider(minValue = 5, maxValue = 30, minStep = 1)
    public double AuraRange = 15;

    @Expose
    @ConfigOption(name = "Only on Farmland", desc = "Only show the helper while standing on Farmland blocks.")
    @ConfigEditorBoolean
    public boolean onlyOnFarmland = false;

    @Expose
    @ConfigOption(name = "Hide Particles", desc = "Hide the Wilted Berberis particles.")
    @ConfigEditorBoolean
    public boolean hideParticles = false;

    @Expose
    @ConfigOption(name = "Mute Others Sounds", desc = "Mute nearby Wilted Berberis sounds while not holding a Wand of Farming or not standing on Farmland blocks.")
    @ConfigEditorBoolean
    public boolean muteOthersSounds = true;

    @Expose
    @ConfigOption(name = "Auto Farm", desc = "Automatically moves to berberis and collects it.")
    @ConfigEditorBoolean
    public boolean autoFarmEnabled = false;

    @Expose
    @ConfigOption(name = "Auto Farm Speed", desc = "Sets the walkspeed of your player while using autofarm.")
    @ConfigEditorSlider(minValue = 0.05f, maxValue = 0.5f, minStep = 0.05f)
    public double autoFarmWalkSpeed = 0.25;

    @Expose
    @ConfigOption(name = "Break Delay", desc = "Sets how many milliseconds it waits after breaking a berberi.")
    @ConfigEditorSlider(minValue = 0f, maxValue = 1000f, minStep = 50f)
    public double breakDelay = 500;
}
