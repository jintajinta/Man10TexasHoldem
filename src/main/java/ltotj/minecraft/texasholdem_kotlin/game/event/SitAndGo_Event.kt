package ltotj.minecraft.texasholdem_kotlin.game.event

import ltotj.minecraft.texasholdem_kotlin.Main
import ltotj.minecraft.texasholdem_kotlin.game.SitAndGo
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack

object SitAndGo_Event : Listener {
    
    private fun clickSound(player: Player) {
        player.playSound(player.location, Sound.BLOCK_COMPOSTER_FILL, 2F, 2F)
    }
    
    private fun matchItem(item: ItemStack, slot: Int, key: String): Int {
        if (slot in 45..53) {
            when (key) {
                "texasholdeminv" -> {
                    when (slot) {
                        46 -> return if (item.type == Material.BLUE_STAINED_GLASS_PANE) slot else 0
                        47 -> return if (item.type == Material.PINK_STAINED_GLASS_PANE) slot else 0
                        48 -> return if (item.type == Material.RED_STAINED_GLASS_PANE) slot else 0
                        49 -> return if (item.type == Material.YELLOW_STAINED_GLASS_PANE) slot else 0
                        50 -> return if (item.type == Material.GREEN_STAINED_GLASS_PANE) slot else 0
                        53 -> return if (item.type == Material.BROWN_STAINED_GLASS_PANE) slot else 0
                    }
                }
                "raise" -> {
                    when (slot) {
                        50 -> return if (item.type == Material.BARRIER) slot else 0
                        51 -> return if (item.type == Material.RED_WOOL) slot else 0
                        52 -> return if (item.type == Material.GOLD_NUGGET) slot else 0
                        53 -> return if (item.type == Material.BLUE_WOOL) slot else 0
                        45, 46, 47, 48 -> {
                            if (item.type == Material.LIGHT_BLUE_STAINED_GLASS_PANE ||
                                item.type == Material.LIME_STAINED_GLASS_PANE ||
                                item.type == Material.ORANGE_STAINED_GLASS_PANE ||
                                item.type == Material.MAGENTA_STAINED_GLASS_PANE) {
                                return slot
                            }
                            return 0
                        }
                    }
                }
            }
        }
        return 0
    }
    
    // GUIクリックイベント
    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        if (e.view.title() != text("SitAndGo")) return
        e.isCancelled = true
        
        val player = e.whoClicked as Player
        val item = e.currentItem
        
        if (item != null && item.type != Material.AIR && item.type != Material.WHITE_STAINED_GLASS_PANE) {
            val table = getSitAndGoTable(player) ?: return
            val playerData = table.getPlData(player.uniqueId) ?: return
            
            if (!playerData.action) {
                clickSound(player)
                when (matchItem(item, e.slot, "texasholdeminv")) {
                    46 -> playerData.fold()
                    48 -> {
                        if (table.bet != 0) player.sendMessage("賭けチップが0でないのでチェックできません")
                        else playerData.preCall.set(true)
                    }
                    47, 49 -> if (table.bet - playerData.instBet <= playerData.playerChips) {
                        playerData.setRaiseMenu()
                    }
                    50 -> playerData.preCall.set(true)
                    53 -> playerData.allIn()
                }
                when (matchItem(item, e.slot, "raise")) {
                    50 -> {
                        playerData.setActionButtons()
                        playerData.addedChips = 0
                    }
                    51 -> playerData.downBet()
                    52 -> playerData.preCall.set(true)
                    53 -> playerData.raiseBet()
                    45, 46, 47, 48 -> {
                        val meta = item.itemMeta
                        val lore = meta?.lore()
                        if (lore != null && lore.isNotEmpty()) {
                            val firstLore = (lore[0] as Component).toString()
                            if (firstLore.contains("オールイン")) {
                                playerData.addedChips = playerData.playerChips + playerData.instBet - table.bet
                            } else {
                                val betMatch = Regex("""§e(\d+)枚""").find(firstLore)
                                if (betMatch != null) {
                                    val targetBet = betMatch.groupValues[1].toInt()
                                    val needAdd = targetBet - table.bet
                                    playerData.addedChips = if (needAdd > 0) needAdd else 0
                                }
                            }
                            playerData.playerGUI.reloadRaiseButton(table.bet + playerData.addedChips, table.lastRaise)
                        }
                    }
                }
            }
        }
    }
    
    // インベントリを閉じた時 → 何もしない（参加扱いのまま）
    @Suppress("UNUSED_PARAMETER")
    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        // 特に処理なし（既存テキサスと同じ仕様）
    }
    
    // プレイヤー切断時 → 自動フォールド
    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) {
        val table = getSitAndGoTable(e.player) ?: return
        table.getPlData(e.player.uniqueId)?.fold()
    }
    
    // プレイヤーキック時
    @EventHandler
    fun onPlayerKick(e: PlayerKickEvent) {
        val table = getSitAndGoTable(e.player) ?: return
        table.getPlData(e.player.uniqueId)?.fold()
    }
    
    // ヘルパーメソッド: プレイヤーが所属するSitAndGoテーブルを取得
    private fun getSitAndGoTable(player: Player): SitAndGo? {
        val masterUUID = Main.currentPlayers[player.uniqueId] ?: return null
        return Main.sitAndGoTables[masterUUID]
    }
}
