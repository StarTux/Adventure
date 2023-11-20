package com.cavetale.adventure;

import com.winthier.sql.SQLRow.NotNull;
import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data @NotNull
public final class SQLMapFinish implements SQLRow {
    @Id private Integer id;
    private UUID player;
    @Keyed @VarChar(255) private String mapPath;
    private Date startTime;
    private Date endTime;
    private long duration;
    private int score;
    private boolean finished;

    public SQLMapFinish() { }

    public SQLMapFinish(final UUID player, final String mapPath, final Date startTime, final Date endTime, final int score, final boolean finished) {
        this.player = player;
        this.mapPath = mapPath;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = endTime.getTime() - startTime.getTime();
        this.score = score;
        this.finished = finished;
    }

    public static void insert(final UUID player, final String mapPath, final Date startTime, final Date endTime, final int score, final boolean finished) {
        final SQLMapFinish row = new SQLMapFinish(player, mapPath, startTime, endTime, score, finished);
        AdventurePlugin.plugin().getDatabase().insert(row);
    }

    public static List<SQLMapFinish> listForMap(final String mapPath) {
        return AdventurePlugin.plugin().getDatabase().find(SQLMapFinish.class)
            .eq("mapPath", mapPath)
            .eq("finished", true)
            .orderByDescending("duration")
            .findList();
    }
}
