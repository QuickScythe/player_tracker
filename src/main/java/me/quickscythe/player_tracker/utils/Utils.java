package me.quickscythe.player_tracker.utils;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.gson.MarkerGson;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import json2.JSONException;
import json2.JSONObject;
import me.quickscythe.player_tracker.PlayerTracker;
import me.quickscythe.player_tracker.utils.logger.LoggerUtils;
import me.quickscythe.player_tracker.utils.sql.SqlDatabase;
import me.quickscythe.player_tracker.utils.sql.SqlUtils;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;

public class Utils {
    private static SqlDatabase core;
    private static LoggerUtils loggerUtils;
    private static PlayerTracker mod;
    private static BlueMapAPI mapAPI = null;
    private static File assetsFolder = new File("bluemap/web/assets/players");
    private static File configFolder;
    private static File mapsFolder;


    public static void init(PlayerTracker mod) {
        Utils.mod = mod;
        configFolder = new File("config/" + mod.NAME);

        mapsFolder = new File(configFolder + "/maps");


        loggerUtils = new LoggerUtils();
        loggerUtils.log("Starting PlayerTracker");
        getMapAPI();


        SqlUtils.createDatabase("core", new SqlDatabase(SqlUtils.SQLDriver.MYSQL, "sql.vanillaflux.com", "vanillaflux", 3306, "sys", "9gGKGqthQJ&!#DGd"));
        core = SqlUtils.getDatabase("core");

        if (!assetsFolder.exists()) loggerUtils.log("Creating assets folder: " + assetsFolder.mkdir());
        if (!configFolder.exists()) loggerUtils.log("Creating assets folder: " + configFolder.mkdir());
        if (!mapsFolder.exists()) loggerUtils.log("Creating assets folder: " + mapsFolder.mkdir());


    }

    public static File getAssetsFolder() {
        return assetsFolder;
    }

    public static BlueMapAPI getMapAPI() {
        if (mapAPI == null) {
            BlueMapAPI.onEnable(api -> {
                Utils.mapAPI = api;
                Utils.getLoggerUtils().log("Registering BlueMapAPI");

                Utils.getLoggerUtils().log("Checking for existing maps");
                for (File file : mapsFolder.listFiles()) {
                    String contents = getFileContents(file);
                    api.getMap(file.getName()).get().getMarkerSets().put("offline_players", MarkerGson.INSTANCE.fromJson(contents, MarkerSet.class));
                }

                for (BlueMapMap map : getMapAPI().getMaps()) {
                    if (map.getMarkerSets().get("offline_players") == null) {
                        MarkerSet offlinePlayers = MarkerSet.builder().defaultHidden(false).label("Offline Players").build();
                        map.getMarkerSets().put("offline_players", offlinePlayers);
                    }
                }
            });

            BlueMapAPI.onDisable(api -> {
                for (BlueMapMap map : getMapAPI().getMaps()) {
                    JSONObject json = new JSONObject(MarkerGson.INSTANCE.toJson(map.getMarkerSets().get("offline_players")));
                    System.out.println(map.getId() + ": " + json);
                    File file = new File(mapsFolder + "/" + map.getId());
                    try {
                        if (!file.exists()) file.createNewFile();

                        PrintWriter writer = new PrintWriter(file);
                        writer.println(json);
                        writer.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                }
            });
        }
        return mapAPI;
    }

    public static String getFileContents(File file) {
        try {
            if (!file.exists()) throw new RuntimeException("File does not exist (" + file.getName() + ")");

            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            reader.close();

            return stringBuilder.toString();
        } catch (IOException ex) {
            throw new RuntimeException("File couldn't be accessed. (" + file.getName() + ")");
        }
    }


    public static LoggerUtils getLoggerUtils() {
        return loggerUtils;
    }

    public static PlayerTracker getMod() {
        return mod;
    }


    public static void save(ServerPlayerEntity player, JSONObject json) {
        ResultSet rs = core.query("SELECT * FROM users WHERE uuid='" + player.getUuid() + "';");
        try {
            if (rs.next()) {
                loggerUtils.log("Record exists. Updating.");
                JSONObject orig = new JSONObject(rs.getString("json"));
                String sql = "UPDATE users SET username=\"" + player.getName().toString().substring(8, player.getName().toString().length() - 1) + "\",last_seen=\"" + new Date().getTime() + "\",json=\"" + StringEscapeUtils.escapeJava(json.toString()) + "\" WHERE uuid=\"" + player.getUuid() + "\";";
                core.update(sql);
            } else {

                loggerUtils.log("No record. Creating.");
                String sql = "INSERT INTO users(uuid,username,discord_key,discord_id,password,last_seen,json) VALUES ('" + player.getUuid() + "','" + player.getName().toString().substring(8, player.getName().toString().length() - 1) + "','null','null','null','" + new Date().getTime() + "','" + StringEscapeUtils.escapeJava(json.toString()) + "');";
                loggerUtils.log(sql);
                core.input(sql);
            }
            rs.close();
        } catch (SQLException e) {
            throw new RuntimeException("There was an error saving " + player.getName() + " data.");
        }

    }

    public static JSONObject getData(UUID uid) {
        ResultSet rs = core.query("SELECT json FROM users WHERE UUID='" + uid.toString() + "';");
        try {
            while (rs.next()) return new JSONObject(rs.getString("json"));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return new JSONObject();
    }

    private static JSONObject deepMerge(JSONObject source, JSONObject target) throws JSONException {
        for (String key : JSONObject.getNames(source)) {
            Object value = source.get(key);
            if (!target.has(key)) {
                // new value for "key":
                target.put(key, value);
            } else {
                // existing value for "key" - recursively deep merge:
                if (value instanceof JSONObject) {
                    JSONObject valueJson = (JSONObject) value;
                    deepMerge(valueJson, target.getJSONObject(key));
                } else {
                    target.put(key, value);
                }
            }
        }
        return target;
    }

    //config files
}