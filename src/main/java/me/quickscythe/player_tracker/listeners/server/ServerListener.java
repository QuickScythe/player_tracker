package me.quickscythe.player_tracker.listeners.server;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.plugin.SkinProvider;
import json2.JSONArray;
import json2.JSONObject;
import me.quickscythe.player_tracker.utils.SessionUtils;
import me.quickscythe.player_tracker.utils.Utils;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ServerListener implements ServerPlayConnectionEvents.Join, ServerPlayConnectionEvents.Disconnect, ServerLivingEntityEvents.AfterDeath {

    private static void createPlayerHead(final @NotNull BlueMapAPI blueMapAPI, final @NotNull UUID playerUUID, final @NotNull String assetName, final @NotNull BlueMapMap map) throws IOException {


    }

    @Override
    public void onPlayDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server) {

        JSONObject json = Utils.getData(handler.player.getUuid());
        json.put("statistics", getStats(handler.getPlayer()));
        JSONArray session_list = json.has("sessions") ? json.getJSONArray("sessions") : json.put("sessions", new JSONArray()).getJSONArray("sessions");
        JSONObject session_info = SessionUtils.getSessionInfo(handler.player.getUuid());
        session_info.put("time_left", new Date().getTime());
        session_info.put("playtime", session_info.getLong("time_left") - session_info.getLong("time_joined"));

        session_info.put("jumps", handler.player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.JUMP)) - session_info.getLong("jumps_start"));
        session_info.remove("jumps_start");

        session_list.put(session_info);
        json.put("sessions", session_list);

        Utils.save(handler.player, json);
        SessionUtils.clearSession(handler.player.getUuid());


        Utils.getMapAPI().getWorld(handler.player.getServerWorld()).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                Utils.getLoggerUtils().log(map.getWorld().getId());
                String name = handler.player.getName().getString();
                String uuid = handler.player.getUuid().toString();
                Vector3d loc = new Vector3d(handler.player.getX(), handler.getPlayer().getY() + 2, handler.getPlayer().getZ());
                POIMarker marker = new POIMarker(name, loc);
                LocalDate date = LocalDate.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy MM dd");
                String text = date.format(formatter);
                LocalDate parsedDate = LocalDate.parse(text, formatter);
                marker.setDetail(name + "\nLast seen: " + parsedDate);


                marker.setLabel(name);
                marker.setIcon("assets/players/" + uuid + ".png", 12, 12);
                map.getMarkerSets().get("offline_players").getMarkers().put(uuid, marker);
            }
        });

    }

    @Override
    public void onPlayReady(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        JSONObject session = SessionUtils.getSessionInfo(handler.player.getUuid());
        session.put("time_joined", new Date().getTime());
        session.put("jumps_start", handler.player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.JUMP)));

        generateIcon(handler.player.getUuid());
        Utils.getMapAPI().getWorld(handler.player.getServerWorld()).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                map.getMarkerSets().get("offline_players").remove(handler.player.getUuid().toString());
            }
        });
    }

    private void generateIcon(UUID uuid) {
        File player_asset = new File(Utils.getAssetsFolder() + "/" + uuid + ".png");


        try {
//            if (player_asset.exists() && Files.getLastModifiedTime(player_asset.toPath()).toMillis() > new Date().getTime() - TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS))
//                return;
            final SkinProvider skinProvider = Utils.getMapAPI().getPlugin().getSkinProvider();
            try {
                final Optional<BufferedImage> oImgSkin = skinProvider.load(uuid);
                if (oImgSkin.isEmpty()) {
                    throw new IOException(uuid + " doesn't have a skin");
                }

                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(player_asset))) {
                    final BufferedImage img = Utils.getMapAPI().getPlugin().getPlayerMarkerIconFactory().apply(uuid, oImgSkin.get());
                    int width = img.getWidth();
                    int height = img.getHeight();
                    int[] pixels = img.getRGB(0, 0, width, height, null, 0, width);

                    for (int i = 0; i < pixels.length; i++) {
                        int p = pixels[i];
                        int a = (p >> 24) & 0xff;
                        int r = (p >> 16) & 0xff;
                        int g = (p >> 8) & 0xff;
                        int b = p & 0xff;

                        int avg = (r + g + b) / 3;
                        p = (a << 24) | (avg << 16) | (avg << 8) | avg;
                        pixels[i] = p;
                    }

                    img.setRGB(0, 0, width, height, pixels, 0, width);
                    ImageIO.write(img, "png", out);
                } catch (IOException e) {
                    throw new IOException("Failed to write " + uuid + "'s head to asset-storage", e);
                }
            } catch (IOException e) {
                throw new IOException("Failed to load skin for player " + uuid, e);
            }
//
//            URL url = new URI("https://api.mineatar.io/face/" + uuid + "?scale=3").toURL();
//            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//            conn.setRequestProperty("User-Agent", "PlayerTracker/1.0(+https://maps.vanillaflux.com/; <quickscythe@gmail.com>)");
//            Utils.getLoggerUtils().log("Generating User Avatar....");
//            conn.setRequestMethod("GET");
//            Utils.getLoggerUtils().log("  - Connection Code: " + conn.getResponseCode());
//            Utils.getLoggerUtils().log("  - Connection Message: " + conn.getResponseMessage());

//            InputStream in = url.openConnection().getInputStream();
//            OutputStream out = new BufferedOutputStream(new FileOutputStream(player_asset));
//            BufferedImage img = ImageIO.read(in);
//            BufferedImage image = Utils.getMapAPI().getPlugin().getPlayerMarkerIconFactory().apply(UUID.fromString(uuid), r);


//            ImageIO.write(img, "png", player_asset);

//            in.close();
//            out.close();


        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }


    }

    @Override
    public void afterDeath(LivingEntity entity, DamageSource damageSource) {
        if (entity instanceof PlayerEntity) {
            JSONObject session = SessionUtils.getSessionInfo(entity.getUuid());
            session.put("deaths", session.has("deaths") ? session.getInt("deaths") + 1 : 1);
            JSONObject killed_by = SessionUtils.getJson("killed_by", session);
            SessionUtils.addInt(damageSource.getType().toString(), 1, killed_by);


        }
        if (damageSource.getAttacker() instanceof PlayerEntity) {
            JSONObject session = SessionUtils.getSessionInfo(damageSource.getAttacker().getUuid());
            JSONObject kills = SessionUtils.getJson("kills", session);

            SessionUtils.addInt(entity.getType().getName().toString().equalsIgnoreCase(entity.getName().toString()) ? entity.getName().toString() : entity.getType().getName().toString() + ":" + entity.getName().toString(), 1, kills);

        }

    }

    private JSONObject getStats(ServerPlayerEntity player) {
        JSONObject stats = new JSONObject("{}");
        List<Identifier> ids = new ArrayList<>();
        ServerStatHandler handler = player.getStatHandler();

        ids.add(Stats.LEAVE_GAME);
        ids.add(Stats.PLAY_TIME);
        ids.add(Stats.BELL_RING);
        ids.add(Stats.EAT_CAKE_SLICE);
        ids.add(Stats.SNEAK_TIME);
        ids.add(Stats.TOTAL_WORLD_TIME);
        ids.add(Stats.ANIMALS_BRED);
        ids.add(Stats.CLEAN_ARMOR);
        ids.add(Stats.CLEAN_BANNER);
        ids.add(Stats.CLEAN_SHULKER_BOX);
        ids.add(Stats.DEATHS);
        ids.add(Stats.DROP);
        ids.add(Stats.ENCHANT_ITEM);
        ids.add(Stats.FILL_CAULDRON);
        ids.add(Stats.FISH_CAUGHT);
        ids.add(Stats.JUMP);
        ids.add(Stats.MOB_KILLS);
        ids.add(Stats.OPEN_BARREL);
        ids.add(Stats.OPEN_CHEST);
        ids.add(Stats.OPEN_ENDERCHEST);
        ids.add(Stats.OPEN_SHULKER_BOX);
        ids.add(Stats.PLAY_NOTEBLOCK);
        ids.add(Stats.PLAY_RECORD);
        ids.add(Stats.PLAYER_KILLS);
        ids.add(Stats.POT_FLOWER);
        ids.add(Stats.RAID_TRIGGER);
        ids.add(Stats.RAID_WIN);
        ids.add(Stats.SLEEP_IN_BED);
        ids.add(Stats.TALKED_TO_VILLAGER);
        ids.add(Stats.TARGET_HIT);
        ids.add(Stats.TIME_SINCE_DEATH);
        ids.add(Stats.TIME_SINCE_REST);
        ids.add(Stats.TRADED_WITH_VILLAGER);
        ids.add(Stats.TRIGGER_TRAPPED_CHEST);
        ids.add(Stats.TUNE_NOTEBLOCK);
        ids.add(Stats.USE_CAULDRON);

        List<Identifier> distances = new ArrayList<>();
        distances.add(Stats.WALK_ONE_CM);
        distances.add(Stats.BOAT_ONE_CM);
        distances.add(Stats.AVIATE_ONE_CM);
        distances.add(Stats.CLIMB_ONE_CM);
        distances.add(Stats.FALL_ONE_CM);
        distances.add(Stats.CROUCH_ONE_CM);
        distances.add(Stats.HORSE_ONE_CM);
        distances.add(Stats.SPRINT_ONE_CM);
        distances.add(Stats.CROUCH_ONE_CM);
        distances.add(Stats.PIG_ONE_CM);
        distances.add(Stats.STRIDER_ONE_CM);
        distances.add(Stats.MINECART_ONE_CM);
        distances.add(Stats.SWIM_ONE_CM);
        distances.add(Stats.WALK_ON_WATER_ONE_CM);
        distances.add(Stats.WALK_UNDER_WATER_ONE_CM);


        List<Identifier> damages = new ArrayList<>();
        damages.add(Stats.DAMAGE_ABSORBED);
        damages.add(Stats.DAMAGE_DEALT);
        damages.add(Stats.DAMAGE_BLOCKED_BY_SHIELD);
        damages.add(Stats.DAMAGE_RESISTED);
        damages.add(Stats.DAMAGE_TAKEN);
        damages.add(Stats.DAMAGE_DEALT_ABSORBED);
        damages.add(Stats.DAMAGE_DEALT_RESISTED);


        stats.put("distance", new JSONObject());
        for (Identifier id : distances) {
            if (handler.getStat(Stats.CUSTOM.getOrCreateStat(id)) != 0)
                stats.getJSONObject("distance").put(id.getPath(), handler.getStat(Stats.CUSTOM.getOrCreateStat(id)));
        }

        stats.put("damage", new JSONObject());
        for (Identifier id : damages) {
            if (handler.getStat(Stats.CUSTOM.getOrCreateStat(id)) != 0)
                stats.getJSONObject("damage").put(id.getPath(), handler.getStat(Stats.CUSTOM.getOrCreateStat(id)));
        }

        stats.put("broken", new JSONObject());

        for (Stat stat : Stats.BROKEN) {
            if (handler.getStat(stat) != 0)
                stats.getJSONObject("broken").put(stat.getValue().toString(), handler.getStat(stat));
        }


        stats.put("dropped", new JSONObject());
        for (Stat stat : Stats.DROPPED) {
            if (handler.getStat(stat) != 0)
                stats.getJSONObject("dropped").put(stat.getValue().toString(), handler.getStat(stat));
        }

        stats.put("crafted", new JSONObject());
        for (Stat stat : Stats.CRAFTED) {
            if (handler.getStat(stat) != 0)
                stats.getJSONObject("crafted").put(stat.getValue().toString(), handler.getStat(stat));
        }

        stats.put("killed", new JSONObject());
        for (Stat stat : Stats.KILLED) {
            if (handler.getStat(stat) != 0)
                stats.getJSONObject("killed").put(((EntityType) stat.getValue()).getLootTableId().getValue().toString(), handler.getStat(stat));
        }

        stats.put("killed_by", new JSONObject());
        for (Stat stat : Stats.KILLED_BY) {
            if (handler.getStat(stat) != 0)
                stats.getJSONObject("killed_by").put(stat.getValue().toString(), handler.getStat(stat));
        }

        stats.put("mined", new JSONObject());
        for (Stat stat : Stats.MINED) {
            if (handler.getStat(stat) != 0)
                stats.getJSONObject("mined").put(((Block) stat.getValue()).getLootTableKey().getValue().getPath(), handler.getStat(stat));
        }

        stats.put("picked_up", new JSONObject());
        for (Stat stat : Stats.PICKED_UP) {
            if (handler.getStat(stat) != 0)
                stats.getJSONObject("picked_up").put(stat.getValue().toString(), handler.getStat(stat));
        }

        stats.put("used", new JSONObject());
        for (Stat stat : Stats.USED) {
            if (handler.getStat(stat) != 0)
                stats.getJSONObject("used").put(stat.getValue().toString(), handler.getStat(stat));
        }

        stats.put("crafted", new JSONObject());
        for (Stat stat : Stats.CRAFTED) {
            if (handler.getStat(stat) != 0)
                stats.getJSONObject("crafted").put(stat.getValue().toString(), handler.getStat(stat));
        }

        for (Identifier id : ids) {
            if (handler.getStat(Stats.CUSTOM.getOrCreateStat(id)) != 0)
                stats.put(id.getPath(), handler.getStat(Stats.CUSTOM.getOrCreateStat(id)));
        }
        return stats;
    }


}
