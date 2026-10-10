package dev.musicbox.client;

import dev.musicbox.network.OpenPayload;
import dev.musicbox.network.SavedPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MusicBoxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().gui.setScreen(
                        new MusicBoxScreen(payload.pos(), payload.instrument(), payload.note()))));
        ClientPlayNetworking.registerGlobalReceiver(SavedPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().gui.screen() instanceof MusicBoxScreen screen)
                        screen.acknowledge(payload.pos(), payload.success());
                }));
    }
}
