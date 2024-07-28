package me.quickscythe.player_tracker;

import me.quickscythe.player_tracker.commands.DiscordCommandManager;
import me.quickscythe.player_tracker.listeners.server.ServerListener;
import me.quickscythe.player_tracker.utils.UID;
import me.quickscythe.player_tracker.utils.Utils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;


public class PlayerTracker implements ModInitializer {

    @Override
    public void onInitialize() {
        Utils.init(this);
        ServerPlayConnectionEvents.JOIN.register(new ServerListener());
        ServerPlayConnectionEvents.DISCONNECT.register(new ServerListener());
        ServerLivingEntityEvents.AFTER_DEATH.register(new ServerListener());
        CommandRegistrationCallback.EVENT.register(new DiscordCommandManager());
    }
}
