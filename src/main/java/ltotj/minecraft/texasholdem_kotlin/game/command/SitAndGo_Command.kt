package ltotj.minecraft.texasholdem_kotlin.game.command

import ltotj.minecraft.texasholdem_kotlin.Main
import ltotj.minecraft.texasholdem_kotlin.MySQLManager
import ltotj.minecraft.texasholdem_kotlin.game.SitAndGo
import ltotj.minecraft.texasholdem_kotlin.rating.RatingRepository
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

object SitAndGo_Command : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        when (args.getOrNull(0)) {
            "start" -> handleStart(sender, args)
            "join" -> handleJoin(sender, args)
            "leave" -> handleLeave(sender)
            "rating" -> handleRating(sender, args)
            "top" -> handleTop(sender)
            "help" -> handleHelp(sender)
            else -> handleHelp(sender)
        }
        return true
    }
    
    // /sng start <buyIn>
    private fun handleStart(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§cプレイヤーのみ実行可能です")
            return
        }
        
        val buyIn = args.getOrNull(1)?.toLongOrNull()
        if (buyIn == null) {
            sender.sendMessage("§c/sng start <バイイン金額>")
            return
        }
        
        // 最低バイインチェック
        val minBuyIn = Main.con.getInt("sitandgo.minBuyIn")
        if (buyIn < minBuyIn) {
            sender.sendMessage("§c最低バイインは ${minBuyIn} です")
            return
        }
        
        // 所持金チェック
        if (!Main.vault.has(sender.uniqueId, buyIn.toDouble())) {
            sender.sendMessage("§c所持金が不足しています")
            return
        }
        
        // 既にテーブルに参加している場合
        if (Main.currentPlayers.containsKey(sender.uniqueId)) {
            sender.sendMessage("§c既にゲームに参加しています")
            return
        }
        
        // バイイン徴収
        Main.vault.withdraw(sender.uniqueId, buyIn.toDouble())
        
        // テーブル作成
        val table = SitAndGo(sender, buyIn)
        Main.sitAndGoTables[sender.uniqueId] = table
        Main.currentPlayers[sender.uniqueId] = sender.uniqueId
        
        sender.sendMessage("§aSit & Go トーナメントを作成しました")
        sender.sendMessage("§7バイイン: §e${buyIn}")
        sender.sendMessage("§7/sng join ${sender.name} で参加できます")
        
        // TODO: 4人揃ったらゲーム開始
    }
    
    // /sng join <host>
    private fun handleJoin(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§cプレイヤーのみ実行可能です")
            return
        }
        
        val hostName = args.getOrNull(1)
        if (hostName == null) {
            sender.sendMessage("§c/sng join <ホスト名>")
            return
        }
        
        val host = Bukkit.getPlayer(hostName)
        if (host == null) {
            sender.sendMessage("§cプレイヤーが見つかりません")
            return
        }
        
        val table = Main.sitAndGoTables[host.uniqueId]
        if (table == null) {
            sender.sendMessage("§c${hostName}のテーブルが見つかりません")
            return
        }
        
        if (table.phase != SitAndGo.TournamentPhase.WAITING) {
            sender.sendMessage("§c既にゲームが開始されています")
            return
        }
        
        // 所持金チェック
        if (!Main.vault.has(sender.uniqueId, table.buyIn.toDouble())) {
            sender.sendMessage("§c所持金が不足しています（必要: ${table.buyIn}）")
            return
        }
        
        // バイイン徴収
        Main.vault.withdraw(sender.uniqueId, table.buyIn.toDouble())
        
        // テーブルに参加
        Main.currentPlayers[sender.uniqueId] = host.uniqueId
        
        sender.sendMessage("§a${hostName}のテーブルに参加しました")
        
        // TODO: playerListに追加、4人揃ったら開始
    }
    
    // /sng leave
    private fun handleLeave(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage("§cプレイヤーのみ実行可能です")
            return
        }
        
        val masterUUID = Main.currentPlayers[sender.uniqueId]
        if (masterUUID == null) {
            sender.sendMessage("§cゲームに参加していません")
            return
        }
        
        val table = Main.sitAndGoTables[masterUUID]
        if (table == null || table.phase != SitAndGo.TournamentPhase.WAITING) {
            sender.sendMessage("§c離脱できません（ゲーム進行中）")
            return
        }
        
        // バイイン返金
        Main.vault.deposit(sender.uniqueId, table.buyIn.toDouble())
        
        // テーブルから離脱
        Main.currentPlayers.remove(sender.uniqueId)
        
        sender.sendMessage("§7テーブルから離脱しました")
        
        // TODO: playerListから削除、ホストの場合はテーブル削除
    }
    
    // /sng rating [player]
    private fun handleRating(sender: CommandSender, args: Array<out String>) {
        val targetName = args.getOrNull(1) ?: if (sender is Player) sender.name else null
        if (targetName == null) {
            sender.sendMessage("§c/sng rating [プレイヤー]")
            return
        }
        
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            sender.sendMessage("§cプレイヤーが見つかりません")
            return
        }
        
        val mysql = MySQLManager(Main.plugin, "SitAndGo_RatingQuery")
        val ratingRepo = RatingRepository(mysql)
        val rating = ratingRepo.getRating(target.uniqueId)
        val displayRating = ratingRepo.getDisplayRating(rating)
        
        sender.sendMessage("§6=== §e${targetName} のレーティング §6===")
        sender.sendMessage("§7レート: §b${displayRating}")
    }
    
    // /sng top
    private fun handleTop(sender: CommandSender) {
        val mysql = MySQLManager(Main.plugin, "SitAndGo_TopQuery")
        val ratingRepo = RatingRepository(mysql)
        val topRatings = ratingRepo.getTopRatings(10)
        
        sender.sendMessage("§6======= §eSit & Go ランキング §6=======")
        for ((index, entry) in topRatings.withIndex()) {
            val rank = index + 1
            val medal = when (rank) {
                1 -> "§6§l🏆"
                2 -> "§f§l🥈"
                3 -> "§e§l🥉"
                else -> "§7$rank位"
            }
            val displayRating = ratingRepo.getDisplayRating(entry.rating)
            sender.sendMessage("$medal §f${entry.name} §7- §b${displayRating} §8(${entry.gamesPlayed}戦 ${entry.wins}勝)")
        }
    }
    
    // /sng help
    private fun handleHelp(sender: CommandSender) {
        sender.sendMessage(listOf(
            "§6=== Sit & Go コマンド ===",
            "§e/sng start <金額> §7- トーナメント開始",
            "§e/sng join <ホスト名> §7- 参加",
            "§e/sng leave §7- 離脱（募集中のみ）",
            "§e/sng rating [プレイヤー] §7- レート確認",
            "§e/sng top §7- ランキング"
        ))
    }
    
    override fun onTabComplete(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("start", "join", "leave", "rating", "top", "help")
                .filter { it.startsWith(args[0], true) }
            2 -> when (args[0].lowercase()) {
                "join" -> getActiveHosts()
                "rating" -> Bukkit.getOnlinePlayers().map { it.name }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
    
    private fun getActiveHosts(): List<String> {
        return Main.sitAndGoTables.entries
            .filter { it.value.phase == SitAndGo.TournamentPhase.WAITING }
            .mapNotNull { Bukkit.getPlayer(it.key)?.name }
    }
}
