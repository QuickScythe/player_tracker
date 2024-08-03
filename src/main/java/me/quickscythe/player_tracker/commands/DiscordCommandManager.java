package me.quickscythe.player_tracker.commands;

import com.mojang.brigadier.CommandDispatcher;
import me.quickscythe.player_tracker.utils.UID;
import me.quickscythe.player_tracker.utils.sql.SqlDatabase;
import me.quickscythe.player_tracker.utils.sql.SqlUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

import static net.minecraft.server.command.CommandManager.literal;

public class DiscordCommandManager implements CommandRegistrationCallback {
    @Override
    public void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("discord").executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            SqlDatabase core = SqlUtils.getDatabase("core");
            ResultSet rs = core.query("SELECT * FROM users WHERE UUID='" + player.getUuid() + "';");

            try {

                while (rs.next()) {
                    Text message;
                    String key;
                    if(rs.getString("discord_key").equals("null")){
                        key = "dc-" + new UID(5);
                        core.update("UPDATE users SET discord_key='" + key + "' WHERE UUID='" + player.getUuid() + "';");
                    } else {
                        key = rs.getString("discord_key");
                    }
                    String dcCmd = "!linkdiscord " + key;
                    message = Text.translatable("commands.discord.key", key).setStyle(Style.EMPTY
                            .withClickEvent(
                                    new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, dcCmd))
                            .withHoverEvent(
                                    new HoverEvent(
                                            HoverEvent.Action.SHOW_TEXT, Text.literal("§aClick to copy: §f" + dcCmd))
                            ));
                    context.getSource().sendFeedback(() -> message, false);
                }
            } catch (SQLException e) {
                context.getSource().sendFeedback(()->Text.literal("§cThat is a player only command."), false);
                throw new RuntimeException(e);
            }


            return 1;
        }));

    }
}
