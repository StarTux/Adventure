package com.winthier.minigames.adventure;

import com.avaje.ebean.SqlQuery;
import com.avaje.ebean.SqlRow;
import com.avaje.ebean.SqlUpdate;
import com.winthier.minigames.MinigamesPlugin;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.Value;

public class Highscore {
    @Value class Entry{ String name; int score; long time; }
    List<Entry> list = null;

    public void init() {
        System.out.println("Setting up Adventure highscore");
        String sql =
            "CREATE TABLE IF NOT EXISTS `Adventure` (" +
            " `id` INT(11) NOT NULL AUTO_INCREMENT," +
            " `player_uuid` VARCHAR(40) NOT NULL," +
            " `player_name` VARCHAR(16) NOT NULL," +
            " `map_id` VARCHAR(40) NOT NULL," +
            " `start_time` DATETIME NOT NULL," +
            " `end_time` DATETIME NOT NULL," +
            " `score` INT(11) NOT NULL," +
            " `finished` BOOLEAN NOT NULL," +
            " PRIMARY KEY (`id`)" +
            ")";
        try {
            MinigamesPlugin.getInstance().getDatabase().createSqlUpdate(sql).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void store(UUID playerUuid, String playerName, String mapID, Date startTime, Date endTime, int score, boolean finished) {
        String sql =
            "INSERT INTO `Adventure` (" +
            " `player_uuid`, `player_name`, `map_id`, `start_time`, `end_time`, `score`, `finished`" +
            ") VALUES (" +
            " :playerUuid, :playerName, :mapID, :startTime, :endTime, :score, :finished" +
            ")";
        try {
            SqlUpdate update = MinigamesPlugin.getInstance().getDatabase().createSqlUpdate(sql);
            update.setParameter("playerUuid", playerUuid);
            update.setParameter("playerName", playerName);
            update.setParameter("mapID", mapID);
            update.setParameter("startTime", startTime);
            update.setParameter("endTime", endTime);
            update.setParameter("score", score);
            update.setParameter("finished", finished);
            update.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    List<Entry> list(String mapId)
    {
        if (list != null) return list;
        String sql =
            "SELECT * from Adventure WHERE map_id = :mapId AND finished = 1 ORDER BY score DESC, start_time ASC LIMIT 10";
        SqlQuery query = MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql);
        query.setParameter("mapId", mapId);
        List<Entry> result = new ArrayList<>();
        for (SqlRow row : query.findList()) {
            String name = row.getString("player_name");
            int score = row.getInteger("score");
            Date startTime = row.getDate("start_time");
            Date endTime = row.getDate("end_time");
            result.add(new Entry(name, score, endTime.getTime() - startTime.getTime()));
        }
        list = result;
        return result;
    }
}
