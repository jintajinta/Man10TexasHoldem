package ltotj.minecraft.texasholdem_kotlin.rating

import ltotj.minecraft.texasholdem_kotlin.MySQLManager
import java.util.*

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
