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
            "open" -> handleOpen(sender)
            "join" -> handleJoin(sender, args)
            "leave" -> handleLeave(sender)
            "stop" -> handleStop(sender, args)
            "rating" -> handleRating(sender, args)
            "top" -> handleTop(sender)
            "debug" -> handleDebug(sender, args)
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
        if (Main.vault.getBalance(sender.uniqueId) < buyIn) {
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
        
        // ホスト自身を参加させる
        table.addSitAndGoPlayer(sender)
        
        sender.sendMessage("§aSit & Go トーナメントを作成しました")
        sender.sendMessage("§7バイイン: §e${buyIn}")
        sender.sendMessage("§7/sng join ${sender.name} で参加できます")
        
        // 募集メッセージをブロードキャスト
        Bukkit.broadcast(net.kyori.adventure.text.Component.text("§6§l[SitAndGo] §e${sender.name} §aがバイイン §e${buyIn} §aでトーナメントを募集中！ §7(1/4)"))
        Bukkit.broadcast(net.kyori.adventure.text.Component.text("§7/sng join ${sender.name} で参加"))
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
        if (Main.vault.getBalance(sender.uniqueId) < table.buyIn) {
            sender.sendMessage("§c所持金が不足しています（必要: ${table.buyIn}）")
            return
        }
        
        // バイイン徴収
        Main.vault.withdraw(sender.uniqueId, table.buyIn.toDouble())
        
        // テーブルに参加
        if (!table.addSitAndGoPlayer(sender)) {
            // 参加失敗時は返金
            Main.vault.deposit(sender.uniqueId, table.buyIn.toDouble())
            sender.sendMessage("§cテーブルに参加できませんでした")
            return
        }
        
        sender.sendMessage("§a${hostName}のテーブルに参加しました")
        
        // 参加人数をブロードキャスト
        val count = table.getPlayerCount()
        Bukkit.broadcast(net.kyori.adventure.text.Component.text("§6§l[SitAndGo] §e${sender.name} §aが参加！ §7(${count}/4)"))
        
        if (count == 4) {
            Bukkit.broadcast(net.kyori.adventure.text.Component.text("§6§l[SitAndGo] §a4人揃いました！ルーレット開始！"))
        }
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
        table.removeSitAndGoPlayer(sender)
        Main.currentPlayers.remove(sender.uniqueId)
        
        sender.sendMessage("§7テーブルから離脱しました")
    }
    
    // /sng stop [host]
    private fun handleStop(sender: CommandSender, args: Array<out String>) {
        if (!sender.isOp) {
            sender.sendMessage("§cOP専用コマンドです")
            return
        }
        
        var targetHost: Player? = null
        
        if (args.size >= 2) {
            // 指定したホストのゲームを停止
            targetHost = Bukkit.getPlayer(args[1])
            if (targetHost == null) {
                sender.sendMessage("§cプレイヤーが見つかりません")
                return
            }
        } else if (sender is Player) {
            // 自分が参加している・主催しているゲームを停止
            val hostUUID = Main.currentPlayers[sender.uniqueId]
            if (hostUUID != null) {
                targetHost = Bukkit.getPlayer(hostUUID)
            }
        }
        
        if (targetHost == null) {
            sender.sendMessage("§c停止対象のゲームが見つかりません。/sng stop [ホスト名] で指定してください。")
            return
        }
        
        val table = Main.sitAndGoTables[targetHost.uniqueId]
        if (table == null) {
            sender.sendMessage("§c${targetHost.name} はSitAndGoを主催していません")
            return
        }
        
        sender.sendMessage("§c${targetHost.name} のゲームを強制終了します...")
        table.cancelTournament()
        Main.sitAndGoTables.remove(targetHost.uniqueId)
    }
    
    // /sng open
    private fun handleOpen(sender: CommandSender) {
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
        if (table == null) {
            sender.sendMessage("§c参加中のテーブルが見つかりません")
            return
        }
        
        table.openSitAndGoInv(sender)
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
            val rankNum = index + 1
            val medal = when (rankNum) {
                1 -> "§6§l🏆"
                2 -> "§f§l🥈"
                3 -> "§e§l🥉"
                else -> "§7${rankNum}位"
            }
            val displayRating = ratingRepo.getDisplayRating(entry.rating)
            sender.sendMessage("$medal §f${entry.name} §7- §b${displayRating} §8(${entry.gamesPlayed}戦 ${entry.wins}勝)")
        }
    }
    
    // /sng help
    private fun handleHelp(sender: CommandSender) {
        sender.sendMessage("§6=== Sit & Go コマンド ===")
        sender.sendMessage("§e/sng open <金額> §7- トーナメント作成（startも可）")
        sender.sendMessage("§e/sng join <ホスト名> §7- 参加")
        sender.sendMessage("§e/sng leave §7- 離脱（募集中のみ）")
        sender.sendMessage("§e/sng rating [プレイヤー] §7- レート確認")
        sender.sendMessage("§e/sng top §7- ランキング")
        if (sender.isOp) {
            sender.sendMessage("§e/sng stop [ホスト] §7- 強制終了（OP）")
            sender.sendMessage("§e/sng debug [倍率] [バイイン] §7- デバッグモード（OP）")
        }
    }
    
    // /sng debug [multiplier] [buyIn]
private fun handleDebug(sender: CommandSender, args: Array<out String>) {
    if (sender !is Player) {
        sender.sendMessage("§cプレイヤーのみ実行可能です")
        return
    }
    
    if (!sender.isOp) {
        sender.sendMessage("§cこのコマンドはOP専用です")
        return
    }
    
    // 既にテーブルに参加している場合
    if (Main.currentPlayers.containsKey(sender.uniqueId)) {
        sender.sendMessage("§c既にゲームに参加しています")
        return
    }
    
    // バイイン指定（デフォルトは1000）
    val buyIn = args.getOrNull(2)?.toLongOrNull() ?: 1000L
    
    // 所持金チェック
    if (Main.vault.getBalance(sender.uniqueId) < buyIn) {
        sender.sendMessage("§c所持金が不足しています（必要: ${buyIn}）")
        return
    }
    
    // バイイン徴収
    Main.vault.withdraw(sender.uniqueId, buyIn.toDouble())
    
    // テーブル作成
    val table = SitAndGo(sender, buyIn)
    Main.sitAndGoTables[sender.uniqueId] = table
    
    // 倍率指定（省略時はランダム）
    val multiplier = args.getOrNull(1)?.toDoubleOrNull()
    if (multiplier != null) {
        table.multiplier = multiplier
        sender.sendMessage("§aデバッグモード: 倍率 ${multiplier}x, バイイン ${buyIn}円")
    } else {
        sender.sendMessage("§aデバッグモード: ランダム倍率, バイイン ${buyIn}円")
    }
    
    // ホスト自身を参加させる
    table.addSitAndGoPlayer(sender)
    
    // ダミープレイヤー3人追加
    table.addDebugBots(3)
    
    sender.sendMessage("§7ダミープレイヤー3人を追加しました")
    sender.sendMessage("§7使用方法: /sng debug [倍率] [バイイン（円）]")
}    }
    
    override fun onTabComplete(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> {
                val commands = mutableListOf("start", "open", "join", "leave", "rating", "top", "help")
                if (sender.isOp) {
                    commands.add("debug")
                    commands.add("stop")
                }
                commands.filter { it.startsWith(args[0], true) }
            }
            2 -> when (args[0].lowercase()) {
                "join" -> getActiveHosts()
                "rating" -> Bukkit.getOnlinePlayers().map { it.name }
                "debug" -> listOf("2.5", "3.0", "4.0", "5.0", "6.0", "8.0", "10.0", "15.0", "20.0")
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
