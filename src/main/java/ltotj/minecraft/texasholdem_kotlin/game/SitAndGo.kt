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
    
    // SitAndGo専用プレイヤーリスト（親クラスと別に管理）
    val sitAndGoPlayerList = ArrayList<SitAndGoPlayerData>()
    
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
        
        // GUIを開く
        player.openInventory(playerData.playerGUI.inv)
        
        // 全プレイヤーに席情報を更新
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
    
    // ======== 倍率抽選 ========
    fun pickMultiplier(): Double {
        val section = con.getConfigurationSection("sitandgo.multiplierTable") ?: return 2.5
        val table = section.getKeys(false).mapNotNull { key ->
            val multiplier = key.toDoubleOrNull() ?: return@mapNotNull null
            val probability = section.getDouble(key)
            multiplier to probability
        }.toMap()
        
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
        val section = con.getConfigurationSection("sitandgo.stackByMultiplier") ?: return 30 * 2
        val bbAmount = section.getInt(multiplier.toString(), 30)
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
        val list = con.getList("sitandgo.blindStructure") ?: return listOf(listOf(1, 2, 2))
        return list.mapNotNull { item ->
            (item as? List<*>)?.mapNotNull { it as? Int }
        }
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
                tournament_time, buy_in, multiplier, total_pool,
                p1_uuid, p1_name, p1_prize, p1_rating_before, p1_rating_after,
                p2_uuid, p2_name, p2_prize, p2_rating_before, p2_rating_after,
                p3_uuid, p3_name, p3_prize, p3_rating_before, p3_rating_after,
                p4_uuid, p4_name, p4_prize, p4_rating_before, p4_rating_after
            ) VALUES (
                NOW(), $buyIn, $multiplier, ${(buyIn * 4 * multiplier).toLong()},
                '${p1["uuid"]}', '${p1["name"]}', ${p1["prize"]}, ${p1["ratingBefore"]}, ${p1["ratingAfter"]},
                '${p2["uuid"]}', '${p2["name"]}', ${p2["prize"]}, ${p2["ratingBefore"]}, ${p2["ratingAfter"]},
                '${p3["uuid"]}', '${p3["name"]}', ${p3["prize"]}, ${p3["ratingBefore"]}, ${p3["ratingAfter"]},
                '${p4["uuid"]}', '${p4["name"]}', ${p4["prize"]}, ${p4["ratingBefore"]}, ${p4["ratingAfter"]}
            )
        """.trimIndent()
        
        try {
            mysql.execute(query)
        } catch (e: Exception) {
            ltotj.minecraft.texasholdem_kotlin.Main.plugin.logger.warning("Failed to save tournament log: ${e.message}")
        }
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
    
    // ======== run()メソッド（トーナメント専用） ========
    override fun run() {
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
        
        // GUI更新
        updateBlindInfoGUI()
        
        // フェーズ2: ゲーム進行中
        phase = TournamentPhase.PLAYING
        isRunning = true
        
        val seatSize = playerList.size
        
        // トーナメントゲームループ（残り1人になるまで続ける）
        while (getActivePlayers().size > 1) {
            // ブラインドレベルチェック
            checkAndUpdateBlindLevel()
            updateBlindInfoGUI()
            
            // ラウンドリセット
            reset()
            
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
            
            // SBとBBの強制ベット
            val (sb, bb) = getCurrentBlinds()
            bigBlindAmount = bb
            var bbCount = 0
            var bbDifCount = 0
            while (bbCount < 2) {
                val currentPlayer = playerList[turnSeat()]
                if (!foldedList.contains(turnSeat())) {
                    currentPlayer.addedChips = if (bbCount == 0) sb else bb
                    if (currentPlayer.call()) {
                        setCoin(turnSeat())
                        currentPlayer.action = false
                        bbCount++
                    }
                }
                turnCount += 1
                bbDifCount++
            }
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
            if (pd.playerChips == 0 && !finishOrder.contains(pd.player.uniqueId)) {
                recordElimination(pd.player.uniqueId)
                pd.player.sendMessage("§c§lチップがなくなりました。${finishOrder.size}位で敗退です。")
            }
        }
    }
    
    // トーナメントキャンセル
    fun cancelTournament() {
        for (pd in playerList) {
            vault.deposit(pd.player.uniqueId, buyIn.toDouble())
            pd.player.sendMessage("§e§lトーナメントがキャンセルされました。参加費を返金しました。")
            ltotj.minecraft.texasholdem_kotlin.Main.currentPlayers.remove(pd.player.uniqueId)
        }
    }
}
