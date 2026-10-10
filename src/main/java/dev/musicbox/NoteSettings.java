package dev.musicbox;

public record NoteSettings(int instrument, int note) {
    public static final int MIN_NOTE = 48;
    public static final int MAX_NOTE = 78;
    public static final NoteSettings DEFAULT = new NoteSettings(Instrument.VANILLA_AUTO.ordinal(), 66);
    public NoteSettings {
        if (!Instrument.valid(instrument))
            throw new IllegalArgumentException("Invalid music box settings");
        Instrument voice = Instrument.values()[instrument];
        if (note < voice.minNote() || note > voice.maxNote())
            throw new IllegalArgumentException("Invalid music box settings");
    }
    public float pitch() {
        Instrument voice = voice();
        if (voice.isVanilla() && !voice.vanillaInstrument().isTunable()) return 1.0f;
        return (float) Math.pow(2.0, (note - (voice.isVanilla() ? 66 : 60)) / 12.0);
    }
    public Instrument voice() { return Instrument.values()[instrument]; }
}
