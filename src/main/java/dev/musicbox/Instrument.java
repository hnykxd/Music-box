package dev.musicbox;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

public enum Instrument {
    PIANO("piano"), ACCORDION("accordion"), RECORDER("recorder"), TRUMPET("trumpet"),
    SUONA("suona"), VIOLIN("violin"), SAXOPHONE("saxophone"), DRUMS("drums"),
    // Keep the first eight ordinals stable: existing world data stores them.
    VANILLA_AUTO("vanilla_auto", NoteBlockInstrument.HARP),
    VANILLA_HARP("vanilla_harp", NoteBlockInstrument.HARP),
    VANILLA_BASEDRUM("vanilla_basedrum", NoteBlockInstrument.BASEDRUM),
    VANILLA_SNARE("vanilla_snare", NoteBlockInstrument.SNARE),
    VANILLA_HAT("vanilla_hat", NoteBlockInstrument.HAT),
    VANILLA_BASS("vanilla_bass", NoteBlockInstrument.BASS),
    VANILLA_FLUTE("vanilla_flute", NoteBlockInstrument.FLUTE),
    VANILLA_BELL("vanilla_bell", NoteBlockInstrument.BELL),
    VANILLA_GUITAR("vanilla_guitar", NoteBlockInstrument.GUITAR),
    VANILLA_CHIME("vanilla_chime", NoteBlockInstrument.CHIME),
    VANILLA_XYLOPHONE("vanilla_xylophone", NoteBlockInstrument.XYLOPHONE),
    VANILLA_IRON_XYLOPHONE("vanilla_iron_xylophone", NoteBlockInstrument.IRON_XYLOPHONE),
    VANILLA_COW_BELL("vanilla_cow_bell", NoteBlockInstrument.COW_BELL),
    VANILLA_DIDGERIDOO("vanilla_didgeridoo", NoteBlockInstrument.DIDGERIDOO),
    VANILLA_BIT("vanilla_bit", NoteBlockInstrument.BIT),
    VANILLA_BANJO("vanilla_banjo", NoteBlockInstrument.BANJO),
    VANILLA_PLING("vanilla_pling", NoteBlockInstrument.PLING),
    VANILLA_TRUMPET("vanilla_trumpet", NoteBlockInstrument.TRUMPET),
    VANILLA_TRUMPET_EXPOSED("vanilla_trumpet_exposed", NoteBlockInstrument.TRUMPET_EXPOSED),
    VANILLA_TRUMPET_OXIDIZED("vanilla_trumpet_oxidized", NoteBlockInstrument.TRUMPET_OXIDIZED),
    VANILLA_TRUMPET_WEATHERED("vanilla_trumpet_weathered", NoteBlockInstrument.TRUMPET_WEATHERED),
    VANILLA_ZOMBIE("vanilla_zombie", NoteBlockInstrument.ZOMBIE),
    VANILLA_SKELETON("vanilla_skeleton", NoteBlockInstrument.SKELETON),
    VANILLA_CREEPER("vanilla_creeper", NoteBlockInstrument.CREEPER),
    VANILLA_DRAGON("vanilla_dragon", NoteBlockInstrument.DRAGON),
    VANILLA_WITHER_SKELETON("vanilla_wither_skeleton", NoteBlockInstrument.WITHER_SKELETON),
    VANILLA_PIGLIN("vanilla_piglin", NoteBlockInstrument.PIGLIN),
    VANILLA_CUSTOM_HEAD("vanilla_custom_head", NoteBlockInstrument.CUSTOM_HEAD);

    private final String id;
    private final SoundEvent sound;
    private final NoteBlockInstrument vanilla;
    Instrument(String id) {
        this.id = id;
        this.sound = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("musicbox", id));
        this.vanilla = null;
    }
    Instrument(String id, NoteBlockInstrument vanilla) {
        this.id = id;
        this.vanilla = vanilla;
        this.sound = null;
    }
    public String id() { return id; }
    /** AUTO and CUSTOM_HEAD must be resolved against the block by the caller. */
    public SoundEvent sound() { return vanilla == null ? sound : vanilla.getSoundEvent().value(); }
    public boolean isAutomatic() { return this == VANILLA_AUTO; }
    public boolean isVanilla() { return vanilla != null; }
    /** Null for the eight additional voices; HARP is AUTO's fallback. */
    public NoteBlockInstrument vanillaInstrument() { return vanilla; }
    public int minNote() { return isVanilla() ? 54 : 48; }
    public int maxNote() { return isVanilla() ? 78 : 72; }
    public Component label() { return Component.translatable("instrument.musicbox." + id); }
    public static boolean valid(int index) { return index >= 0 && index < values().length; }
}
