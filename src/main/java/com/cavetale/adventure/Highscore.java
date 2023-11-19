package com.cavetale.adventure;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@RequiredArgsConstructor
public final class Highscore {
    private final Adventure plugin;
    @Value
    final class Entry {
        String name;
        int score;
        long time;
    }
    protected List<Entry> list = null;

    public void init() {
        System.out.println("Setting up Adventure highscore");
        String sql =
            "CREATE TABLE IF NOT EXISTS `Adventure` ("
            + " `id` INT(11) NOT NULL AUTO_INCREMENT,"
            + " `player_uuid` VARCHAR(40) NOT NULL,"
            + " `player_name` VARCHAR(16) NOT NULL,"
            + " `map_id` VARCHAR(40) NOT NULL,"
            + " `start_time` DATETIME NOT NULL,"
            + " `end_time` DATETIME NOT NULL,"
            + " `score` INT(11) NOT NULL,"
            + " `finished` BOOLEAN NOT NULL,"
            + " PRIMARY KEY (`id`)"
            + ")";
        try {
            plugin.db.executeUpdate(sql);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void store(UUID playerUuid, String playerName, String mapID, Date startTime, Date endTime, int score, boolean finished) {
        String sql =
            "INSERT INTO `Adventure` ("
            + " `player_uuid`, `player_name`, `map_id`, `start_time`, `end_time`, `score`, `finished`"
            + ") VALUES ("
            + " ?, ?, ?, ?, ?, ?, ?"
            + ")";
        try (PreparedStatement update = plugin.db.getConnection().prepareStatement(sql)) {
            update.setString(1, playerUuid.toString());
            update.setString(2, playerName);
            update.setString(3, mapID);
            update.setTimestamp(4, new java.sql.Timestamp(startTime.getTime()));
            update.setTimestamp(5, new java.sql.Timestamp(endTime.getTime()));
            update.setInt(6, score);
            update.setBoolean(7, finished);
            update.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected List<Entry> list(String mapId) {
        if (list != null) return list;
        String sql =
            "SELECT * from Adventure WHERE map_id = ? AND finished = 1 ORDER BY score DESC, start_time ASC LIMIT 10";
        List<Entry> result = new ArrayList<>();
        try (PreparedStatement query = plugin.db.getConnection().prepareStatement(sql)) {
            query.setString(1, mapId);
            ResultSet row = query.executeQuery();
            while (row.next()) {
                String name = row.getString("player_name");
                int score = row.getInt("score");
                Date startTime = row.getTimestamp("start_time");
                Date endTime = row.getTimestamp("end_time");
                result.add(new Entry(name, score, endTime.getTime() - startTime.getTime()));
            }
            list = result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
