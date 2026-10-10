package dev.musicbox.client;

import dev.musicbox.Instrument;
import dev.musicbox.network.SettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.Arrays;
import java.util.Comparator;

/** A compact in-world editor; changes remain a draft until Save is pressed. */
public final class MusicBoxScreen extends Screen {
    private static final int GOLD = 0xFFE6B96D;
    private static final int LIGHT = 0xFFFFE2A3;
    private static final String[] NOTES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final int PAGE_SIZE = 8;
    private static final Instrument[] MENU = Arrays.stream(Instrument.values())
            .sorted(Comparator.comparingInt(voice -> voice.isAutomatic() ? 0 : voice.isVanilla() ? 1 : 2))
            .toArray(Instrument[]::new);
    private final BlockPos pos;
    private int instrument;
    private int note;
    private int left, top, panelWidth, menuTop, menuIndex, menuPage, menuWidth;
    private boolean menuOpen;
    private long sentAt;
    private Button selector, save, preview, cancel, previousPage, nextPage;
    private PitchSlider slider;
    private final Button[] options = new Button[PAGE_SIZE];

    public MusicBoxScreen(BlockPos pos, int instrument, int note) {
        super(Component.translatable("screen.musicbox.title"));
        this.pos = pos.immutable();
        this.instrument = Math.clamp(instrument, 0, Instrument.values().length - 1);
        this.note = Math.clamp(note, voice().minNote(), voice().maxNote());
    }

    @Override
    protected void init() {
        panelWidth = Math.min(460, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(30, height / 2 - 20);
        int selectorWidth = Math.min(108, panelWidth / 3);
        selector = addRenderableWidget(new BrassButton(left + 8, top + 8, selectorWidth, 24,
                selectedLabel(), button -> toggleMenu()));
        slider = addRenderableWidget(new PitchSlider(left + selectorWidth + 16, top + 8,
                panelWidth - selectorWidth - 24));
        int buttonWidth = Math.min(94, (panelWidth - 32) / 3);
        int start = left + (panelWidth - (buttonWidth * 3 + 12)) / 2;
        preview = addRenderableWidget(new BrassButton(start, top + 58, buttonWidth, 20,
                Component.translatable("screen.musicbox.preview"), button -> preview()));
        save = addRenderableWidget(new BrassButton(start + buttonWidth + 6, top + 58, buttonWidth, 20,
                Component.translatable("screen.musicbox.save"), button -> save()));
        cancel = addRenderableWidget(new BrassButton(start + (buttonWidth + 6) * 2, top + 58, buttonWidth, 20,
                Component.translatable("gui.cancel"), button -> onClose()));
        menuWidth = Math.min(184, panelWidth - 16);
        menuTop = Math.clamp(top + 34, 6, Math.max(6, height - (PAGE_SIZE + 1) * 18 - 6));
        for (int i = 0; i < options.length; i++) {
            final int slot = i;
            options[i] = addRenderableWidget(new BrassButton(left + 8, menuTop + i * 18,
                    menuWidth, 18, Component.empty(), button -> select(menuPage * PAGE_SIZE + slot)));
            options[i].visible = false;
        }
        previousPage = addRenderableWidget(new BrassButton(left + 8, menuTop + PAGE_SIZE * 18,
                menuWidth / 2, 18, Component.empty(), button -> changePage(-1)));
        nextPage = addRenderableWidget(new BrassButton(left + 8 + menuWidth / 2, menuTop + PAGE_SIZE * 18,
                menuWidth - menuWidth / 2, 18, Component.empty(), button -> changePage(1)));
        menuOpen = false;
        updateMenu();
        refreshActive();
        setInitialFocus(slider);
    }

    private Component selectedLabel() {
        return Component.empty().append(Instrument.values()[instrument].label()).append("  \u25be");
    }

    private Instrument voice() { return Instrument.values()[instrument]; }
    private double pitchPosition() { return (note - voice().minNote()) / (double) (voice().maxNote() - voice().minNote()); }
    private int pageCount() { return (MENU.length + PAGE_SIZE - 1) / PAGE_SIZE; }

    private void updateMenu() {
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = menuPage * PAGE_SIZE + slot;
            options[slot].visible = menuOpen && index < MENU.length;
            if (index < MENU.length) options[slot].setMessage(MENU[index].label());
        }
        previousPage.visible = nextPage.visible = menuOpen;
        previousPage.active = menuPage > 0;
        nextPage.active = menuPage + 1 < pageCount();
        previousPage.setMessage(Component.translatable("screen.musicbox.previous_page"));
        nextPage.setMessage(Component.translatable("screen.musicbox.next_page", menuPage + 1, pageCount()));
    }

    private void changePage(int delta) {
        menuPage = Math.clamp(menuPage + delta, 0, pageCount() - 1);
        menuIndex = menuPage * PAGE_SIZE;
        updateMenu();
        setFocused(options[0]);
    }

    private void toggleMenu() {
        menuOpen = !menuOpen;
        menuIndex = Arrays.asList(MENU).indexOf(voice());
        menuPage = menuIndex / PAGE_SIZE;
        selector.visible = !menuOpen;
        updateMenu();
        refreshActive();
        setFocused(menuOpen ? options[menuIndex % PAGE_SIZE] : selector);
    }

    private void select(int index) {
        double position = pitchPosition();
        instrument = MENU[index].ordinal();
        note = voice().minNote() + (int) Math.round(position * (voice().maxNote() - voice().minNote()));
        slider.syncFromNote();
        selector.setMessage(selectedLabel());
        toggleMenu();
        preview();
    }

    private void refreshActive() {
        boolean editable = !menuOpen && sentAt == 0;
        slider.active = preview.active = save.active = editable;
        selector.active = sentAt == 0;
        cancel.active = !menuOpen;
    }

    private void preview() {
        if (ClientPlayNetworking.canSend(SettingsPayload.TYPE))
            ClientPlayNetworking.send(new SettingsPayload(pos, instrument, note, true));
    }

    private void save() {
        if (!ClientPlayNetworking.canSend(SettingsPayload.TYPE)) return;
        ClientPlayNetworking.send(new SettingsPayload(pos, instrument, note, false));
        sentAt = System.currentTimeMillis();
        save.setMessage(Component.translatable("screen.musicbox.saving"));
        refreshActive();
    }

    public void acknowledge(BlockPos savedPos, boolean success) {
        if (sentAt == 0 || !pos.equals(savedPos)) return;
        if (success) { onClose(); return; }
        sentAt = 0;
        save.setMessage(Component.translatable("screen.musicbox.retry"));
        refreshActive();
    }

    @Override
    public void tick() {
        if (minecraft.level == null || minecraft.player == null) { onClose(); return; }
        if (sentAt != 0 && System.currentTimeMillis() - sentAt > 4000) {
            sentAt = 0;
            save.setMessage(Component.translatable("screen.musicbox.retry"));
            refreshActive();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (menuOpen) {
            if (event.key() == 256) { toggleMenu(); return true; }
            if (event.key() == 264 || event.key() == 265) {
                menuIndex = Math.floorMod(menuIndex + (event.key() == 264 ? 1 : -1), MENU.length);
                menuPage = menuIndex / PAGE_SIZE;
                updateMenu();
                setFocused(options[menuIndex % PAGE_SIZE]);
                return true;
            }
            if (event.key() == 266 || event.key() == 267) { changePage(event.key() == 267 ? 1 : -1); return true; }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menuOpen && !(event.x() >= left + 8 && event.x() < options[0].getRight()
                && event.y() >= menuTop && event.y() < menuTop + (PAGE_SIZE + 1) * 18)) {
            toggleMenu();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (menuOpen) { if (vertical != 0) changePage(vertical < 0 ? 1 : -1); return true; }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x55000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(font, title, width / 2, top - 18, LIGHT);
        brass(graphics, left, top, panelWidth, 42, false);
        graphics.centeredText(font, Component.translatable("screen.musicbox.hint"), width / 2, top + 44, 0xFFEADBC5);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }

    private static String noteName(int midi) { return NOTES[midi % 12] + (midi / 12 - 1); }

    private static void brass(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean focused) {
        g.fill(x, y, x + w, y + h, 0xF025180F);
        g.outline(x, y, w, h, focused ? LIGHT : GOLD);
        g.outline(x + 2, y + 2, w - 4, h - 4, 0xFF6D4928);
        g.horizontalLine(x + 3, x + w - 4, y + 3, 0xFFAA773B);
    }

    private final class BrassButton extends Button {
        BrassButton(int x, int y, int w, int h, Component label, OnPress action) {
            super(x, y, w, h, label, action, DEFAULT_NARRATION);
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            if (menuOpen && this == options[0]) g.nextStratum();
            brass(g, getX(), getY(), width, height, isHoveredOrFocused());
            g.centeredText(font, font.plainSubstrByWidth(getMessage().getString(), width - 8), getX() + width / 2,
                    getY() + (height - 8) / 2, active ? LIGHT : 0xFF8E806B);
            if (isHoveredOrFocused() && font.width(getMessage()) > width - 8)
                g.setTooltipForNextFrame(getMessage(), mx, my);
        }
    }

    private final class PitchSlider extends AbstractSliderButton {
        PitchSlider(int x, int y, int width) {
            super(x, y, width, 24, Component.empty(), pitchPosition());
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(voice().isVanilla()
                    ? Component.translatable("screen.musicbox.vanilla_pitch", note - voice().minNote())
                    : Component.translatable("screen.musicbox.pitch", noteName(note), note));
        }
        void syncFromNote() { value = pitchPosition(); updateMessage(); }
        @Override protected void applyValue() {
            note = voice().minNote() + (int) Math.round(value * (voice().maxNote() - voice().minNote()));
            value = pitchPosition();
            updateMessage();
        }
        @Override public boolean keyPressed(KeyEvent event) {
            if (isFocused() && active && (event.key() == 263 || event.key() == 262)) {
                setValue(value + (event.key() == 262 ? 1 : -1) / (double) (voice().maxNote() - voice().minNote()));
                preview();
                return true;
            }
            return super.keyPressed(event);
        }
        @Override public void onRelease(MouseButtonEvent event) { super.onRelease(event); preview(); }
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            brass(g, getX(), getY(), width, height, isHoveredOrFocused());
            for (int i = 0; i <= 24; i++) {
                int x = getX() + 5 + (width - 10) * i / 24;
                g.verticalLine(x, getY() + 4, getY() + (i % 12 == 0 ? 19 : 10), 0xFF775230);
            }
            int handleX = getX() + 1 + (int) Math.round(value * (width - 8));
            g.fill(handleX, getY() + 2, handleX + 6, getY() + height - 2, GOLD);
            g.centeredText(font, getMessage(), getX() + width / 2, getY() + 8, 0xFFFFFFFF);
        }
    }
}
