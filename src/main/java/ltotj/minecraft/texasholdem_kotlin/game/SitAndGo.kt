package ltotj.minecraft.texasholdem_kotlin.game

import ltotj.minecraft.texasholdem_kotlin.Main
import ltotj.minecraft.texasholdem_kotlin.Main.Companion.con
import ltotj.minecraft.texasholdem_kotlin.Main.Companion.vault
import ltotj.minecraft.texasholdem_kotlin.MySQLManager
import ltotj.minecraft.texasholdem_kotlin.Utility.createGUIItem
import ltotj.minecraft.texasholdem_kotlin.game.utility.PlayerGUI
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
    var recruitmentStartTime: Long = 0  // 募集開始時刻
    
    @Volatile var isCancelled: Boolean = false
    
    // Configキャッシュ（テーブル作成時に1回だけ読み込み）
    private val cachedBlindStructure: List<List<Int>>
    
    init {
        // Config読み込みはテーブル作成時のみ
        cachedBlindStructure = loadBlindStructureFromConfig()
    }
    
    // SitAndGo専用プレイヤーリスト（親クラスと別に管理）
    val sitAndGoPlayerList = ArrayList<SitAndGoPlayerData>()
    
    // ======== 内部クラス: SitAndGoPlayerData ========
    inner class SitAndGoPlayerData(player: Player, seat: Int) : PlayerData(player, seat) {
        // PlayerGUIのタイトルをSitAndGoに変更
        override val playerGUI = PlayerGUI(seat, "SitAndGo")
        
        var eliminationOrder: Int = -1
        var finalRank: Int = 0
        var prizeWon: Long = 0
        var ratingBefore: Int = 0
        var ratingAfter: Int = 0
        
        // タイムバンク
        var defaultTimeRemaining: Int = 15    // デフォルト持ち時間
        var additionalTimeRemaining: Int = 0  // アディショナル持ち時間
        var afkCount: Int = 0                 // 連続放置回数
        
        // デバッグ用Bot判定
        var isBot: Boolean = false
        var botName: String = ""
        var botUuid: UUID? = null  // Bot用のユニークUUID
        
        // ユニークなUUIDを取得（Bot対応）
        fun getUniqueId(): UUID {
            return if (isBot && botUuid != null) botUuid!! else player.uniqueId
        }
        
        // レーティング表示付きの頭アイテム
        override fun getHead(): ItemStack {
            val item = super.getHead()
            val meta = item.itemMeta
            if (meta != null) {
                meta.lore(listOf(Component.text("§7Rating: §f${ratingBefore}")))
                item.itemMeta = meta
            }
            return item
        }
    }
    
    // ======== プレイヤー管理 ========
    fun addSitAndGoPlayer(player: Player): Boolean {
        if (sitAndGoPlayerList.size >= 4 || phase != TournamentPhase.WAITING) return false
        if (sitAndGoPlayerList.any { it.player.uniqueId == player.uniqueId }) return false
        
        val seat = sitAndGoPlayerList.size
        val playerData = SitAndGoPlayerData(player, seat)
        
        // レート取得
        val mysql = MySQLManager(ltotj.minecraft.texasholdem_kotlin.Main.plugin, "SitAndGo_Rating_AddPlayer")
        val ratingRepo = ltotj.minecraft.texasholdem_kotlin.rating.RatingRepository(mysql)
        playerData.ratingBefore = ratingRepo.getRating(player.uniqueId)
        
        sitAndGoPlayerList.add(playerData)
        seatMap[player.uniqueId] = seat
        
        // Main.currentPlayersに登録
        Main.currentPlayers[player.uniqueId] = masterPlayer.uniqueId
        
        // 最初の参加者で募集タイマー開始
        if (sitAndGoPlayerList.size == 1) {
            recruitmentStartTime = System.currentTimeMillis()
            startRecruitmentTimer()
        }
        
        // GUIを開く
        player.openInventory(playerData.playerGUI.inv)
        
        // 新規参加者のGUIに既存プレイヤー情報を設定
        for (existingPd in sitAndGoPlayerList) {
            if (existingPd.player.uniqueId != player.uniqueId) {
                playerData.playerGUI.setCoin(existingPd.seat, existingPd.player.name, firstChips)
                playerData.playerGUI.inv.setItem(cardPosition(existingPd.seat) - 1, existingPd.getHead())
            }
        }
        
        // 全プレイヤーに新規参加者の席情報を更新
        for (pd in sitAndGoPlayerList) {
            pd.playerGUI.setCoin(seat, player.name, firstChips)
            pd.playerGUI.inv.setItem(cardPosition(seat) - 1, playerData.getHead())
        }
        
        // 4人揃ったら開始
        if (sitAndGoPlayerList.size == 4) {
            // playerListにコピー（親クラス互換）
            playerList.clear()
            playerList.addAll(sitAndGoPlayerList)
            start()
        }
        
        return true
    }
    
    fun removeSitAndGoPlayer(player: Player): Boolean {
        if (phase != TournamentPhase.WAITING) return false
        
        val playerData = sitAndGoPlayerList.find { it.player.uniqueId == player.uniqueId } ?: return false
        sitAndGoPlayerList.remove(playerData)
        seatMap.remove(player.uniqueId)
        
        // 席番号を再割り当て
        for ((index, pd) in sitAndGoPlayerList.withIndex()) {
            pd.playerGUI.inv.clear()
            seatMap[pd.player.uniqueId] = index
        }
        
        // ホストが抜けた場合はテーブル解散
        if (player.uniqueId == masterPlayer.uniqueId) {
            dissolveTournament()
            return true
        }
        
        return true
    }
    
    fun dissolveTournament() {
        for (pd in sitAndGoPlayerList) {
            vault.deposit(pd.player.uniqueId, buyIn.toDouble())
            pd.player.sendMessage("§e§lホストが離脱したためトーナメントが解散しました。返金されました。")
            ltotj.minecraft.texasholdem_kotlin.Main.currentPlayers.remove(pd.player.uniqueId)
            pd.player.closeInventory()
        }
        ltotj.minecraft.texasholdem_kotlin.Main.sitAndGoTables.remove(masterPlayer.uniqueId)
    }
    
    fun getPlayerCount(): Int = sitAndGoPlayerList.size
    
    // ======== 募集タイマー ========
    private fun startRecruitmentTimer() {
        val waitSeconds = con.getInt("sitandgo.waitTimeSeconds")
        
        org.bukkit.Bukkit.getScheduler().runTaskLater(Main.plugin, Runnable {
            // タイムアウト時に4人揃っていなければ解散
            if (phase == TournamentPhase.WAITING && sitAndGoPlayerList.size < 4) {
                Main.plugin.logger.info("[SitAndGo] Recruitment timeout for ${masterPlayer.name}")
                dissolveTournament()
                org.bukkit.Bukkit.broadcast(
                    net.kyori.adventure.text.Component.text("§6§l[SitAndGo] §c${masterPlayer.name}のトーナメントは時間切れにより解散しました")
                )
            }
        }, (waitSeconds * 20).toLong())  // TicksはSeconds * 20
    }
    
    // ======== デバッグ用: ダミープレイヤー追加 ========
    fun addDebugBots(count: Int) {
        for (i in 1..count) {
            if (sitAndGoPlayerList.size >= 4) break
            
            val seat = sitAndGoPlayerList.size
            val botPlayer = masterPlayer  // ダミーとして同じプレイヤーを使用（実際にはGUI非表示）
            val playerData = SitAndGoPlayerData(botPlayer, seat)
            playerData.isBot = true
            playerData.botName = "Bot$i"
            // Bot用にユニークかつ固定のUUIDを生成（結果表示・レート計算用）
            // ランダムだとレートが毎回リセットされるため、固定UUIDを使用
            playerData.botUuid = java.util.UUID.fromString("00000000-0000-0000-0000-00000000000$i")
            
            // デフォルトレート
            playerData.ratingBefore = 2500
            
            sitAndGoPlayerList.add(playerData)
            // BotもseatMapに登録（ユニークUUID使用）
            seatMap[playerData.getUniqueId()] = seat
            
            // 全プレイヤーのGUIを更新
            for (pd in sitAndGoPlayerList.filter { !it.isBot }) {
                pd.playerGUI.setCoin(seat, "§7Bot$i", firstChips)
            }
        }
        
        // 4人揃ったら開始
        if (sitAndGoPlayerList.size == 4) {
            playerList.clear()
            playerList.addAll(sitAndGoPlayerList)
            start()
        }
    }
    
    // ======== インベントリを開く ========
    fun openSitAndGoInv(player: Player) {
        if (phase == TournamentPhase.WAITING) {
            val pd = sitAndGoPlayerList.find { it.player.uniqueId == player.uniqueId }
            if (pd != null) {
                player.openInventory(pd.playerGUI.inv)
            } else {
                player.sendMessage("§c参加データが見つかりません")
            }
        } else {
            // ゲーム開始後は親クラスのリストを使用
            openInv(player.uniqueId)
        }
    }
    
    // ======== 倍率抽選 ========
    fun pickMultiplier(): Double {
        Main.plugin.logger.info("[SitAndGo Debug] Picking multiplier using direct path method...")
        
        // 直接パス指定でmultiplierTableの各値を取得
        val multipliers = listOf("2.5", "3.0", "3.5", "4.0", "5.0", "6.0", "8.0", "10.0", "15.0", "20.0")
        val table = mutableMapOf<Double, Double>()
        
        for (multStr in multipliers) {
            val probability = con.getDouble("sitandgo.multiplierTable.$multStr")
            if (probability > 0.0) {
                val mult = multStr.toDouble()
                table[mult] = probability
                Main.plugin.logger.info("[SitAndGo Debug] Found multiplier: $mult -> $probability%")
            }
        }
        
        if (table.isEmpty()) {
            Main.plugin.logger.warning("[SitAndGo Debug] No multipliers found, using default 2.5")
            return 2.5
        }
        
        Main.plugin.logger.info("[SitAndGo Debug] Final multiplierTable: $table")
        
        val random = Random.nextDouble() * 100.0
        var cumulative = 0.0
        for ((mult, weight) in table) {
            cumulative += weight
            if (random < cumulative) {
                Main.plugin.logger.info("[SitAndGo Debug] Selected multiplier: $mult (random: $random, cumulative: $cumulative)")
                return mult
            }
        }
        Main.plugin.logger.warning("[SitAndGo Debug] No multiplier selected, using default 2.5")
        return 2.5
    }
    
    // ======== スタック計算 ========
    fun getStartingStack(): Int {
        Main.plugin.logger.info("[SitAndGo Debug] Getting starting stack for multiplier: $multiplier")
        
        // 直接パス指定でstackByMultiplierの値を取得
        val bbAmount = con.getInt("sitandgo.stackByMultiplier.$multiplier")
        Main.plugin.logger.info("[SitAndGo Debug] bbAmount from config: $bbAmount")
        
        val actualBbAmount = if (bbAmount > 0) bbAmount else 30 // デフォルト30
        Main.plugin.logger.info("[SitAndGo Debug] Using bbAmount: $actualBbAmount (default used: ${bbAmount <= 0})")
        
        val blinds = getBlindStructure()[0]
        Main.plugin.logger.info("[SitAndGo Debug] blinds[0]: ${blinds?.joinToString(",") ?: "null"}")
        
        val finalStack = actualBbAmount * (blinds?.get(1) ?: 2)
        Main.plugin.logger.info("[SitAndGo Debug] finalStack: $finalStack ($actualBbAmount * ${blinds?.get(1) ?: 2})")
        return finalStack
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
        val totalPool = buyIn * multiplier // 正しい計算: バイイン×倍率
        val distribution = getPrizeDistribution()
        return (totalPool * (distribution[rank] ?: 0.0)).toLong()
    }
    
    // ======== ブラインド管理 ========
    private fun loadBlindStructureFromConfig(): List<List<Int>> {
        val list = con.getList("sitandgo.blindStructure")
        Main.plugin.logger.info("[SitAndGo Debug] blindStructure list: ${list != null}")
        if (list == null) {
            Main.plugin.logger.warning("[SitAndGo Debug] blindStructure is null, using default [1,2,2]")
            return listOf(listOf(1, 2, 2))
        }
        
        val result = list.mapNotNull { item ->
            (item as? List<*>)?.mapNotNull { it as? Int }
        }
        Main.plugin.logger.info("[SitAndGo Debug] blindStructure loaded: $result")
        return result
    }
    
    fun getBlindStructure(): List<List<Int>> {
        return cachedBlindStructure // キャッシュから取得（Config読み込みなし）
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
            val oldLevel = currentBlindLevel
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
        
        // スロット18: コンパス（ストラクチャタイマー + 情報統合、スタック数=残り時間）
        val structureItem = ItemStack(Material.COMPASS, maxOf(1, minOf(64, nextLevelIn)))
        structureItem.itemMeta = structureItem.itemMeta?.apply {
            displayName(Component.text("§e次レベルまで §f${nextLevelIn}秒"))
            lore(listOf(
                Component.text("§7現在: Lv.${currentBlindLevel + 1}"),
                Component.text("§7SB:$sb / BB:$bb / BBA:$bba")
            ))
        }
        
        // スロット13: 倍率・賞金プール（ルーレットと同じアイテム、コミュニティカード上）
        val rouletteItem = RouletteDisplay.getItemForMultiplier(multiplier)
        val prizeItem = ItemStack(rouletteItem.material)
        prizeItem.itemMeta = prizeItem.itemMeta?.apply {
            displayName(Component.text("§e倍率: ${rouletteItem.displayName}"))
            lore(listOf(
                Component.text("§7賞金プール: §e${(buyIn * multiplier).toLong()}"),
                Component.text("§71位: §6${calculatePrize(1)}"),
                Component.text("§72位: §f${calculatePrize(2)}"),
                Component.text("§73位: §7${calculatePrize(3)}")
            ))
        }
        
        // 全プレイヤーのGUIに反映
        for (pd in playerList) {
            pd.playerGUI.inv.setItem(18, structureItem)
            pd.playerGUI.inv.setItem(13, prizeItem)
            
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
    
    // 軽量版: ストラクチャタイマーのみ更新（毎秒呼び出し可）
    private fun updateStructureTimerOnly() {
        val (sb, bb, bba) = getCurrentBlinds()
        val nextLevelIn = getSecondsUntilNextLevel()
        
        val structureItem = ItemStack(Material.COMPASS, maxOf(1, minOf(64, nextLevelIn)))
        structureItem.itemMeta = structureItem.itemMeta?.apply {
            displayName(Component.text("§e次レベルまで §f${nextLevelIn}秒"))
            lore(listOf(
                Component.text("§7現在: Lv.${currentBlindLevel + 1}"),
                Component.text("§7SB:$sb / BB:$bb / BBA:$bba")
            ))
        }
        
        for (pd in playerList) {
            pd.playerGUI.inv.setItem(18, structureItem)
        }
    }
    
    // ======== ルーレット演出 ========
    fun playRouletteAnimation(targetMultiplier: Double) {
        phase = TournamentPhase.ROULETTE
        
        val targetIndex = RouletteDisplay.getReelIndex(targetMultiplier)
        val totalSpins = 3  // 3周
        // 修正: 1個ずれの修正 - targetIndexまで正確に止まるよう調整
        val totalSteps = RouletteDisplay.REEL_ITEMS.size * totalSpins + targetIndex + 1
        
        var delay: Long
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
            // ルーレット中は倍率を隠してわくわく感を演出
            meta.displayName(Component.text("§6§l???"))
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
        
        // 停止後に倍率を表示
        val item = RouletteDisplay.getItemForMultiplier(mult)
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
        
        // 勝者（finishOrderに含まれていないプレイヤー = 1位）を追加
        for (pd in playerList) {
            if (pd is SitAndGoPlayerData) {
                if (!finishOrder.contains(pd.getUniqueId())) {
                    finishOrder.add(pd.getUniqueId())
                }
            }
        }
        
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
        
        // プレイヤー登録解除（Botは登録されていない）
        for (pd in playerList) {
            if (pd is SitAndGoPlayerData && !pd.isBot) {
                Main.currentPlayers.remove(pd.player.uniqueId)
            }
        }
        
        // テーブル削除
        Main.sitAndGoTables.remove(masterPlayer.uniqueId)
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
    val mysql = MySQLManager(ltotj.minecraft.texasholdem_kotlin.Main.plugin, "SitAndGo_Log")
    
    // 順位順にソート
    val sortedRankings = rankings.sortedBy { it.second }
    
    // 各順位のプレイヤーデータを取得
    val playerDataList = sortedRankings.map { (uuid, rank) ->
        val pd = playerList.find { it.player.uniqueId == uuid }
        val sitAndGoPd = pd as? SitAndGoPlayerData
        mapOf(
            "uuid" to uuid.toString(),
            "name" to (pd?.player?.name ?: "Unknown"),
            "rank" to rank,
            "prize" to calculatePrize(rank),
            "ratingBefore" to (sitAndGoPd?.ratingBefore ?: 0),
            "ratingAfter" to (sitAndGoPd?.ratingAfter ?: 0)
        )
    }
    
    // 4人分のデータがあることを確認
    if (playerDataList.size < 4) return
    
    val p1 = playerDataList[0]
    val p2 = playerDataList[1]
    val p3 = playerDataList[2]
    val p4 = playerDataList[3]
    
    val query = """
        INSERT INTO sitandgo_log (
            start_time, buy_in, multiplier, total_prize,
            p1_uuid, p1_name, p1_rank, p1_prize, p1_rating_before, p1_rating_after,
            p2_uuid, p2_name, p2_rank, p2_prize, p2_rating_before, p2_rating_after,
            p3_uuid, p3_name, p3_rank, p3_prize, p3_rating_before, p3_rating_after,
            p4_uuid, p4_name, p4_rank, p4_prize, p4_rating_before, p4_rating_after
        ) VALUES (
            NOW(), $buyIn, $multiplier, ${(buyIn * multiplier).toLong()},
            '${p1["uuid"]}', '${p1["name"]}', ${p1["rank"]}, ${p1["prize"]}, ${p1["ratingBefore"]}, ${p1["ratingAfter"]},
            '${p2["uuid"]}', '${p2["name"]}', ${p2["rank"]}, ${p2["prize"]}, ${p2["ratingBefore"]}, ${p2["ratingAfter"]},
            '${p3["uuid"]}', '${p3["name"]}', ${p3["rank"]}, ${p3["prize"]}, ${p3["ratingBefore"]}, ${p3["ratingAfter"]},
            '${p4["uuid"]}', '${p4["name"]}', ${p4["rank"]}, ${p4["prize"]}, ${p4["ratingBefore"]}, ${p4["ratingAfter"]}
        )
    """.trimIndent()
    
    Main.plugin.logger.info("[SitAndGo Debug] Saving log: $query")
    
    try {
        mysql.execute(query)
        Main.plugin.logger.info("[SitAndGo] Tournament log saved successfully")
    } catch (e: Exception) {
        Main.plugin.logger.warning("Failed to save tournament log: ${e.message}")
        e.printStackTrace()
    }
}
    
    fun sendTournamentResult(rankings: List<Pair<UUID, Int>>) {
        val rankData = rankings.sortedBy { it.second }.map { (uuid, rank) ->
            val pd = playerList.find { it.player.uniqueId == uuid }
            Triple(rank, pd?.player?.name ?: "Unknown", calculatePrize(rank))
        }
        
        val messages = mutableListOf(
            "§4§l============ §eSit & Go Result §4§l============",
            "§e倍率: §6§l${multiplier}x §7(賞金プール: ${(buyIn * multiplier).toLong()})",
            ""
        )
        
        if (rankData.isNotEmpty()) messages.add("§6§l🏆 1位: ${rankData[0].second} §e+${rankData[0].third}")
        if (rankData.size > 1) messages.add("§f§l🥈 2位: ${rankData[1].second} §e+${rankData[1].third}")
        if (rankData.size > 2) messages.add("§7§l🥉 3位: ${rankData[2].second} §e+${rankData[2].third}")
        if (rankData.size > 3) messages.add("§8   4位: ${rankData[3].second}")
            
        messages.add("§4§l==========================================")
        
        for (playerData in playerList) {
            for (msg in messages) playerData.player.sendMessage(msg)
        }
    }
    
    private fun setClockFormatted(displayText: String, amount: Int) {
        val item = ItemStack(Material.CLOCK, maxOf(1, minOf(64, amount)))
        val meta = item.itemMeta
        meta.displayName(Component.text(displayText))
        item.itemMeta = meta
        setItemAlPl(19, item) // スロット19: アクションタイマー（持ち時間）
    }

    // ======== アクションタイマー（Bot対応 & タイムバンク実装） ========
    override fun actionTime(dif: Int) {
        turnCount += dif
        
        // ループ条件:
        // 1. 全員がアクション完了していない (folded < size-1)
        // 2. ベット額が揃っていない (instBet != bet) OR まだ一巡していない (turnCount < size + dif)
        // 3. アクティブプレイヤーが複数いる
        // 4. キャンセルされていない
        while (((allInList.size + foldedList.size + 1) < playerList.size || bet != 0) &&
                foldedList.size < playerList.size - 1 &&
                ((playerList[turnSeat()].instBet != bet) || turnCount < playerList.size + dif)
        ) {
            if (isCancelled) return

            val currentSeat = turnSeat()
            val currentPd = playerList[currentSeat]
            
            // GUI更新
            setGUI(currentSeat)
            
            // プレイヤー準備
            currentPd.preCall.set(false)
            currentPd.player.playSound(currentPd.player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 2F, 2F)
            
            // アクション待ち
            if (!foldedList.contains(currentSeat) && !allInList.contains(currentSeat)) {
                
                // === Botの場合 ===
                if (currentPd is SitAndGoPlayerData && currentPd.isBot) {
                    processBotAction(currentPd)
                } 
                // === 人間の場合 ===
                else {
                    processHumanAction(currentPd)
                }
            }
            
            // ターン終了処理
            currentPd.playerGUI.removeButton()
            removeItem(chipPosition(currentSeat) - 3)
            removeItem(19) // アクションタイマー削除（スロット19）
            setCoin(currentSeat)
            turnCount += 1
        }
        
        // ラウンド終了後のチップアニメーション
        for (i in 0 until playerList.size) {
            if (!foldedList.contains(i)) {
                if (allInList.contains(i)) {
                    setItemAlPl(chipPosition(i), createGUIItem(Material.NETHER_STAR, 1, "§e§lオールイン済み${playerList[i].totalBetAmount}枚"))
                    Thread.sleep(500)
                } else {
                    removeItem(chipPosition(i))
                    playSoundAlPl(Sound.BLOCK_GRAVEL_STEP, 2F)
                    Thread.sleep(500)
                }
            }
        }
        
        turnCount = 0
        lastRaise = 2 // 最小レイズ額リセット
        setPot()
        resetBet()
    }
    
    private fun processBotAction(bot: SitAndGoPlayerData) {
        // 思考時間（演出）
        Thread.sleep(1000)
        
        val random = Random.nextInt(100)
        when {
            // コール額が足りないならオールイン
            bot.playerChips <= bet - bot.instBet -> {
                bot.call() // call内でチップ不足ならAll-inになる
            }
            random < 10 && bet > bot.instBet -> { // 10%でフォールド（別途がある場合のみ）
                bot.fold()
            }
            random < 95 -> { // 85%でコール/チェック (10+85=95)
                bot.call()
            }
            else -> { // 5%でオールイン
                bot.allIn()
            }
        }
    }
    
    private fun processHumanAction(playerData: ltotj.minecraft.texasholdem_kotlin.game.TexasHoldem.PlayerData) {
        val sngPlayer = playerData as? SitAndGoPlayerData
        
        // タイムバンク計算
        // デフォルト: 15s (AFKで減少)
        // アディショナル: Max 15s (+5s/turn)
        var defaultTime = 30 // Fallback
        var additionalTime = 0
        
        if (sngPlayer != null) {
            // アディショナル追加 (+5秒, 最大15秒)
            sngPlayer.additionalTimeRemaining = minOf(15, sngPlayer.additionalTimeRemaining + 5)
            // デフォルト時間計算 (15 - afk*5)
            sngPlayer.defaultTimeRemaining = maxOf(0, 15 - (sngPlayer.afkCount * 5))
            
            defaultTime = sngPlayer.defaultTimeRemaining
            additionalTime = sngPlayer.additionalTimeRemaining
        }
        
        val totalTime = defaultTime + additionalTime
        val tickRate = 20 // 1秒あたりのtick数
        val loopCount = totalTime * tickRate
        
        // カウントダウンループ
        for (i in loopCount downTo 0) {
            if (isCancelled) return

            Thread.sleep(50) // 1tick = 50ms
            
            // 秒数更新表示
            if (i % 20 == 0) {
                val secondsRemaining = i / 20
                playSoundAlPl(Sound.BLOCK_STONE_BUTTON_CLICK_ON, 2F)
                
                // タイムバンク表示分け
                val displayTime = if (secondsRemaining > additionalTime) {
                    "§a${secondsRemaining - additionalTime} §e+${additionalTime}"
                } else {
                    "§c${secondsRemaining}" // アディショナル消費中
                }
                setClockFormatted(displayTime, secondsRemaining)
                
                // ストラクチャタイマーを毎秒更新（軽量）
                updateStructureTimerOnly()
            }
            
            // タイムアウト
            if (i == 0) {
                playerData.addedChips = 0
                playerData.fold()
                if (sngPlayer != null) {
                    sngPlayer.afkCount++ // 放置カウント増加
                    sngPlayer.player.sendMessage("§cタイムアウトしました (放置回数: ${sngPlayer.afkCount})")
                }
                break
            }
            
            // アクション実行確認
            if (playerData.action) {
                if (sngPlayer != null) {
                    sngPlayer.afkCount = 0 // 放置リセット
                    
                    // アディショナル残り時間を保存
                    val timeConsumed = totalTime - (i / 20)
                    if (timeConsumed > defaultTime) {
                        // アディショナル消費
                        val additionalConsumed = timeConsumed - defaultTime
                        sngPlayer.additionalTimeRemaining = maxOf(0, sngPlayer.additionalTimeRemaining - additionalConsumed)
                    }
                }
                break
            }
            
            // プリコール処理
            if (playerData.preCall.get()) {
                playerData.preCall.set(false)
                playerData.call()
            }
        }
        
        playerData.action = false // フラグリセット
    }
    
    // ======== run()メソッド（トーナメント専用） ========
    override fun run() {
        isCancelled = false // フラグリセット
        // 4人揃っていることを確認
        if (playerList.size < 4) {
            for (pd in playerList) {
                pd.player.sendMessage("§c人数不足でトーナメントを開始できませんでした")
            }
            cancelTournament()
            return
        }
        
        // フェーズ1: ルーレット演出
        multiplier = pickMultiplier()
        playRouletteAnimation(multiplier)
        
        // スタック設定
        firstChips = getStartingStack()
        for (pd in playerList) {
            pd.playerChips = firstChips
        }
        
        // ブラインドタイマー開始
        blindLevelStartTime = System.currentTimeMillis()
        currentBlindLevel = 0 // 初期化
        
        // GUI更新
        updateBlindInfoGUI()
        
        // フェーズ2: ゲーム進行中
        phase = TournamentPhase.PLAYING
        isRunning = true
        
        val seatSize = playerList.size
        
        // トーナメントゲームループ（残り1人になるまで続ける）
        while (getActivePlayers().size > 1 && !isCancelled) {
            // ブラインドレベルチェック
            val levelChanged = checkAndUpdateBlindLevel()
            if (levelChanged) {
                Main.plugin.logger.info("[SitAndGo Debug] Blind level increased to: $currentBlindLevel")
            }
            updateBlindInfoGUI()
            
            // ラウンドリセット
            reset()
            
            // pot を0にリセット（アンティのみを表示するため）
            pot = 0
            
            // 既に脱落したプレイヤーをfoldedListに追加
            for (i in 0 until seatSize) {
                if (playerList[i].playerChips == 0 && !foldedList.contains(i)) {
                    foldedList.add(i)
                }
            }
            
            // 残り1人なら終了
            if (getActivePlayers().size <= 1) break
            
            // カード配布
            for (i in 0 until seatSize) {
                if (foldedList.contains(i)) continue
                playSoundAlPl(Sound.ITEM_BOOK_PAGE_TURN, 2F)
                setPlayerCard(i, 0)
                sleep(300)
                playSoundAlPl(Sound.ITEM_BOOK_PAGE_TURN, 2F)
                setPlayerCard(i, 1)
                sleep(300)
            }
            setCommunityCard()
            
            val dif = if (getActivePlayers().size == 2) 1 else 0
            
            // SB、BB、BBAの取得
            val (sb, bb, bba) = getCurrentBlinds()
            Main.plugin.logger.info("[SitAndGo Debug] Round start - SB: $sb, BB: $bb, BBA: $bba, currentBlindLevel: $currentBlindLevel")
            bigBlindAmount = bb
            
            // ======== 正しいポーカールール: SB/BB強制ベット ========
            // TexasHoldemの実装をベースに、foldedList（脱落席）スキップを追加
            
            var bbCount = 0
            var bbDifCount = 0
            var sbSeat = -1
            var bbSeat = -1
            
            // SB/BBを2人から徴収（脱落席はスキップ）
            while (bbCount < 2 && bbDifCount < seatSize * 2) {  // 無限ループ防止
                val currentSeat = turnSeat()
                
                // 脱落席はスキップ
                if (!foldedList.contains(currentSeat)) {
                    val currentPlayer = playerList[currentSeat]
                    val betAmount = if (bbCount == 0) sb else bb
                    
                    // addedChipsを設定（表示用）してから支払い
                    currentPlayer.addedChips = betAmount
                    
                    // 直接チップを減らす
                    val payAmount = minOf(betAmount, currentPlayer.playerChips)
                    currentPlayer.playerChips -= payAmount
                    currentPlayer.instBet = payAmount
                    currentPlayer.totalBetAmount += payAmount
                    
                    // betを現在のブラインドに設定（SB後はsb、BB後はbb）
                    bet = betAmount
                    
                    Main.plugin.logger.info("[SitAndGo Debug] Player ${currentPlayer.player.name} (seat $currentSeat) posts ${if (bbCount == 0) "SB" else "BB"}: $payAmount (bet now: $bet)")
                    
                    // まずプレイヤーチップ表示を更新
                    setCoin(currentSeat)
                    
                    // その後、SB/BBチップを表示（addedChipsを使用）
                    for (pd in playerList) {
                        pd.playerGUI.setChips(currentSeat, currentPlayer.addedChips, 1)
                    }
                    
                    // addedChipsを表示だけに使用し、実際のベットロジックには影響させないようリセット
                    // これをしないと、コール時にaddedChips分が加算されてレイズになってしまう
                    currentPlayer.addedChips = 0
                    
                    currentPlayer.action = false
                    
                    // 席を記録
                    if (bbCount == 0) {
                        sbSeat = currentSeat
                    } else {
                        bbSeat = currentSeat
                    }
                    
                    bbCount++
                }
                
                turnCount += 1
                bbDifCount++
            }
            
            // BBA徴収前にpotを0に強制リセット
            // SB/BB処理で意図せずpotが増えている可能性を排除し、プリフロップはアンティのみにする
            pot = 0
            
            // BBA (Big Blind Ante) 徴収 - BBポジションのみ、BB優先・余りをアンティに
            Main.plugin.logger.info("[SitAndGo Debug] BBA check: bba=$bba, bbSeat=$bbSeat, sb=$sb, bb=$bb")
            if (bba > 0 && bbSeat >= 0) {
                val bbPlayer = playerList[bbSeat]
                // BB支払い後の残りチップでBBAを払う
                val anteAmount = minOf(bba, bbPlayer.playerChips)
                Main.plugin.logger.info("[SitAndGo Debug] BBA calculation: bba=$bba, bbPlayer.chips=${bbPlayer.playerChips}, anteAmount=$anteAmount")
                if (anteAmount > 0) {
                    bbPlayer.playerChips -= anteAmount
                    bbPlayer.totalBetAmount += anteAmount
                    pot += anteAmount
                    Main.plugin.logger.info("[SitAndGo Debug] Player ${bbPlayer.player.name} (seat $bbSeat) pays BBA: $anteAmount (after BB)")
                    setCoin(bbSeat)
                    setPot()
                }
            }
            
            // ミニマムレイズの差分を設定（正しいポーカールール）
            // プリフロップ: BBがベースなので、差分 = bb (BB - 0 の差分)
            // ミニマムレイズ = bb + bb = 2BB （正しい）
            lastRaise = bb
            turnCount = 0
            
            // プリフロップ
            actionTime(bbDifCount)
            
            // フロップ
            if (foldedList.size != playerList.size - 1) {
                openCommunityCard(0)
                openCommunityCard(1)
                openCommunityCard(2)
            }
            if (foldedList.size != playerList.size - 1) {
                actionTime(dif)
            }
            
            // ターン
            if (foldedList.size != playerList.size - 1) {
                openCommunityCard(3)
            }
            if (foldedList.size != playerList.size - 1) {
                actionTime(dif)
            }
            
            // リバー
            if (foldedList.size != playerList.size - 1) {
                openCommunityCard(4)
            }
            if (foldedList.size != playerList.size - 1) {
                actionTime(dif)
            }
            
            // ショーダウン
            if (foldedList.size != playerList.size - 1) {
                playSoundAlPl(Sound.ITEM_BOOK_PAGE_TURN, 2F)
                for (i in 0 until seatSize) {
                    if (!foldedList.contains(i)) openPlCard(i)
                }
                sleep(2000)
            }
            
            // 勝者決定・チップ移動
            showAndPayReward((firstSeat + bbDifCount - 1) % seatSize)
            
            // 脱落チェック
            checkEliminations()
            
            sleep(1000)
            for (i in 0 until seatSize) {
                removeItem(cardPosition(i))
                removeItem(cardPosition(i) + 1)
            }
            firstSeat += 1
        }
        
        // フェーズ3: 終了処理
        endTournament()
    }
    
    // アクティブプレイヤー（チップが残っているプレイヤー）を取得
    fun getActivePlayers(): List<PlayerData> {
        return playerList.filter { it.playerChips > 0 }
    }
    
    // 脱落チェック
    fun checkEliminations() {
        for (pd in playerList) {
            if (pd is SitAndGoPlayerData) {
                if (pd.playerChips == 0 && !finishOrder.contains(pd.getUniqueId())) {
                    recordElimination(pd.getUniqueId())
                    
                    // 順位を正しく計算（1人目脱落=4位、2人目=3位、3人目=2位）
                    val rank = 5 - finishOrder.size
                    
                    if (!pd.isBot) {
                        pd.player.sendMessage("§c§lチップがなくなりました。${rank}位で敗退です。")
                        
                        // 脱落プレイヤーの頭とチップ表示を削除
                        val seat = pd.seat
                        for (pl in playerList) {
                            pl.playerGUI.inv.setItem(cardPosition(seat) - 1, null) // 頭削除
                            pl.playerGUI.inv.setItem(cardPosition(seat) + 2, null) // チップ削除
                        }
                    }
                }
            }
        }
    }
    
    // トーナメントキャンセル
    fun cancelTournament() {
        isCancelled = true // ループ停止
        isRunning = false
        
        for (pd in playerList) {
            vault.deposit(pd.player.uniqueId, buyIn.toDouble())
            pd.player.sendMessage("§e§lトーナメントがキャンセルされました。参加費を返金しました。")
            ltotj.minecraft.texasholdem_kotlin.Main.currentPlayers.remove(pd.player.uniqueId)
        }
        
        // WAITING中のプレイヤーも処理
        if (phase == TournamentPhase.WAITING) {
            for (pd in sitAndGoPlayerList) {
                // playerListに含まれていない場合のみ返金
                if (!playerList.any { it.player.uniqueId == pd.player.uniqueId }) {
                    vault.deposit(pd.player.uniqueId, buyIn.toDouble())
                    pd.player.sendMessage("§e§lトーナメントがキャンセルされました。参加費を返金しました。")
                    ltotj.minecraft.texasholdem_kotlin.Main.currentPlayers.remove(pd.player.uniqueId)
                }
            }
        }
    }
}
