package ltotj.minecraft.texasholdem_kotlin.game.utility

import ltotj.minecraft.texasholdem_kotlin.Main.Companion.con
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack


class PlayerGUI(private val seat: Int,name: String){

    val inv= Bukkit.createInventory(null, 54, Component.text(name))

    private fun createGUIItem(material: Material, amount: Int, name: String, lore: List<String>):ItemStack{
        val safeAmount = if (amount <= 0) 1 else amount
        val item=ItemStack(material, safeAmount)
        val meta=item.itemMeta
        meta.displayName(Component.text(name))
        meta.lore(listToComponent(lore))
        item.itemMeta = meta
        return item
    }

    private fun createGUIItem(material: Material, amount: Int, name: String):ItemStack{
        val safeAmount = if (amount <= 0) 1 else amount
        val item=ItemStack(material, safeAmount)
        val meta=item.itemMeta
        meta.displayName(Component.text(name))
        item.itemMeta = meta
        return item
    }

    private fun setGUIItem(slot: Int, material: Material, amount: Int, name: String, lore: List<String>){
        inv.setItem(slot, createGUIItem(material, amount, name, lore))
    }

    private fun setGUIItem(slot: Int, material: Material, name: String, lore: List<String>){
        inv.setItem(slot, createGUIItem(material, 1, name, lore))
    }

    private fun setGUIItem(slot: Int, material: Material, name: String){
        inv.setItem(slot, createGUIItem(material, 1, name))
    }

    fun setCard(slot: Int, card: Card){
        inv.setItem(slot, card.getCard())
    }

    fun setFaceDownCard(slot: Int){
        val material = Material.valueOf(con.getString("cardMaterial"))
        val item = ItemStack(material, 1)
        val meta = item.itemMeta
        meta.setCustomModelData(con.getInt("faceDownCard.customModelData"))
        meta.displayName(Component.text("???"))
        item.itemMeta = meta
        inv.setItem(slot,item)
    }

    private fun cardPosition(seat: Int):Int{
        when(seat){
            0 -> return 42
            1 -> return 37
            2 -> return 1
            3 -> return 6
        }
        return 0
    }

    private fun chipPosition(seat: Int):Int {
        when (seat) {
            0 -> return 35
            1 -> return 30
            2 -> return 12
            3 -> return 17
        }
        return 0
    }

    private fun listToComponent(list: List<String>):List<Component>{
        val cList=ArrayList<Component>()
        for(i in list.indices){
            cList.add(Component.text(list[i]))
        }
        return cList
    }

    fun enchantItem(slot:Int){
        val item=inv.getItem(slot)
        val meta=item?.itemMeta
        meta?.addEnchant(Enchantment.LURE, 1, true)
        item?.itemMeta = meta
        inv.setItem(slot,item)
    }

    fun setChips(seat: Int, amount: Int, rate: Int){
        setGUIItem(chipPosition(seat), Material.GOLD_NUGGET, amount, "§l§yチップ", listOf("§e計${amount}枚","§e一枚§e" + rate + "円"))
    }

    fun setCoin(seat: Int, name: String, amount: Int){
        setGUIItem(cardPosition(seat) + 2, Material.GOLD_INGOT, "§c§l$name§r§wのチップ", listOf("§e$amount§w枚"))
    }

    fun setPot(pot: Int){
        setGUIItem(25, Material.GOLD_BLOCK, "§6現在の賭けチップ合計", listOf("§e§l$pot§w枚"))
    }

    fun setWinner(evenOrOdd:Boolean,head:ItemStack){
        if(evenOrOdd){
            inv.setItem(20,head)
            setGUIItem(21,Material.GOLD_BLOCK,"§l§aW§bI§cN§dN§eE§dR§f!")
            inv.setItem(22,head)
            setGUIItem(23,Material.GOLD_BLOCK,"§l§aW§bI§cN§dN§eE§dR§f!")
            inv.setItem(24,head)
        }
        else{
            setGUIItem(20,Material.GOLD_BLOCK,"§l§aW§bI§cN§dN§eE§dR§f!")
            inv.setItem(21,head)
            setGUIItem(22,Material.GOLD_BLOCK,"§l§aW§bI§cN§dN§eE§dR§f!")
            inv.setItem(23,head)
            setGUIItem(24,Material.GOLD_BLOCK,"§l§aW§bI§cN§dN§eE§dR§f!")
        }
    }

    fun setDrawGame(){
        for(i in 0..4){
            setGUIItem(20+i,Material.BARRIER,"DRAW",listOf("引き分け 同率一位のプレイヤーに賞金が分配されます"))
        }
    }

    fun setRaiseButton(minBet: Int, isPreFlop: Boolean, pot: Int, currentBet: Int, bbAmount: Int, playerChips: Int, instBet: Int){
        for(i in 45..53){
            setGUIItem(i, Material.WHITE_STAINED_GLASS_PANE,"")
        }
        
        // 右側にメイン操作ボタン配置（50-53）
        setGUIItem(49,Material.WHITE_STAINED_GLASS_PANE,"")
        setGUIItem(50,Material.BARRIER,"§4§l戻る")
        setGUIItem(51,Material.RED_WOOL,"§c§l賭けチップを一枚減らす")
        setGUIItem(52,Material.GOLD_NUGGET,minBet , "§a§l以下の枚数でチップを上乗せする", listOf("§c" + minBet + "枚追加","§d最小上乗せ枚数は§e${minBet}枚§dです"))
        setGUIItem(53,Material.BLUE_WOOL,"§9§l賭けチップを一枚増やす")
        
        // 左側にショートカットボタン配置（45-48）
        if (isPreFlop) {
            // プリフロップ: 2.5bb, 3bb, 4bb, 5bb
            setQuickBetButton(45, bbAmount, 2.5, "2.5BB", playerChips, instBet)
            setQuickBetButton(46, bbAmount, 3.0, "3BB", playerChips, instBet)
            setQuickBetButton(47, bbAmount, 4.0, "4BB", playerChips, instBet)
            setQuickBetButton(48, bbAmount, 5.0, "5BB", playerChips, instBet)
        } else if (currentBet == 0) {
            // ポストフロップ（ベット）: 30%, 50%, 75%, pot
            setQuickBetButton(45, pot, 0.3, "30% pot", playerChips, instBet)
            setQuickBetButton(46, pot, 0.5, "50% pot", playerChips, instBet)
            setQuickBetButton(47, pot, 0.75, "75% pot", playerChips, instBet)
            setQuickBetButton(48, pot, 1.0, "pot", playerChips, instBet)
        } else {
            // ポストフロップ（レイズ）: 2.5x, 3x, 3.5x, 4x
            setQuickRaiseButton(45, currentBet, 2.5, "2.5x", playerChips, instBet)
            setQuickRaiseButton(46, currentBet, 3.0, "3x", playerChips, instBet)
            setQuickRaiseButton(47, currentBet, 3.5, "3.5x", playerChips, instBet)
            setQuickRaiseButton(48, currentBet, 4.0, "4x", playerChips, instBet)
        }
    }
    
    private fun setQuickBetButton(slot: Int, base: Int, multiplier: Double, label: String, playerChips: Int, instBet: Int) {
        val targetBet = (base * multiplier).toInt()
        val needChips = targetBet - instBet
        
        // スロット位置に応じた寒色→暖色のグラデーション
        val material = when(slot) {
            45 -> Material.LIGHT_BLUE_STAINED_GLASS_PANE  // 最小額 - 寒色（水色）
            46 -> Material.LIME_STAINED_GLASS_PANE        // 中小額 - 中間（ライム）
            47 -> Material.ORANGE_STAINED_GLASS_PANE      // 中大額 - 中間（オレンジ）
            48 -> Material.MAGENTA_STAINED_GLASS_PANE     // 最大額 - 暖色（マゼンタ）
            else -> Material.WHITE_STAINED_GLASS_PANE
        }
        
        val lore = if (needChips > playerChips) {
            listOf("§e§lオールイン")
        } else {
            listOf("§e${targetBet}枚")
        }
        setGUIItem(slot, material, "§b§l${label}", lore)
    }
    
    private fun setQuickRaiseButton(slot: Int, currentBet: Int, multiplier: Double, label: String, playerChips: Int, instBet: Int) {
        val targetBet = (currentBet * multiplier).toInt()
        val needChips = targetBet - instBet
        
        // スロット位置に応じた寒色→暖色のグラデーション
        val material = when(slot) {
            45 -> Material.LIGHT_BLUE_STAINED_GLASS_PANE  // 最小倍率 - 寒色（水色）
            46 -> Material.LIME_STAINED_GLASS_PANE        // 中小倍率 - 中間（ライム）
            47 -> Material.ORANGE_STAINED_GLASS_PANE      // 中大倍率 - 中間（オレンジ）
            48 -> Material.MAGENTA_STAINED_GLASS_PANE     // 最大倍率 - 暖色（マゼンタ）
            else -> Material.WHITE_STAINED_GLASS_PANE
        }
        
        val lore = if (needChips > playerChips) {
            listOf("§e§lオールイン")
        } else {
            listOf("§e${targetBet}枚")
        }
        setGUIItem(slot, material, "§d§l${label}", lore)
    }

    fun setTurnPBlo(seat: Int){
        setGUIItem(chipPosition(seat) - 3, Material.DIAMOND_BLOCK, "§l§wターンプレイヤー")
    }

    fun reloadRaiseButton(add:Int,minBet:Int){
        setGUIItem(52, Material.GOLD_NUGGET,add , "§a§l以下の枚数でチップを上乗せする", listOf("§c" + add + "枚追加","§d最小上乗せ枚数は§e${minBet}枚§dです"))
    }

    fun setActionButtons(){
        for (i in 45..53) {
            setActionButton(i)
        }
    }

    fun setActionButton(num:Int){
        when(num){
            46->setGUIItem(num,Material.BLUE_STAINED_GLASS_PANE,"§w§lフォールド")
            47->setGUIItem(num,Material.PINK_STAINED_GLASS_PANE,"§w§lレイズ")
            48->setGUIItem(num,Material.RED_STAINED_GLASS_PANE,"§w§lチェック")
            49->setGUIItem(num,Material.YELLOW_STAINED_GLASS_PANE,"§w§lベット")
            50->setGUIItem(num,Material.GREEN_STAINED_GLASS_PANE,"§w§lコール")
            53->setGUIItem(num,Material.BROWN_STAINED_GLASS_PANE,"§w§lオールイン")
            45,51,52->setGUIItem(num,Material.WHITE_STAINED_GLASS_PANE,"")
        }
    }

    fun removeButton(){
        for(i in 45..53){
            inv.setItem(i, ItemStack(Material.WHITE_STAINED_GLASS_PANE))
        }
    }
}