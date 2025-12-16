# SitAndGo 実装計画書（詳細版）

## 目次
1. [フェーズ概要](#フェーズ概要)
2. [フェーズ1: データベース拡張](#フェーズ1-データベース拡張)
3. [フェーズ2: Config拡張](#フェーズ2-config拡張)
4. [フェーズ3: SitAndGo.kt 本体実装](#フェーズ3-sitandgokt-本体実装)
5. [フェーズ4: イベント処理](#フェーズ4-イベント処理)
6. [フェーズ5: ルーレット演出実装](#フェーズ5-ルーレット演出実装)
7. [フェーズ6: レーティングシステム実装](#フェーズ6-レーティングシステム実装)
8. [フェーズ7: コマンド実装](#フェーズ7-コマンド実装)
9. [フェーズ8: テスト・検証](#フェーズ8-テスト検証)
10. [ファイル一覧](#ファイル一覧)
11. [最終チェックリスト](#最終チェックリスト)

---

## フェーズ概要

| フェーズ | 内容 | 推定作業量 |
|:---:|---|:---:|
| 1 | データベース拡張 | 小 |
| 2 | Config拡張 | 小 |
| 3 | SitAndGo.kt 本体実装 | 大 |
| 4 | イベント処理 | 小 |
| 5 | ルーレット演出実装 | 中 |
| 6 | レーティングシステム実装 | 中 |
| 7 | コマンド実装 | 小 |
| 8 | テスト・検証 | 中 |

### 推奨実装順序
```
1 (DB) → 2 (Config) → 3 (本体) → 4 (イベント) → 6 (レーティング) → 5 (ルーレット) → 7 (コマンド) → 8 (テスト)
```

### 依存関係図
```
フェーズ1 (DB) ──┐
                ├──► フェーズ3 (本体) ──► フェーズ4 (イベント)
フェーズ2 (Config)┘        │
                          ├──► フェーズ5 (ルーレット)
                          │
                          ▼
                    フェーズ6 (レーティング)
                          │
                          ▼
                    フェーズ7 (コマンド)
                          │
                          ▼
                    フェーズ8 (テスト)
```

---

## フェーズ1: データベース拡張

### 1.1 レーティングテーブル新規作成
```sql
CREATE TABLE IF NOT EXISTS sitandgo_rating (
    id INT UNSIGNED AUTO_INCREMENT,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    name VARCHAR(16) NULL,
    rating_internal INT DEFAULT 2500,
    games_played INT DEFAULT 0,
    wins INT DEFAULT 0,
    second_place INT DEFAULT 0,
    third_place INT DEFAULT 0,
    fourth_place INT DEFAULT 0,
    total_prize BIGINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY(id)
);
CREATE INDEX sitandgo_rating_uuid_index ON sitandgo_rating(uuid);
CREATE INDEX sitandgo_rating_rating_index ON sitandgo_rating(rating_internal DESC);
```

### 1.2 SitAndGoゲームログテーブル
```sql
CREATE TABLE IF NOT EXISTS sitandgo_log (
    id INT UNSIGNED AUTO_INCREMENT,
    start_time DATETIME,
    end_time DATETIME,
    buy_in BIGINT,
    multiplier DOUBLE,
    total_prize BIGINT,
    p1_uuid VARCHAR(36), p1_name VARCHAR(16), p1_rank INT, p1_prize BIGINT, p1_rating_before INT, p1_rating_after INT,
    p2_uuid VARCHAR(36), p2_name VARCHAR(16), p2_rank INT, p2_prize BIGINT, p2_rating_before INT, p2_rating_after INT,
    p3_uuid VARCHAR(36), p3_name VARCHAR(16), p3_rank INT, p3_prize BIGINT, p3_rating_before INT, p3_rating_after INT,
    p4_uuid VARCHAR(36), p4_name VARCHAR(16), p4_rank INT, p4_prize BIGINT, p4_rating_before INT, p4_rating_after INT,
    PRIMARY KEY(id)
);
```

### チェックリスト
- [ ] 1.1 sitandgo_rating テーブル作成SQL追加
- [ ] 1.2 sitandgo_log テーブル作成SQL追加
- [ ] 1.3 Main.kt の onEnable でテーブル自動作成処理追加

---

## フェーズ2: Config拡張

### 2.1 config.yml追加項目
**対象ファイル**: `src/main/resources/config.yml`

```yaml
sitandgo:
  enabled: true
  minBuyIn: 100           # 最低バイイン
  ratingMinBuyIn: 100000  # レート変動が発生する最低バイイン
  maxPlayers: 4
  waitTimeSeconds: 60     # 募集待機時間
  blindLevelSeconds: 180  # 1レベルあたりの秒数（3分）
  
  # タイムバンク設定
  timeBank:
    defaultTime: 15       # デフォルト持ち時間（秒）
    additionalTime: 15    # アディショナル持ち時間上限（秒）
    additionalPerTurn: 5  # 毎ターン追加されるアディショナル（秒）
    afkPenalty: 5         # 放置時のデフォルト減少（秒）
  
  # 倍率と確率（%）
  multiplierTable:
    "2.5": 16.0
    "3.0": 14.5
    "3.5": 13.5
    "4.0": 37.0
    "5.0": 8.0
    "6.0": 4.0
    "8.0": 3.0
    "10.0": 2.0
    "15.0": 0.7
    "20.0": 0.3
  
  # 倍率ごとの開始スタック（BB単位）
  stackByMultiplier:
    "2.5": 20
    "3.0": 25
    "3.5": 30
    "4.0": 30
    "5.0": 35
    "6.0": 40
    "8.0": 50
    "10.0": 60
    "15.0": 80
    "20.0": 100
  
  # ブラインドストラクチャ [SB, BB, BBA]
  blindStructure:
    - [1, 2, 2]
    - [1, 3, 3]
    - [2, 4, 4]
    - [3, 6, 6]
    - [5, 10, 10]
    - [7, 14, 14]
    - [10, 20, 20]
    - [15, 30, 30]
    - [20, 40, 40]
    - [30, 60, 60]
    - [40, 80, 80]
    - [50, 100, 100]
    - [70, 140, 140]
  
  # レーティング設定
  rating:
    initialRating: 2500
    maxDisplayRating: 5000
    eloScale: 8250
    baseK: 80
    baseMinutes: 16.8
    protectionThreshold: 1000
```

### 2.2 Config.kt拡張
**対象ファイル**: `src/main/java/ltotj/minecraft/texasholdem_kotlin/Config.kt`

追加メソッド:
```kotlin
fun getConfigurationSection(path: String): ConfigurationSection?
fun getList(path: String): List<*>?
fun getBoolean(path: String): Boolean
```

### チェックリスト
- [ ] 2.1 config.yml に sitandgo セクション追加
- [ ] 2.2 Config.kt にセクション取得メソッド追加

---

## フェーズ3: SitAndGo.kt 本体実装

### 3.1 クラス構造
**新規ファイル**: `src/main/java/ltotj/minecraft/texasholdem_kotlin/game/SitAndGo.kt`

```kotlin
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
    
    // ======== メソッド ========
    fun pickMultiplier(): Double { ... }
    fun getStartingStack(): Int { ... }
    fun calculatePrize(rank: Int): Long { ... }
    fun getCurrentBlinds(): Triple<Int, Int, Int> { ... }
    fun checkAndUpdateBlindLevel() { ... }
    fun recordElimination(playerUUID: UUID) { ... }
    fun getFinalRankings(): List<Pair<UUID, Int>> { ... }
    
    override fun run() { ... }
    fun playRouletteAnimation() { ... }
    fun endTournament() { ... }
}
```

### 3.2 内部クラス SitAndGoPlayerData
```kotlin
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
```

### 3.3 タイムバンクシステム

#### 仕様
| 項目 | 値 | 説明 |
|---|---|---|
| デフォルト持ち時間 | 15秒 | 毎ターン15秒にリセット（放置ペナルティ時は減少） |
| アディショナル持ち時間 | 上限15秒 | 毎ターン+5秒追加、余りは持ち越し |
| 放置ペナルティ | -5秒/回 | デフォルト持ち時間から減算、最小0秒 |
| 復活条件 | アクション実行 | デフォルト15秒に復活、afkCount=0 |

#### 時間消費順序
1. デフォルト持ち時間を消費
2. デフォルトが0になったらアディショナルを消費
3. 両方0でタイムアウト → 自動フォールド

#### 実装コード
```kotlin
// アクション開始時
fun startActionTimer(playerData: SitAndGoPlayerData) {
    // アディショナル追加（上限15秒）
    playerData.additionalTimeRemaining = minOf(15, playerData.additionalTimeRemaining + 5)
    
    // デフォルト持ち時間の計算（放置ペナルティ適用）
    val defaultTime = maxOf(0, 15 - (playerData.afkCount * 5))
    playerData.defaultTimeRemaining = defaultTime
    
    val totalTime = defaultTime + playerData.additionalTimeRemaining
    // タイマー開始...
}

// アクション完了時
fun onActionComplete(playerData: SitAndGoPlayerData, wasTimeout: Boolean) {
    if (wasTimeout) {
        // 放置: afkCount増加
        playerData.afkCount++
        playerData.fold()
    } else {
        // アクションあり: afkCount リセット
        playerData.afkCount = 0
    }
}
```

#### 放置シナリオ例
| 回数 | afkCount | デフォルト | アディショナル | 合計 |
|---:|---:|---:|---:|---:|
| 通常 | 0 | 15秒 | 0〜15秒 | 15〜30秒 |
| 1回放置後 | 1 | 10秒 | 5秒 | 15秒 |
| 2回放置後 | 2 | 5秒 | 5秒 | 10秒 |
| 3回放置後 | 3 | 0秒 | 5秒 | 5秒 |

### 3.4 倍率抽選ロジック
```kotlin
fun pickMultiplier(): Double {
    val table = mapOf(
        2.5 to 16.0, 3.0 to 14.5, 3.5 to 13.5, 4.0 to 37.0,
        5.0 to 8.0, 6.0 to 4.0, 8.0 to 3.0, 10.0 to 2.0,
        15.0 to 0.7, 20.0 to 0.3
    )
    val random = Random().nextDouble() * 100.0
    var cumulative = 0.0
    for ((mult, weight) in table) {
        cumulative += weight
        if (random < cumulative) return mult
    }
    return 2.5
}
```

### 3.5 賞金計算
```kotlin
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
```

### 3.6 ブラインドレベル管理
```kotlin
fun checkAndUpdateBlindLevel(): Boolean {
    val elapsed = System.currentTimeMillis() - blindLevelStartTime
    val levelDuration = 180 * 1000L  // 3分
    val newLevel = (elapsed / levelDuration).toInt()
    if (newLevel > currentBlindLevel) {
        currentBlindLevel = minOf(newLevel, blindStructure.size - 1)
        return true
    }
    return false
}

fun getCurrentBlinds(): Triple<Int, Int, Int> {
    val structure = listOf(
        Triple(1, 2, 2), Triple(1, 3, 3), Triple(2, 4, 4),
        Triple(3, 6, 6), Triple(5, 10, 10), Triple(7, 14, 14),
        Triple(10, 20, 20), Triple(15, 30, 30), Triple(20, 40, 40),
        Triple(30, 60, 60), Triple(40, 80, 80), Triple(50, 100, 100),
        Triple(70, 140, 140)
    )
    return structure[minOf(currentBlindLevel, structure.size - 1)]
}
```

### 3.7 GUI情報表示

プレイ中はsendMessageが見えないため、**GUI内で全ての情報を表示**する。

#### 表示項目
| スロット | 表示内容 | アイテム | 更新タイミング |
|---:|---|---|---|
| 18 | 次レベルまでの時間 | CLOCK | 毎秒 |
| 19 | 現在のブラインド | GOLD_NUGGET | レベルアップ時 |
| 26 | 自分のレート | EXPERIENCE_BOTTLE | ゲーム開始時 |
| 27 | 倍率・賞金プール | SUNFLOWER | ゲーム開始時 |

#### 実装
```kotlin
fun updateBlindInfoGUI() {
    val (sb, bb, bba) = getCurrentBlinds()
    val nextLevelIn = getSecondsUntilNextLevel()
    
    // スロット18: 次レベルまでの時間
    val clockItem = ItemStack(Material.CLOCK, maxOf(1, minOf(64, nextLevelIn)))
    clockItem.itemMeta = clockItem.itemMeta?.apply {
        displayName(Component.text("§e次レベルまで §f${nextLevelIn}秒"))
        lore(listOf(
            Component.text("§7現在: Lv.${currentBlindLevel + 1}"),
            Component.text("§7次: SB:${getNextBlinds().first} / BB:${getNextBlinds().second}")
        ))
    }
    
    // スロット19: 現在のブラインド
    val blindItem = ItemStack(Material.GOLD_NUGGET)
    blindItem.itemMeta = blindItem.itemMeta?.apply {
        displayName(Component.text("§6SB:$sb / BB:$bb / BBA:$bba"))
    }
    
    // スロット26: レート表示
    val ratingItem = ItemStack(Material.EXPERIENCE_BOTTLE)
    ratingItem.itemMeta = ratingItem.itemMeta?.apply {
        displayName(Component.text("§bあなたのレート: §f${playerData.ratingBefore}"))
        if (buyIn >= 100000) {
            lore(listOf(Component.text("§a✓ レート変動あり")))
        } else {
            lore(listOf(Component.text("§7レート変動なし（10万以上で変動）")))
        }
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
        pd.playerGUI.inv.setItem(26, ratingItem)  // 各自のレート
        pd.playerGUI.inv.setItem(27, prizeItem)
    }
}

fun getSecondsUntilNextLevel(): Int {
    val elapsed = System.currentTimeMillis() - blindLevelStartTime
    val levelDuration = 180 * 1000L
    val currentLevelElapsed = elapsed % levelDuration
    return ((levelDuration - currentLevelElapsed) / 1000).toInt()
}
```

### 3.8 トーナメント終了処理
```kotlin
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
    if (buyIn >= 100000) {
        updateRatings(rankings)
    }
    
    // ログ保存
    saveTournamentLog()
    
    // 結果表示（GUI + チャット）
    sendTournamentResult()
}

fun sendTournamentResult() {
    // ゲーム終了後なのでチャットで見える
    val messages = listOf(
        "§4§l============ §eSit & Go Result §4§l============",
        "§e倍率: §6§l${multiplier}x §7(賞金プール: ${(buyIn * 4 * multiplier).toLong()})",
        "",
        "§6§l🏆 1位: ${rank1Player.name} §e+${prize1}",
        "§f§l🥈 2位: ${rank2Player.name} §e+${prize2}",
        "§7§l🥉 3位: ${rank3Player.name} §e+${prize3}",
        "§8   4位: ${rank4Player.name}",
        "",
        if (buyIn >= 100000) "§7レート変動: ..." else "§7レート変動なし",
        "§4§l=========================================="
    )
    for (playerData in playerList) {
        for (msg in messages) playerData.player.sendMessage(msg)
    }
}
```

### チェックリスト
- [ ] 3.1 SitAndGo.kt 基本クラス作成
- [ ] 3.2 TournamentPhase enum 実装
- [ ] 3.3 SitAndGoPlayerData クラス実装
- [ ] 3.4 タイムバンクシステム実装
- [ ] 3.5 pickMultiplier() 倍率抽選実装
- [ ] 3.6 getStartingStack() スタック計算実装
- [ ] 3.7 calculatePrize() 賞金計算実装
- [ ] 3.8 ブラインドレベル管理実装
- [ ] 3.9 GUI情報表示実装（時間、ブラインド、レート、倍率）
- [ ] 3.10 順位確定ロジック実装
- [ ] 3.11 run() メインゲームループ実装
- [ ] 3.12 endTournament() 終了処理実装

---

## フェーズ4: イベント処理

### 4.1 SitAndGo_Event.kt
**新規ファイル**: `src/main/java/ltotj/minecraft/texasholdem_kotlin/game/event/SitAndGo_Event.kt`

```kotlin
object SitAndGo_Event : Listener {
    
    // GUIクリックイベント
    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        // アクションボタン処理（既存TexasHoldem_Eventを参考に）
    }
    
    // インベントリを閉じた時
    // → 何もしない（参加扱いのまま、既存テキサスと同じ仕様）
    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        // 特に処理なし
    }
    
    // プレイヤー切断時 → 自動フォールド
    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) {
        val table = getSitAndGoTable(e.player) ?: return
        val playerData = table.getPlData(e.player.uniqueId) ?: return
        
        // 自動フォールド
        playerData.fold()
    }
    
    // プレイヤーキック時
    @EventHandler
    fun onPlayerKick(e: PlayerKickEvent) {
        // onPlayerQuitと同様
    }
}
```

### チェックリスト
- [ ] 4.1 SitAndGo_Event.kt 作成
- [ ] 4.2 GUIクリック処理実装
- [ ] 4.3 切断時の自動フォールド処理
- [ ] 4.4 Main.kt でイベント登録

---

## フェーズ5: ルーレット演出実装

### 5.1 ルーレットデータクラス
**新規ファイル**: `src/main/java/ltotj/minecraft/texasholdem_kotlin/game/utility/RouletteDisplay.kt`

```kotlin
data class RouletteItem(
    val material: Material,
    val multiplier: Double,
    val displayName: String
)

object RouletteDisplay {
    val REEL_ITEMS = listOf(
        RouletteItem(Material.COPPER_INGOT, 2.5, "§7§l2.5x"),
        RouletteItem(Material.COPPER_INGOT, 3.0, "§7§l3.0x"),
        RouletteItem(Material.COPPER_INGOT, 3.5, "§7§l3.5x"),
        RouletteItem(Material.COPPER_INGOT, 4.0, "§7§l4.0x"),
        RouletteItem(Material.GOLD_INGOT, 4.0, "§6§l4.0x"),
        RouletteItem(Material.GOLD_INGOT, 5.0, "§6§l5.0x"),
        RouletteItem(Material.GOLD_BLOCK, 6.0, "§e§l6.0x"),
        RouletteItem(Material.GOLD_BLOCK, 8.0, "§e§l8.0x"),
        RouletteItem(Material.DIAMOND, 10.0, "§b§l10.0x"),
        RouletteItem(Material.DIAMOND_BLOCK, 15.0, "§b§l§n15.0x"),
        RouletteItem(Material.NETHER_STAR, 20.0, "§d§l§n✦20.0x✦")
    )
    
    fun getItemForMultiplier(multiplier: Double): RouletteItem {
        return REEL_ITEMS.find { it.multiplier == multiplier } ?: REEL_ITEMS[0]
    }
    
    fun getReelIndex(multiplier: Double): Int {
        return REEL_ITEMS.indexOfFirst { it.multiplier == multiplier }.takeIf { it >= 0 } ?: 0
    }
}
```

### 5.2 ルーレット演出メソッド
```kotlin
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
        
        // GUI更新（スロット20-24を使用）
        displayRouletteFrame(item)
        
        // カチカチ音
        playSoundAlPl(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F + (step % 3) * 0.1F)
        
        // 減速計算
        val progress = step.toDouble() / totalSteps
        delay = (50 + (maxDelay - 50) * progress.pow(2)).toLong()
        
        sleep(delay)
    }
    
    // 停止演出
    playStopEffect(targetMultiplier)
    sleep(2000)
    
    phase = TournamentPhase.PLAYING
}

fun playStopEffect(multiplier: Double) {
    when {
        multiplier >= 20.0 -> {
            playSoundAlPl(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F)
            repeat(5) {
                playSoundAlPl(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0F)
                sleep(200)
            }
        }
        multiplier >= 10.0 -> {
            playSoundAlPl(Sound.ENTITY_PLAYER_LEVELUP, 1.0F)
            playSoundAlPl(Sound.BLOCK_BEACON_ACTIVATE, 1.0F)
        }
        multiplier >= 6.0 -> {
            playSoundAlPl(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F)
        }
        else -> {
            playSoundAlPl(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F)
        }
    }
}
```

### チェックリスト
- [ ] 5.1 RouletteDisplay.kt 作成
- [ ] 5.2 playRouletteAnimation() 実装
- [ ] 5.3 displayRouletteFrame() GUI表示実装
- [ ] 5.4 playStopEffect() 停止エフェクト実装

---

## フェーズ6: レーティングシステム実装

### 6.1 レーティング計算クラス
**新規ファイル**: `src/main/java/ltotj/minecraft/texasholdem_kotlin/rating/SitAndGoRating.kt`

```kotlin
object SitAndGoRating {
    const val INITIAL_RATING = 2500
    const val ELO_SCALE = 8250.0
    const val BASE_K = 80.0
    const val BASE_MINUTES = 16.8
    const val PROTECTION_THRESHOLD = 1000
    
    fun expectedWinRate(ratingA: Int, ratingB: Int): Double {
        return 1.0 / (1.0 + 10.0.pow((ratingB - ratingA) / ELO_SCALE))
    }
    
    fun calculateK(estimatedMinutes: Double): Double {
        return BASE_K * sqrt(estimatedMinutes / BASE_MINUTES)
    }
    
    fun estimateGameMinutes(startingBB: Int): Double {
        return when {
            startingBB <= 20 -> 13.9
            startingBB <= 25 -> 15.0
            startingBB <= 30 -> 16.8
            startingBB <= 35 -> 18.0
            startingBB <= 40 -> 19.3
            startingBB <= 50 -> 21.0
            startingBB <= 60 -> 22.5
            startingBB <= 80 -> 24.8
            else -> 27.0
        }
    }
    
    fun calculateRatingChanges(
        players: List<Triple<UUID, Int, Int>>,  // UUID, rating, rank
        startingBB: Int
    ): Map<UUID, Int> {
        val minutes = estimateGameMinutes(startingBB)
        val k = calculateK(minutes)
        
        // Step 1: 基本変動量（ペアワイズ）
        val baseDeltas = mutableMapOf<UUID, Double>()
        for (player in players) {
            var delta = 0.0
            for (opponent in players) {
                if (player.first == opponent.first) continue
                val expected = expectedWinRate(player.second, opponent.second)
                val actual = if (player.third < opponent.third) 1.0 else 0.0
                delta += (actual - expected)
            }
            baseDeltas[player.first] = (k / 3.0) * delta
        }
        
        // Step 2: 2位保証補正
        val rank2Player = players.find { it.third == 2 }!!
        val baseRank2Delta = baseDeltas[rank2Player.first]!!
        val b = maxOf(0.0, 1.0 - baseRank2Delta)
        
        // Step 3: 初心者保護
        val result = mutableMapOf<UUID, Int>()
        for (player in players) {
            val baseDelta = baseDeltas[player.first]!!
            val finalDelta = when (player.third) {
                1 -> baseDelta
                2 -> baseDelta + b
                3, 4 -> {
                    if (player.second <= PROTECTION_THRESHOLD) {
                        baseDelta  // 負担免除
                    } else {
                        baseDelta - (b / 2.0)
                    }
                }
                else -> baseDelta
            }
            result[player.first] = finalDelta.roundToInt()
        }
        
        return result
    }
}
```

### 6.2 レーティングDB操作クラス
**新規ファイル**: `src/main/java/ltotj/minecraft/texasholdem_kotlin/rating/RatingRepository.kt`

```kotlin
class RatingRepository(private val mysql: MySQLManager) {
    
    fun getRating(uuid: UUID): Int {
        val result = mysql.query(
            "SELECT rating_internal FROM sitandgo_rating WHERE uuid='$uuid'"
        ) ?: return SitAndGoRating.INITIAL_RATING
        return if (result.next()) {
            result.getInt("rating_internal")
        } else {
            SitAndGoRating.INITIAL_RATING
        }.also { result.close(); mysql.close() }
    }
    
    fun updateRating(uuid: UUID, name: String, newRating: Int, rank: Int, prize: Long) {
        val clampedRating = maxOf(0, newRating)
        val rankColumn = when(rank) {
            1 -> "wins"
            2 -> "second_place"
            3 -> "third_place"
            4 -> "fourth_place"
            else -> "games_played"
        }
        
        mysql.execute("""
            INSERT INTO sitandgo_rating (uuid, name, rating_internal, games_played, $rankColumn, total_prize)
            VALUES ('$uuid', '$name', $clampedRating, 1, 1, $prize)
            ON DUPLICATE KEY UPDATE
                name = '$name',
                rating_internal = $clampedRating,
                games_played = games_played + 1,
                $rankColumn = $rankColumn + 1,
                total_prize = total_prize + $prize,
                updated_at = NOW()
        """)
    }
    
    fun getDisplayRating(internalRating: Int): Int {
        return internalRating.coerceIn(0, 5000)
    }
    
    fun getTopRatings(limit: Int = 10): List<RatingEntry> {
        val result = mysql.query(
            "SELECT uuid, name, rating_internal, games_played, wins FROM sitandgo_rating ORDER BY rating_internal DESC LIMIT $limit"
        ) ?: return emptyList()
        
        val entries = mutableListOf<RatingEntry>()
        while (result.next()) {
            entries.add(RatingEntry(
                UUID.fromString(result.getString("uuid")),
                result.getString("name"),
                result.getInt("rating_internal"),
                result.getInt("games_played"),
                result.getInt("wins")
            ))
        }
        result.close()
        mysql.close()
        return entries
    }
}

data class RatingEntry(
    val uuid: UUID,
    val name: String,
    val rating: Int,
    val gamesPlayed: Int,
    val wins: Int
)
```

### チェックリスト
- [ ] 6.1 SitAndGoRating.kt 計算ロジック実装
- [ ] 6.2 expectedWinRate() 実装
- [ ] 6.3 calculateK() 実装
- [ ] 6.4 calculateRatingChanges() 実装（2位保証込み）
- [ ] 6.5 初心者保護ロジック実装
- [ ] 6.6 RatingRepository.kt DB操作クラス実装
- [ ] 6.7 getRating() / updateRating() 実装
- [ ] 6.8 getTopRatings() 実装

---

## フェーズ7: コマンド実装

### 7.1 SitAndGo_Command.kt
**新規ファイル**: `src/main/java/ltotj/minecraft/texasholdem_kotlin/game/command/SitAndGo_Command.kt`

```kotlin
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
        if (sender !is Player) return
        val buyIn = args.getOrNull(1)?.toLongOrNull() ?: run {
            sender.sendMessage("§c/sng start <バイイン金額>")
            return
        }
        // 最低バイインチェック
        // 所持金チェック
        // テーブル作成
        // 募集開始
    }
    
    // /sng join <host>
    private fun handleJoin(sender: CommandSender, args: Array<out String>) { ... }
    
    // /sng leave
    private fun handleLeave(sender: CommandSender) { ... }
    
    // /sng rating [player]
    private fun handleRating(sender: CommandSender, args: Array<out String>) { ... }
    
    // /sng top
    private fun handleTop(sender: CommandSender) { ... }
    
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
    
    override fun onTabComplete(...): List<String> {
        return when (args.size) {
            1 -> listOf("start", "join", "leave", "rating", "top", "help")
                .filter { it.startsWith(args[0], true) }
            2 -> when (args[0]) {
                "join" -> getActiveHosts()
                "rating" -> null  // オンラインプレイヤー
                else -> emptyList()
            }
            else -> emptyList()
        } ?: emptyList()
    }
}
```

### 7.2 plugin.yml更新
```yaml
commands:
  poker:
    description: Texas Holdem commands
    usage: /poker <args>
  sng:
    description: Sit and Go tournament commands
    usage: /sng <start|join|leave|rating|top|help>
    aliases: [sitandgo]
```

### 7.3 Main.kt更新
```kotlin
// Companion objectに追加
lateinit var sitAndGoTables: HashMap<UUID, SitAndGo>

// onEnable内に追加
sitAndGoTables = HashMap()
getCommand("sng")!!.setExecutor(SitAndGo_Command)
server.pluginManager.registerEvents(SitAndGo_Event, this)

// DB初期化（executor内）
mysql.execute("CREATE TABLE IF NOT EXISTS sitandgo_rating ...")
mysql.execute("CREATE TABLE IF NOT EXISTS sitandgo_log ...")
```

### チェックリスト
- [ ] 7.1 SitAndGo_Command.kt 作成
- [ ] 7.2 /sng start 実装
- [ ] 7.3 /sng join 実装
- [ ] 7.4 /sng leave 実装
- [ ] 7.5 /sng rating 実装
- [ ] 7.6 /sng top 実装
- [ ] 7.7 plugin.yml にコマンド追加
- [ ] 7.8 Main.kt にコマンド登録・テーブル追加
- [ ] 7.9 TabCompleter 実装

---

## フェーズ8: テスト・検証

### 8.1 単体テスト項目
- [ ] 倍率抽選が確率通りか（10万回シミュレーション）
- [ ] 期待値 ≈ 4.0 の確認
- [ ] 賞金計算が正しいか（全倍率パターン）
- [ ] レーティング計算が正しいか
- [ ] 2位保証が機能しているか
- [ ] 初心者保護（R1000以下）が機能しているか
- [ ] レート下限0が守られているか
- [ ] タイムバンクが正しく動作するか

### 8.2 結合テスト項目
- [ ] 4人揃うまで開始しないことの確認
- [ ] ルーレット演出の動作確認
- [ ] ブラインドレベルが3分ごとに上がることの確認
- [ ] GUI情報表示（時間、ブラインド、レート）の確認
- [ ] 脱落順位が正しく記録されることの確認
- [ ] 賞金が正しくVault経由で支払われることの確認
- [ ] レート変動がDB保存されることの確認
- [ ] バイイン10万未満ではレート変動しないことの確認

### 8.3 エッジケーステスト
- [ ] 同時脱落時の順位決定（スタック→ポジション）
- [ ] 途中切断プレイヤーの自動フォールド
- [ ] 放置プレイヤーのタイムバンク減少

### チェックリスト
- [ ] 8.1 シミュレーションコード作成
- [ ] 8.2 期待値検証実行
- [ ] 8.3 ローカルテスト環境構築
- [ ] 8.4 全倍率での賞金配分テスト
- [ ] 8.5 レーティング変動テスト
- [ ] 8.6 タイムバンクテスト
- [ ] 8.7 本番環境デプロイ
- [ ] 8.8 本番動作確認

---

## ファイル一覧

### 新規作成（6ファイル）
| ファイルパス | 説明 |
|---|---|
| `game/SitAndGo.kt` | メインクラス |
| `game/event/SitAndGo_Event.kt` | イベントハンドラ |
| `game/utility/RouletteDisplay.kt` | ルーレット演出 |
| `game/command/SitAndGo_Command.kt` | コマンド処理 |
| `rating/SitAndGoRating.kt` | レート計算ロジック |
| `rating/RatingRepository.kt` | レートDB操作 |

### 既存修正（4ファイル）
| ファイルパス | 変更内容 |
|---|---|
| `Main.kt` | テーブル追加、コマンド登録、イベント登録、DB初期化 |
| `Config.kt` | セクション取得メソッド追加 |
| `resources/config.yml` | sitandgoセクション追加 |
| `resources/plugin.yml` | コマンド追加 |

### 変更不要
| ファイルパス | 理由 |
|---|---|
| `game/TexasHoldem.kt` | 継承で対応可能 |
| `resources/db.sql` | Main.ktで動的に作成 |

---

## 最終チェックリスト

### フェーズ1: DB
- [ ] sitandgo_rating テーブル作成
- [ ] sitandgo_log テーブル作成
- [ ] Main.kt onEnable でテーブル自動作成

### フェーズ2: Config
- [ ] config.yml sitandgo セクション追加
- [ ] Config.kt メソッド追加

### フェーズ3: 本体
- [ ] SitAndGo.kt 基本構造
- [ ] TournamentPhase enum
- [ ] SitAndGoPlayerData クラス
- [ ] タイムバンクシステム
- [ ] pickMultiplier() 倍率抽選
- [ ] calculatePrize() 賞金計算
- [ ] ブラインドレベル管理
- [ ] GUI情報表示（時間、ブラインド、レート、倍率）
- [ ] 順位確定ロジック
- [ ] run() メインループ
- [ ] endTournament() 終了処理

### フェーズ4: イベント
- [ ] SitAndGo_Event.kt 作成
- [ ] GUIクリック処理
- [ ] 切断時の自動フォールド
- [ ] Main.kt イベント登録

### フェーズ5: ルーレット
- [ ] RouletteDisplay.kt 作成
- [ ] playRouletteAnimation() 実装
- [ ] 倍率別エフェクト実装

### フェーズ6: レーティング
- [ ] SitAndGoRating.kt 計算ロジック
- [ ] RatingRepository.kt DB操作
- [ ] 2位保証ロジック
- [ ] 初心者保護ロジック
- [ ] バイイン10万判定

### フェーズ7: コマンド
- [ ] SitAndGo_Command.kt 作成
- [ ] /sng start, join, leave, rating, top
- [ ] plugin.yml コマンド追加
- [ ] Main.kt コマンド登録
- [ ] TabCompleter

### フェーズ8: テスト
- [ ] 倍率シミュレーション
- [ ] 賞金計算テスト
- [ ] レート計算テスト
- [ ] タイムバンクテスト
- [ ] 結合テスト
- [ ] 本番デプロイ
