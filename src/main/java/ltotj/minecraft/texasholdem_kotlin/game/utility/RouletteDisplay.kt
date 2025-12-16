package ltotj.minecraft.texasholdem_kotlin.game.utility

import org.bukkit.Material

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
