    // /sng stop [host]
    private fun handleStop(sender: CommandSender, args: Array<out String>) {
        if (!sender.isOp) {
            sender.sendMessage("§cOP専用コマンドです")
            return
        }
        
        var targetHost: Player? = null
        
        if (args.size >= 2) {
            // 指定したホストのゲームを停止
            targetHost = Bukkit.getPlayer(args[1])
            if (targetHost == null) {
                sender.sendMessage("§cプレイヤーが見つかりません")
                return
            }
        } else if (sender is Player) {
            // 自分が参加している・主催しているゲームを停止
            val hostUUID = Main.currentPlayers[sender.uniqueId]
            if (hostUUID != null) {
                targetHost = Bukkit.getPlayer(hostUUID)
            }
        }
        
        if (targetHost == null) {
            sender.sendMessage("§c停止対象のゲームが見つかりません。/sng stop [ホスト名] で指定してください。")
            return
        }
        
        val table = Main.sitAndGoTables[targetHost.uniqueId]
        if (table == null) {
            sender.sendMessage("§c${targetHost.name} はSitAndGoを主催していません")
            return
        }
        
        sender.sendMessage("§c${targetHost.name} のゲームを強制終了します...")
        table.cancelTournament()
        Main.sitAndGoTables.remove(targetHost.uniqueId)
    }
