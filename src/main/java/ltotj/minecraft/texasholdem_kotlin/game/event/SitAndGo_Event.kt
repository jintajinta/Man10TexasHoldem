package ltotj.minecraft.texasholdem_kotlin.game.event

import ltotj.minecraft.texasholdem_kotlin.Main
import ltotj.minecraft.texasholdem_kotlin.game.SitAndGo
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent

object SitAndGo_Event : Listener {
    
    // GUIクリックイベント
    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        // TODO: アクションボタン処理（既存TexasHoldem_Eventを参考に実装）
    }
    
    // インベントリを閉じた時 → 何もしない（参加扱いのまま）
    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        // 特に処理なし（既存テキサスと同じ仕様）
    }
    
    // プレイヤー切断時 → 自動フォールド
    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) {
        val table = getSitAndGoTable(e.player) ?: return
        val playerData = table.playerList.find { it.player.uniqueId == e.player.uniqueId } ?: return
        
        // 自動フォールド
        playerData.fold()
    }
    
    // プレイヤーキック時
    @EventHandler
    fun onPlayerKick(e: PlayerKickEvent) {
        // onPlayerQuitと同様
        val table = getSitAndGoTable(e.player) ?: return
        val playerData = table.playerList.find { it.player.uniqueId == e.player.uniqueId } ?: return
        
        // 自動フォールド
        playerData.fold()
    }
    
    // ヘルパーメソッド: プレイヤーが所属するSitAndGoテーブルを取得
    private fun getSitAndGoTable(player: org.bukkit.entity.Player): SitAndGo? {
        val masterUUID = Main.currentPlayers[player.uniqueId] ?: return null
        return Main.sitAndGoTables[masterUUID]
    }
}
