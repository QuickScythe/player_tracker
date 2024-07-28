package me.quickscythe.player_tracker.utils;

import json2.JSONException;
import json2.JSONObject;
import me.quickscythe.player_tracker.PlayerTracker;
import me.quickscythe.player_tracker.utils.logger.LoggerUtils;
import me.quickscythe.player_tracker.utils.sql.SqlDatabase;
import me.quickscythe.player_tracker.utils.sql.SqlUtils;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.commons.lang3.StringEscapeUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;

public class Utils {
    private static SqlDatabase core;
    private static LoggerUtils loggerUtils;
    private static PlayerTracker mod;

    public static void init(PlayerTracker mod) {
        Utils.mod = mod;
        loggerUtils = new LoggerUtils();

        loggerUtils.log("Starting PlayerTracker");

        SqlUtils.createDatabase("core", new SqlDatabase(SqlUtils.SQLDriver.MYSQL, "sql.vanillaflux.com", "vanillaflux", 3306, "sys", "9gGKGqthQJ&!#DGd"));
        core = SqlUtils.getDatabase("core");

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
                String sql = "INSERT INTO users(uuid,username,discord_id,last_seen,json) VALUES ('" + player.getUuid() + "','" + player.getName().toString().substring(8, player.getName().toString().length() - 1) + "','null','" + new Date().getTime() + "','" + StringEscapeUtils.escapeJava(json.toString()) + "');";
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
            while (rs.next())
                return new JSONObject(rs.getString("json"));
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