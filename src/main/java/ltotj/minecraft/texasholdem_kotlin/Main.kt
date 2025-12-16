package ltotj.minecraft.texasholdem_kotlin

import ltotj.minecraft.texasholdem_kotlin.game.SitAndGo
import ltotj.minecraft.texasholdem_kotlin.game.TexasHoldem
import ltotj.minecraft.texasholdem_kotlin.game.command.AllinORFold_Command
import ltotj.minecraft.texasholdem_kotlin.game.command.SitAndGo_Command
import ltotj.minecraft.texasholdem_kotlin.game.command.TexasHoldem_Command
import ltotj.minecraft.texasholdem_kotlin.game.event.AllinORFold_Event
import ltotj.minecraft.texasholdem_kotlin.game.event.SitAndGo_Event
import ltotj.minecraft.texasholdem_kotlin.game.event.TexasHoldem_Event
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.HashMap

class Main : JavaPlugin() {

    companion object{

        lateinit var con: Config
        lateinit var texasHoldemTables:HashMap<UUID, TexasHoldem>
        lateinit var sitAndGoTables:HashMap<UUID, SitAndGo>
        lateinit var currentPlayers:HashMap<UUID, UUID>
        lateinit var plugin: JavaPlugin
        lateinit var playable:AtomicBoolean
        lateinit var vault: VaultManager
        val executor=Executors.newCachedThreadPool()
        var pluginTitle="TexasHoldem"


        fun getPlData(player: Player): TexasHoldem.PlayerData?{
            if(!currentPlayers.containsKey(player.uniqueId))return null
            return getTable(player)?.getPlData(player.uniqueId)
        }

        fun getTable(player: Player): TexasHoldem? {
            if(!currentPlayers.containsKey(player.uniqueId))return null
            return texasHoldemTables[currentPlayers[player.uniqueId]]
        }

    }

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()
        con=Config(this)
        texasHoldemTables=HashMap()
        sitAndGoTables=HashMap()
        currentPlayers=HashMap()
        plugin =this
        playable=AtomicBoolean()
        playable.set(config.getBoolean("canPlay"))
        vault = VaultManager(this)
        server.pluginManager.registerEvents(TexasHoldem_Event,this)
        server.pluginManager.registerEvents(SitAndGo_Event,this)
        getCommand("poker")!!.setExecutor(TexasHoldem_Command)
        getCommand("sng")!!.setExecutor(SitAndGo_Command)

        executor.execute {
            val mysql=MySQLManager(this,"TexasHoldem_onEnable")
            mysql.execute("create table if not exists handsLog\n" +
                    "(\n" +
                    "    id int unsigned auto_increment,\n" +
                    "    gameId int unsigned,\n" +
                    "    P1card varchar(16) null,\n" +
                    "    P2card varchar(16) null,\n" +
                    "    P3card varchar(16) null,\n" +
                    "    P4card varchar(16) null,\n" +
                    "    community varchar(32) null,\n" +
                    "    foldP varchar(20) null,\n" +
                    "\n" +
                    "    primary key(id)\n" +
                    ");")
            
            // Sit and Go rating table
            mysql.execute("CREATE TABLE IF NOT EXISTS sitandgo_rating (\n" +
                    "    id INT UNSIGNED AUTO_INCREMENT,\n" +
                    "    uuid VARCHAR(36) UNIQUE NOT NULL,\n" +
                    "    name VARCHAR(16) NULL,\n" +
                    "    rating_internal INT DEFAULT 2500,\n" +
                    "    games_played INT DEFAULT 0,\n" +
                    "    wins INT DEFAULT 0,\n" +
                    "    second_place INT DEFAULT 0,\n" +
                    "    third_place INT DEFAULT 0,\n" +
                    "    fourth_place INT DEFAULT 0,\n" +
                    "    total_prize BIGINT DEFAULT 0,\n" +
                    "    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,\n" +
                    "    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                    "    PRIMARY KEY(id)\n" +
                    ");")
            mysql.execute("CREATE INDEX IF NOT EXISTS sitandgo_rating_uuid_index ON sitandgo_rating(uuid);")
            mysql.execute("CREATE INDEX IF NOT EXISTS sitandgo_rating_rating_index ON sitandgo_rating(rating_internal DESC);")
            
            // Sit and Go log table
            mysql.execute("CREATE TABLE IF NOT EXISTS sitandgo_log (\n" +
                    "    id INT UNSIGNED AUTO_INCREMENT,\n" +
                    "    start_time DATETIME,\n" +
                    "    end_time DATETIME,\n" +
                    "    buy_in BIGINT,\n" +
                    "    multiplier DOUBLE,\n" +
                    "    total_prize BIGINT,\n" +
                    "    p1_uuid VARCHAR(36), p1_name VARCHAR(16), p1_rank INT, p1_prize BIGINT, p1_rating_before INT, p1_rating_after INT,\n" +
                    "    p2_uuid VARCHAR(36), p2_name VARCHAR(16), p2_rank INT, p2_prize BIGINT, p2_rating_before INT, p2_rating_after INT,\n" +
                    "    p3_uuid VARCHAR(36), p3_name VARCHAR(16), p3_rank INT, p3_prize BIGINT, p3_rating_before INT, p3_rating_after INT,\n" +
                    "    p4_uuid VARCHAR(36), p4_name VARCHAR(16), p4_rank INT, p4_prize BIGINT, p4_rating_before INT, p4_rating_after INT,\n" +
                    "    PRIMARY KEY(id)\n" +
                    ");")
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}