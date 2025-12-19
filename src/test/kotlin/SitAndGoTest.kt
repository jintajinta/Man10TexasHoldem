package ltotj.minecraft.texasholdem_kotlin.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

/**
 * SitAndGo倍率・レート計算テスト
 * IntelliJで右クリック → Run 'SitAndGoTest' で実行
 */
class SitAndGoTest {
    
    private val multiplierTable = mapOf(
        2.5 to 17.3,
        3.0 to 14.6,
        3.5 to 13.5,
        4.0 to 39.0,
        5.0 to 7.0,
        6.0 to 3.7,
        8.0 to 2.0,
        10.0 to 1.9,
        15.0 to 0.7,
        20.0 to 0.3
    )
    
    // ==================== 倍率抽選テスト ====================
    
    @Test
    @DisplayName("倍率抽選: 確率分布が期待通りか")
    fun testMultiplierDistribution() {
        val iterations = 100000
        val results = mutableMapOf<Double, Int>()
        multiplierTable.keys.forEach { results[it] = 0 }
        
        repeat(iterations) {
            val mult = pickMultiplier()
            results[mult] = results.getOrDefault(mult, 0) + 1
        }
        
        println("【倍率抽選テスト】 試行回数: $iterations")
        println("倍率     | 期待確率 | 実際確率 | 誤差")
        println("-".repeat(50))
        
        for ((mult, expectedPercent) in multiplierTable) {
            val count = results[mult] ?: 0
            val actualPercent = count.toDouble() / iterations * 100
            val error = abs(actualPercent - expectedPercent)
            
            println("${mult}x".padEnd(9) + "| ${expectedPercent}%".padEnd(10) + 
                    "| ${String.format("%.2f", actualPercent)}%".padEnd(10) + 
                    "| ${String.format("%.2f", error)}%")
            
            // 誤差が1%以内であることを確認
            assertTrue(error < 1.0, "倍率 ${mult}x の誤差が1%を超えています: $error%")
        }
    }
    
    @Test
    @DisplayName("倍率抽選: 期待リターンが3.9925x（還元率99.8%）であること")
    fun testExpectedReturn() {
        var expectedReturn = 0.0
        for ((mult, percent) in multiplierTable) {
            expectedReturn += percent / 100 * mult
        }
        
        println("期待リターン: ${String.format("%.4f", expectedReturn)}x")
        println("還元率: ${String.format("%.2f", expectedReturn / 4 * 100)}%")
        
        // 期待リターンが3.9925x（還元率99.8%）であることを確認（誤差±0.01）
        assertEquals(3.9925, expectedReturn, 0.01, "期待リターンが3.9925xではありません: $expectedReturn")
    }
    
    @Test
    @DisplayName("倍率抽選: 確率の合計が100%であること")
    fun testProbabilitySum() {
        val sum = multiplierTable.values.sum()
        assertEquals(100.0, sum, 0.01, "確率の合計が100%ではありません: $sum")
    }
    
    private fun pickMultiplier(): Double {
        val random = Random.nextDouble() * 100.0
        var cumulative = 0.0
        for ((mult, weight) in multiplierTable) {
            cumulative += weight
            if (random < cumulative) return mult
        }
        return 2.5
    }
    
    // ==================== レート計算テスト ====================
    
    @Test
    @DisplayName("レート計算: ゼロサムであること")
    fun testRatingZeroSum() {
        val ratings = listOf(1500, 1500, 1500, 1500)
        val ranks = listOf(1, 2, 3, 4)
        
        val changes = calculateRatingChanges(ratings, ranks, 30)
        val sum = changes.values.sum()
        
        println("レート変動: $changes")
        println("合計: $sum")
        
        assertEquals(0, sum, "レート変動の合計がゼロではありません: $sum")
    }
    
    @Test
    @DisplayName("レート計算: 同レートで1位が最も増加、4位が最も減少")
    fun testRatingOrderSameRating() {
        val ratings = listOf(1500, 1500, 1500, 1500)
        val ranks = listOf(1, 2, 3, 4)
        
        val changes = calculateRatingChanges(ratings, ranks, 30)
        
        println("全員同レート(1500)でのレート変動:")
        for (i in 0..3) {
            println("${ranks[i]}位: ${changes[i]} (${ratings[i]} → ${ratings[i] + changes[i]!!})")
        }
        
        assertTrue(changes[0]!! > changes[1]!!, "1位の変動が2位より小さい")
        assertTrue(changes[1]!! > changes[2]!!, "2位の変動が3位より小さい")
        assertTrue(changes[2]!! > changes[3]!!, "3位の変動が4位より小さい")
        assertTrue(changes[0]!! > 0, "1位の変動がプラスではない")
        assertTrue(changes[3]!! < 0, "4位の変動がマイナスではない")
    }
    
    @Test
    @DisplayName("レート計算: 低レートが1位で大きく増加")
    fun testLowRatingWinsMore() {
        val ratings = listOf(1000, 1200, 1500, 2000)  // 低→高
        val ranks = listOf(1, 2, 3, 4)  // 低レートが1位
        
        val changes = calculateRatingChanges(ratings, ranks, 30)
        
        println("低レートが勝った場合:")
        for (i in 0..3) {
            println("${ranks[i]}位 (${ratings[i]}): ${changes[i]} → ${ratings[i] + changes[i]!!}")
        }
        
        // 低レート(1000)が1位になると大きく増加
        assertTrue(changes[0]!! > 50, "低レートが勝った時の増加が小さすぎる: ${changes[0]}")
    }
    
    @Test
    @DisplayName("レート計算: 高レートが1位で小さく増加")
    fun testHighRatingWinsLess() {
        val ratings = listOf(2000, 1500, 1200, 1000)  // 高→低
        val ranks = listOf(1, 2, 3, 4)  // 高レートが1位
        
        val changes = calculateRatingChanges(ratings, ranks, 30)
        
        println("高レートが勝った場合:")
        for (i in 0..3) {
            println("${ranks[i]}位 (${ratings[i]}): ${changes[i]} → ${ratings[i] + changes[i]!!}")
        }
        
        // 高レート(2000)が1位になっても小さく増加
        assertTrue(changes[0]!! < 30, "高レートが勝った時の増加が大きすぎる: ${changes[0]}")
    }
    
    @Test
    @DisplayName("レート計算: スタックサイズでK係数が変わる")
    fun testKFactorByStack() {
        val ratings = listOf(1500, 1500, 1500, 1500)
        val ranks = listOf(1, 2, 3, 4)
        
        val changes20BB = calculateRatingChanges(ratings, ranks, 20)
        val changes60BB = calculateRatingChanges(ratings, ranks, 60)
        val changes100BB = calculateRatingChanges(ratings, ranks, 100)
        
        println("スタックサイズ別1位の変動:")
        println("20BB (ハイパーターボ): ${changes20BB[0]}")
        println("60BB (標準): ${changes60BB[0]}")
        println("100BB (ディープ): ${changes100BB[0]}")
        
        // スタックが深いほど変動が大きい
        assertTrue(changes20BB[0]!! < changes60BB[0]!!, "20BBより60BBの方が変動が小さい")
        assertTrue(changes60BB[0]!! < changes100BB[0]!!, "60BBより100BBの方が変動が小さい")
    }
    
    private fun calculateRatingChanges(ratings: List<Int>, ranks: List<Int>, startingBB: Int): Map<Int, Int> {
        val kFactor = getKFactor(startingBB)
        val changes = mutableMapOf<Int, Int>()
        
        for (i in ratings.indices) {
            var totalChange = 0.0
            
            for (j in ratings.indices) {
                if (i == j) continue
                
                val expectedScore = 1.0 / (1.0 + Math.pow(10.0, (ratings[j] - ratings[i]) / 400.0))
                val actualScore = when {
                    ranks[i] < ranks[j] -> 1.0
                    ranks[i] > ranks[j] -> 0.0
                    else -> 0.5
                }
                
                totalChange += kFactor * (actualScore - expectedScore)
            }
            
            changes[i] = Math.round(totalChange).toInt()
        }
        
        // ゼロサム調整
        val total = changes.values.sum()
        if (total != 0) {
            val maxIdx = changes.maxByOrNull { abs(it.value) }?.key ?: 0
            changes[maxIdx] = changes[maxIdx]!! - total
        }
        
        return changes
    }
    
    private fun getKFactor(startingBB: Int): Double {
        return when {
            startingBB <= 20 -> 24.0
            startingBB <= 40 -> 28.0
            startingBB <= 60 -> 32.0
            else -> 36.0
        }
    }
    
    // ==================== 賞金計算テスト ====================
    
    @Test
    @DisplayName("賞金計算: 10x未満は1位70%/2位30%")
    fun testPrizeDistributionUnder10x() {
        val buyIn = 100000L
        val multiplier = 4.0
        
        val totalPool = buyIn * 4 * multiplier
        val prize1 = calculatePrize(1, multiplier, totalPool)
        val prize2 = calculatePrize(2, multiplier, totalPool)
        val prize3 = calculatePrize(3, multiplier, totalPool)
        val prize4 = calculatePrize(4, multiplier, totalPool)
        
        println("10x未満 (${multiplier}x):")
        println("プール: $totalPool")
        println("1位: $prize1 (${prize1 * 100 / totalPool}%)")
        println("2位: $prize2 (${prize2 * 100 / totalPool}%)")
        println("3位: $prize3")
        println("4位: $prize4")
        
        assertEquals((totalPool * 0.70).toLong(), prize1, "1位の賞金が70%ではない")
        assertEquals((totalPool * 0.30).toLong(), prize2, "2位の賞金が30%ではない")
        assertEquals(0L, prize3, "3位の賞金が0ではない")
        assertEquals(0L, prize4, "4位の賞金が0ではない")
    }
    
    @Test
    @DisplayName("賞金計算: 10x以上は1位60%/2位30%/3位10%")
    fun testPrizeDistribution10xPlus() {
        val buyIn = 100000L
        val multiplier = 10.0
        
        val totalPool = buyIn * 4 * multiplier
        val prize1 = calculatePrize(1, multiplier, totalPool)
        val prize2 = calculatePrize(2, multiplier, totalPool)
        val prize3 = calculatePrize(3, multiplier, totalPool)
        val prize4 = calculatePrize(4, multiplier, totalPool)
        
        println("10x以上 (${multiplier}x):")
        println("プール: $totalPool")
        println("1位: $prize1 (${prize1 * 100 / totalPool}%)")
        println("2位: $prize2 (${prize2 * 100 / totalPool}%)")
        println("3位: $prize3 (${prize3 * 100 / totalPool}%)")
        println("4位: $prize4")
        
        assertEquals((totalPool * 0.60).toLong(), prize1, "1位の賞金が60%ではない")
        assertEquals((totalPool * 0.30).toLong(), prize2, "2位の賞金が30%ではない")
        assertEquals((totalPool * 0.10).toLong(), prize3, "3位の賞金が10%ではない")
        assertEquals(0L, prize4, "4位の賞金が0ではない")
    }
    
    @Test
    @DisplayName("賞金計算: 賞金合計がプールと一致")
    fun testPrizeSumEqualsPool() {
        val buyIn = 100000L
        
        for (multiplier in listOf(2.5, 4.0, 6.0, 10.0, 15.0, 20.0)) {
            val totalPool = buyIn * 4 * multiplier
            val total = (1..4).sumOf { calculatePrize(it, multiplier, totalPool) }
            
            assertEquals(totalPool.toLong(), total, "倍率${multiplier}xで賞金合計がプールと一致しない")
        }
    }
    
    private fun calculatePrize(rank: Int, multiplier: Double, totalPool: Double): Long {
        val distribution = if (multiplier >= 10.0) {
            mapOf(1 to 0.60, 2 to 0.30, 3 to 0.10, 4 to 0.0)
        } else {
            mapOf(1 to 0.70, 2 to 0.30, 3 to 0.0, 4 to 0.0)
        }
        return (totalPool * (distribution[rank] ?: 0.0)).toLong()
    }
    
    // ==================== 初心者保護テスト ====================
    
    @Test
    @DisplayName("初心者保護: 10ゲーム未満で4位の損失が50%軽減")
    fun testBeginnerProtection() {
        val ratings = listOf(1500, 1500, 1500, 1500)
        val ranks = listOf(1, 2, 3, 4)
        val games = listOf(50, 50, 50, 5)  // 最後のプレイヤーは初心者
        
        val normalChanges = calculateRatingChanges(ratings, ranks, 30)
        val protectedChanges = calculateRatingChangesWithProtection(ratings, ranks, games, 30)
        
        println("初心者保護テスト:")
        println("通常の4位変動: ${normalChanges[3]}")
        println("保護後の4位変動: ${protectedChanges[3]}")
        
        // 初心者の損失が約50%軽減されていること
        val normalLoss = abs(normalChanges[3]!!)
        val protectedLoss = abs(protectedChanges[3]!!)
        
        assertTrue(protectedLoss < normalLoss, "初心者保護が機能していない")
        assertTrue(protectedLoss <= normalLoss / 2 + 1, "損失軽減が50%ではない")
    }
    
    private fun calculateRatingChangesWithProtection(
        ratings: List<Int>, ranks: List<Int>, games: List<Int>, startingBB: Int
    ): Map<Int, Int> {
        val changes = calculateRatingChanges(ratings, ranks, startingBB).toMutableMap()
        
        for (i in ratings.indices) {
            if (games[i] < 10 && ranks[i] == 4 && changes[i]!! < 0) {
                changes[i] = changes[i]!! / 2
            }
        }
        
        // ゼロサム再調整（1位に調整）
        val total = changes.values.sum()
        if (total != 0) {
            val firstPlaceIdx = ranks.indexOf(1)
            changes[firstPlaceIdx] = changes[firstPlaceIdx]!! - total
        }
        
        return changes
    }
}
