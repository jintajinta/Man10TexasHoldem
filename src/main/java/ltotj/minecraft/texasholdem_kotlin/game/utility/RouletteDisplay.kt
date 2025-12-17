package ltotj.minecraft.texasholdem_kotlin.game.utility

import org.bukkit.Material

data class RouletteItem(
    val material: Material,
    val multiplier: Double,
    val displayName: String
)

object RouletteDisplay {
    val REEL_ITEMS = listOf(
        RouletteItem(Material.WOODEN_SWORD, 2.5, "§7§l2.5x"),
        RouletteItem(Material.WOODEN_SWORD, 3.0, "§7§l3.0x"),
        RouletteItem(Material.WOODEN_SWORD, 3.5, "§7§l3.5x"),
        RouletteItem(Material.STONE_SWORD, 4.0, "§8§l4.0x"),
        RouletteItem(Material.IRON_SWORD, 5.0, "§f§l5.0x"),
        RouletteItem(Material.IRON_SWORD, 6.0, "§f§l6.0x"),
        RouletteItem(Material.DIAMOND_SWORD, 8.0, "§b§l8.0x"),
        RouletteItem(Material.DIAMOND_SWORD, 10.0, "§b§l10.0x"),
        RouletteItem(Material.NETHERITE_SWORD, 15.0, "§4§l§n15.0x"),
        RouletteItem(Material.TRIDENT, 20.0, "§d§l§n✦20.0x✦")
    )
    
    fun getItemForMultiplier(multiplier: Double): RouletteItem {
        return REEL_ITEMS.find { it.multiplier == multiplier } ?: REEL_ITEMS[0]
    }
    
    fun getReelIndex(multiplier: Double): Int {
        return REEL_ITEMS.indexOfFirst { it.multiplier == multiplier }.takeIf { it >= 0 } ?: 0
    }
}
