package ltotj.minecraft.texasholdem_kotlin.game

import ltotj.minecraft.texasholdem_kotlin.Main.Companion.con
import ltotj.minecraft.texasholdem_kotlin.Main.Companion.vault
import ltotj.minecraft.texasholdem_kotlin.MySQLManager
import ltotj.minecraft.texasholdem_kotlin.Utility.createGUIItem
import ltotj.minecraft.texasholdem_kotlin.game.utility.RouletteDisplay
import ltotj.minecraft.texasholdem_kotlin.rating.RatingRepository
import ltotj.minecraft.texasholdem_kotlin.rating.SitAndGoRating
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.math.pow
import kotlin.random.Random

class SitAndGo(
    masterPlayer: Player,
    val buyIn: Long
) : TexasHoldem(masterPlayer, 4, 4, 1) {
    
    // ======== フェーズ管理 ========
    enum class TournamentPhase {
        WAITING,      // 募集中（4人待ち）
        ROULETTE,     // ルーレット演出中
        PLAYING,      // ゲーム進行中
        FINISHED      // 終了
    }
    var phase: TournamentPhase = TournamentPhase.WAITING
    
    // ======== 状態管理 ========
    var multiplier: Double = 2.5
    var currentBlindLevel: Int = 0
    var blindLevelStartTime: Long = 0
    val finishOrder: MutableList<UUID> = mutableListOf()
    
    // ======== 内部クラス: SitAndGoPlayerData ========
    inner class SitAndGoPlayerData(player: Player, seat: Int) : PlayerData(player, seat) {
        var eliminationOrder: Int = -1
        var finalRank: Int = 0
        var prizeWon: Long = 0
        var ratingBefore: Int = 0
        var ratingAfter: Int = 0
        
        // タイムバンク
        var defaultTimeRemaining: Int = 15    // デフォルト持ち時間
        var additionalTimeRemaining: Int = 0  // アディショナル持ち時間
        var afkCount: Int = 0                 // 連続放置回数
    }
    
    // ======== 倍率抽選 ========
    fun pickMultiplier(): Double {
        val table = mapOf(
            2.5 to 16.0, 3.0 to 14.5, 3.5 to 13.5, 4.0 to 37.0,
            5.0 to 8.0, 6.0 to 4.0, 8.0 to 3.0, 10.0 to 2.0,
            15.0 to 0.7, 20.0 to 0.3
        )
        val random = Random.nextDouble() * 100.0
        var cumulative = 0.0
        for ((mult, weight) in table) {
            cumulative += weight
            if (random < cumulative) return mult
        }
        return 2.5
    }
    
    // ======== スタック計算 ========
    fun getStartingStack(): Int {
        val bbAmount = when {
            multiplier <= 2.5 -> 20
            multiplier <= 3.0 -> 25
            multiplier <= 3.5 -> 30
            multiplier <= 4.0 -> 30
            multiplier <= 5.0 -> 35
            multiplier <= 6.0 -> 40
            multiplier <= 8.0 -> 50
            multiplier <= 10.0 -> 60
            multiplier <= 15.0 -> 80
            else -> 100
        }
        val blinds = getBlindStructure()[0]
        return bbAmount * blinds[1]  // BB単位 × BB
    }
    
    // ======== 賞金計算 ========
    fun getPrizeDistribution(): Map<Int, Double> {
        return if (multiplier >= 10.0) {
            mapOf(1 to 0.60, 2 to 0.30, 3 to 0.10, 4 to 0.0)
        } else {
            mapOf(1 to 0.70, 2 to 0.30, 3 to 0.0, 4 to 0.0)
        }
    }
    
    fun calculatePrize(rank: Int): Long {
        val totalPool = buyIn * 4 * multiplier
        val distribution = getPrizeDistribution()
        return (totalPool * (distribution[rank] ?: 0.0)).toLong()
    }
    
    // ======== ブラインド管理 ========
    fun getBlindStructure(): List<List<Int>> {
        return listOf(
            listOf(1, 2, 2), listOf(1, 3, 3), listOf(2, 4, 4),
            listOf(3, 6, 6), listOf(5, 10, 10), listOf(7, 14, 14),
            listOf(10, 20, 20), listOf(15, 30, 30), listOf(20, 40, 40),
            listOf(30, 60, 60), listOf(40, 80, 80), listOf(50, 100, 100),
            listOf(70, 140, 140)
        )
    }
    
    fun getCurrentBlinds(): Triple<Int, Int, Int> {
        val structure = getBlindStructure()
        val blinds = structure[minOf(currentBlindLevel, structure.size - 1)]
        return Triple(blinds[0], blinds[1], blinds[2])
    }
    
    fun checkAndUpdateBlindLevel(): Boolean {
        val elapsed = System.currentTimeMillis() - blindLevelStartTime
        val levelDuration = con.getInt("sitandgo.blindLevelSeconds") * 1000L
        val newLevel = (elapsed / levelDuration).toInt()
        if (newLevel > currentBlindLevel) {
            currentBlindLevel = minOf(newLevel, getBlindStructure().size - 1)
            return true
        }
        return false
    }
    
    fun getSecondsUntilNextLevel(): Int {
        val elapsed = System.currentTimeMillis() - blindLevelStartTime
        val levelDuration = con.getInt("sitandgo.blindLevelSeconds") * 1000L
        val currentLevelElapsed = elapsed % levelDuration
        return ((levelDuration - currentLevelElapsed) / 1000).toInt()
    }
    
    // ======== GUI情報表示 ========
    fun updateBlindInfoGUI() {
        val (sb, bb, bba) = getCurrentBlinds()
        val nextLevelIn = getSecondsUntilNextLevel()
        
        // スロット18: 次レベルまでの時間
        val clockItem = ItemStack(Material.CLOCK, maxOf(1, minOf(64, nextLevelIn)))
        clockItem.itemMeta = clockItem.itemMeta?.apply {
            displayName(Component.text("§e次レベルまで §f${nextLevelIn}秒"))
            lore(listOf(
                Component.text("§7現在: Lv.${currentBlindLevel + 1}"),
                Component.text("§7SB:$sb / BB:$bb / BBA:$bba")
            ))
        }
        
        // スロット19: 現在のブラインド
        val blindItem = ItemStack(Material.GOLD_NUGGET)
        blindItem.itemMeta = blindItem.itemMeta?.apply {
            displayName(Component.text("§6SB:$sb / BB:$bb / BBA:$bba"))
        }
        
        // スロット27: 倍率・賞金プール
        val prizeItem = ItemStack(Material.SUNFLOWER)
        prizeItem.itemMeta = prizeItem.itemMeta?.apply {
            displayName(Component.text("§e倍率: §6§l${multiplier}x"))
            lore(listOf(
                Component.text("§7賞金プール: §e${(buyIn * 4 * multiplier).toLong()}"),
                Component.text("§71位: §6${calculatePrize(1)}"),
                Component.text("§72位: §f${calculatePrize(2)}"),
                Component.text("§73位: §7${calculatePrize(3)}")
            ))
        }
        
        // 全プレイヤーのGUIに反映
        for (pd in playerList) {
            pd.playerGUI.inv.setItem(18, clockItem)
            pd.playerGUI.inv.setItem(19, blindItem)
            pd.playerGUI.inv.setItem(27, prizeItem)
            
            // スロット26: 各自のレート表示
            if (pd is SitAndGoPlayerData) {
                val ratingItem = ItemStack(Material.EXPERIENCE_BOTTLE)
                ratingItem.itemMeta = ratingItem.itemMeta?.apply {
                    displayName(Component.text("§bあなたのレート: §f${pd.ratingBefore}"))
                    if (buyIn >= con.getInt("sitandgo.ratingMinBuyIn")) {
                        lore(listOf(Component.text("§a✓ レート変動あり")))
                    } else {
                        lore(listOf(Component.text("§7レート変動なし（10万以上で変動）")))
                    }
                }
                pd.playerGUI.inv.setItem(26, ratingItem)
            }
        }
    }
    
    // ======== ルーレット演出 ========
    fun playRouletteAnimation(targetMultiplier: Double) {
        phase = TournamentPhase.ROULETTE
        
        val targetIndex = RouletteDisplay.getReelIndex(targetMultiplier)
        val totalSpins = 3  // 3周
        val totalSteps = RouletteDisplay.REEL_ITEMS.size * totalSpins + targetIndex
        
        var delay = 50L  // 初期50ms（高速）
        val maxDelay = 400L  // 最終400ms（低速）
        
        for (step in 0 until totalSteps) {
            val currentIndex = step % RouletteDisplay.REEL_ITEMS.size
            val item = RouletteDisplay.REEL_ITEMS[currentIndex]
            
            // GUI更新（スロット22に表示）
            displayRouletteFrame(item)
            
            // カチカチ音
            playSoundAlPl(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F + (step % 3) * 0.1F)
            
            // 減速計算
            val progress = step.toDouble() / totalSteps
            delay = (50 + (maxDelay - 50) * progress.pow(2)).toLong()
            
            Thread.sleep(delay)
        }
        
        // 停止演出
        playStopEffect(targetMultiplier)
        Thread.sleep(2000)
        
        phase = TournamentPhase.PLAYING
    }
    
    fun displayRouletteFrame(item: ltotj.minecraft.texasholdem_kotlin.game.utility.RouletteItem) {
        val displayItem = ItemStack(item.material)
        val meta = displayItem.itemMeta
        if (meta != null) {
            meta.displayName(Component.text(item.displayName))
            displayItem.itemMeta = meta
        }
        
        for (pd in playerList) {
            pd.playerGUI.inv.setItem(22, displayItem)
        }
    }
    
    fun playStopEffect(mult: Double) {
        when {
            mult >= 20.0 -> {
                playSoundAlPl(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F)
                repeat(5) {
                    playSoundAlPl(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0F)
                    Thread.sleep(200)
                }
            }
            mult >= 10.0 -> {
                playSoundAlPl(Sound.ENTITY_PLAYER_LEVELUP, 1.0F)
                playSoundAlPl(Sound.BLOCK_BEACON_ACTIVATE, 1.0F)
            }
            mult >= 6.0 -> {
                playSoundAlPl(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F)
            }
            else -> {
                playSoundAlPl(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F)
            }
        }
    }
    
    // ======== 順位確定 ========
    fun recordElimination(playerUUID: UUID) {
        finishOrder.add(playerUUID)
    }
    
    fun getFinalRankings(): List<Pair<UUID, Int>> {
        // finishOrderは脱落順（早い方が4位）
        val rankings = mutableListOf<Pair<UUID, Int>>()
        for (i in finishOrder.indices) {
            rankings.add(Pair(finishOrder[i], 4 - i))  // 逆順にして順位付け
        }
        return rankings
    }
    
    // ======== トーナメント終了処理 ========
    fun endTournament() {
        phase = TournamentPhase.FINISHED
        
        // 順位確定
        val rankings = getFinalRankings()
        
        // 賞金配布
        for ((uuid, rank) in rankings) {
            val prize = calculatePrize(rank)
            vault.deposit(uuid, prize.toDouble())
        }
        
        // レート更新（バイイン10万以上のみ）
        if (buyIn >= con.getInt("sitandgo.ratingMinBuyIn")) {
            updateRatings(rankings)
        }
        
        // ログ保存
        saveTournamentLog(rankings)
        
        // 結果表示（チャット）
        sendTournamentResult(rankings)
    }
    
    fun updateRatings(rankings: List<Pair<UUID, Int>>) {
        val mysql = MySQLManager(ltotj.minecraft.texasholdem_kotlin.Main.plugin, "SitAndGo_Rating")
        val ratingRepo = RatingRepository(mysql)
        
        // レート取得
        val playerRatings = rankings.map { (uuid, rank) ->
            Triple(uuid, ratingRepo.getRating(uuid), rank)
        }
        
        // レート変動計算
        val startingBB = getStartingStack() / getCurrentBlinds().second
        val ratingChanges = SitAndGoRating.calculateRatingChanges(playerRatings, startingBB)
        
        // レート更新
        for ((uuid, rank) in rankings) {
            val pd = playerList.find { it.player.uniqueId == uuid } as? SitAndGoPlayerData ?: continue
            val oldRating = pd.ratingBefore
            val change = ratingChanges[uuid] ?: 0
            val newRating = maxOf(0, oldRating + change)
            pd.ratingAfter = newRating
            
            ratingRepo.updateRating(uuid, pd.player.name, newRating, rank, pd.prizeWon)
        }
    }
    
    fun saveTournamentLog(rankings: List<Pair<UUID, Int>>) {
        // TODO: sitandgo_logテーブルにログ保存
    }
    
    fun sendTournamentResult(rankings: List<Pair<UUID, Int>>) {
        val rankData = rankings.sortedBy { it.second }.map { (uuid, rank) ->
            val pd = playerList.find { it.player.uniqueId == uuid }
            Triple(rank, pd?.player?.name ?: "Unknown", calculatePrize(rank))
        }
        
        val messages = listOf(
            "§4§l============ §eSit & Go Result §4§l============",
            "§e倍率: §6§l${multiplier}x §7(賞金プール: ${(buyIn * 4 * multiplier).toLong()})",
            "",
            "§6§l🏆 1位: ${rankData[0].second} §e+${rankData[0].third}",
            "§f§l🥈 2位: ${rankData[1].second} §e+${rankData[1].third}",
            "§7§l🥉 3位: ${rankData[2].second} §e+${rankData[2].third}",
            "§8   4位: ${rankData[3].second}",
            "",
            if (buyIn >= con.getInt("sitandgo.ratingMinBuyIn")) "§7レート変動あり" else "§7レート変動なし",
            "§4§l=========================================="
        )
        for (playerData in playerList) {
            for (msg in messages) playerData.player.sendMessage(msg)
        }
    }
    
    // ======== run()メソッド（既存を拡張） ========
    override fun run() {
        // フェーズ1: 募集中 → 4人揃うまで待機（コマンドから制御）
        
        // フェーズ2: ルーレット演出
        multiplier = pickMultiplier()
        playRouletteAnimation(multiplier)
        
        // スタック設定
        firstChips = getStartingStack()
        for (pd in playerList) {
            pd.playerChips = firstChips
        }
        
        // ブラインドタイマー開始
        blindLevelStartTime = System.currentTimeMillis()
        
        // フェーズ3: ゲーム進行中（既存のrun()ロジックを使用）
        phase = TournamentPhase.PLAYING
        isRunning = true
        
        // TODO: 既存のTexasHoldem run()ロジックを呼び出し or 統合
        
        // フェーズ4: 終了
        endTournament()
    }
}
