package ltotj.minecraft.texasholdem_kotlin.rating

import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
