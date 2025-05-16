package bigtwo.app

import bigtwo.app.ai.AutoPlayer
import bigtwo.app.model.Card
import bigtwo.app.model.Deck
import bigtwo.app.model.HandType
import bigtwo.app.player.Player
import bigtwo.app.rules.RuleVariant
import bigtwo.app.rules.Rules
import java.io.PrintStream

data class PlayerInfo(val name: String, val isHuman: Boolean)
class GameManager(
    private val playerInfos: List<PlayerInfo> = listOf(
        PlayerInfo("玩家1", true),
        PlayerInfo("玩家2", false),
        PlayerInfo("玩家3", false),
        PlayerInfo("玩家4", false)
    ),
    private val ruleVariant: RuleVariant = RuleVariant.SOUTHERN,
    private val autoPlay: Boolean = true // 是否自动模拟出牌
) {
    private val rules = Rules(ruleVariant)
    private val players = playerInfos.map { Player(it.name, it.isHuman) }
    private val deck = Deck()
    private val autoPlayer = AutoPlayer(rules)
    private var isFirstPlay: Boolean = true
    // 当前游戏状态
    private var currentPlayerIndex = 0
    private var previousHand: List<Card>? = null
    private var lastPlayedBy: Player? = null
    private var gameEnded = false

    // 标记是否为游戏首轮
    private var isInitialTurn: Boolean = true

    // 添加过牌状态跟踪
    private val playerPassStatus = mutableMapOf<Player, Boolean>()

    // 添加最后出牌玩家的索引
    private var lastPlayerWhoPlayedIndex = -1

    // 添加连续过牌计数
    private var consecutivePassCount = 0

    // 初始化游戏
    fun initGame() {
        // 发牌
        val hands = deck.deal()
        players.forEachIndexed { index, player ->
            player.receiveCards(hands[index])
            playerPassStatus[player] = false  // 初始化过牌状态
        }

        // 确定首出玩家（持有方块3的玩家）
        currentPlayerIndex = players.indexOfFirst { rules.hasStartingCard(it) }
        println("游戏开始，${players[currentPlayerIndex].name}首先出牌(持有方块3)")
        consecutivePassCount = 0
    }

    fun showFirstPlayer(): Player {
        println("首位出牌玩家是 ${players[currentPlayerIndex].name}")
        return players[currentPlayerIndex]
    }

    fun showPlayer(index: Int): Player {
        return players[index]
    }

    fun showgameended(): Boolean {
        return players.any { it.hasWon() }
    }

    fun getPlayers(): List<Player> = players

    /** 获取当前玩家索引 */
    fun getCurrentPlayerIndex(): Int = currentPlayerIndex

    /** 获取指定玩家手牌 */
    fun getPlayerHand(playerIndex: Int): List<Card> = players[playerIndex].getCards()

    /** 获取上一手牌 */
    fun getPreviousHand(): List<Card>? = previousHand

    /** 检查游戏是否结束 */
    fun isGameEnded(): Boolean = gameEnded

    // 重置所有玩家的过牌状态
    private fun resetPassStatus() {
        players.forEach { player ->
            playerPassStatus[player] = false
        }
        consecutivePassCount = 0
    }

    // 运行游戏主循环
    fun runGame() {
        initGame()

        while (!gameEnded) {
            playTurn()

            // 检测是否连续三个玩家过牌
            if (consecutivePassCount >= 3) {
                println("连续三人过牌！下一位玩家可以任意出牌")
                resetPassStatus()
                // 只清空上一手牌，但不再要求出方块3
                previousHand = null
            }
        }

        showResults()
    }

    // 修改后的 playTurn 方法
    private fun playTurn() {
        val currentPlayer = players[currentPlayerIndex]
        println("\n轮到 ${currentPlayer.name} 出牌")
        println("当前手牌: ${currentPlayer.getCards().sorted()}")

        if (previousHand != null) {
            println("上一手牌: $previousHand 由 ${lastPlayedBy?.name} 出")
        }
        // 如果是游戏首轮且为真人玩家且非自动模式
        if (isInitialTurn && currentPlayer.isHuman && !autoPlay) {
            var valid = false
            while (!valid) {
                val cardsToPlay = getPlayerInputWithTimeout(currentPlayer)
                try {
                    // 仅在第一次出牌时要求包含方块3
                    if (isFirstPlay) {
                        require(cardsToPlay.contains(Card(3, Card.Suit.DIAMOND))) { "必须出方块3" }
                    }
                    handlePlay(currentPlayer, cardsToPlay)
                    valid = true
                    isInitialTurn = false
                } catch (e: IllegalArgumentException) {
                    println(e.message + " 请重新出牌。")
                }
            }
        } else if (!currentPlayer.isHuman || autoPlay) {
            val cardsToPlay = autoPlayer.autoPlayCards(currentPlayer, previousHand)
            handlePlay(currentPlayer, cardsToPlay)
            if (isInitialTurn) {
                isInitialTurn = false
            }
        } else {
            // 真人玩家非首轮出牌，不再要求出方块3
            var valid = false
            while (!valid) {
                val cardsToPlay = getPlayerInputWithTimeout(currentPlayer)
                try {
                    handlePlay(currentPlayer, cardsToPlay)
                    valid = true
                } catch (e: IllegalArgumentException) {
                    println(e.message + " 请重新出牌。")
                }
            }
        }

        // 检查胜利条件
        if (players.any { it.hasWon() }) {
            val winner = players.first { it.hasWon() }
            println("\n🎉 ${winner.name} 获胜！")
            gameEnded = true
            return
        }

        // 移动到下一位玩家
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
    }

    // 处理玩家出牌逻辑
    private fun handlePlay(player: Player, cardsToPlay: List<Card>) {
        if (cardsToPlay.isEmpty()) {
            println("${player.name} 选择过牌")
            playerPassStatus[player] = true
            consecutivePassCount++
        } else {
            val previousHandType = previousHand?.let { HandType.from(it) }
            player.playCards(cardsToPlay, previousHandType)
            println("${player.name} 出牌: $cardsToPlay")
            previousHand = cardsToPlay
            lastPlayedBy = player
            lastPlayerWhoPlayedIndex = players.indexOf(player)
            resetPassStatus()
            if (isFirstPlay) { // 出牌成功后取消第一次出牌限制
                isFirstPlay = false
            }
        }
    }

    // 获取玩家输入，带超时功能
    private fun getPlayerInputWithTimeout(player: Player): List<Card> {
        while (true) {
            println("请输入要出的牌的索引（用逗号分隔，例如: 0,1,2），或输入 pass 过牌：")
            val inputThread = Thread {
                val input = readLine()
                synchronized(this) {
                    if (input != null && input.lowercase() != "pass") {
                        try {
                            val selectedIndices = input.split(",").map { it.trim().toInt() }
                            val selectedCards = selectedIndices.map { player.getCards()[it] }
                            playerInput = selectedCards
                        } catch (e: Exception) {
                            println("输入无效，请重新输入！")
                            playerInput = null
                        }
                    } else {
                        playerInput = emptyList()
                    }
                }
            }
            inputThread.start()
            inputThread.join(15000) // 等待 15 秒

            if (inputThread.isAlive) {
                inputThread.interrupt()
                println("超时！自动出牌")
                return autoPlayer.autoPlayCards(player, previousHand)
            }

            var validInput: List<Card>? = null
            synchronized(this) {
                if (playerInput != null) {
                    // 仅在游戏首轮时要求出牌包含方块3
                    if (isInitialTurn && previousHand == null && !playerInput!!.contains(Card(3, Card.Suit.DIAMOND))) {
                        println("必须出方块3，请重新输入。")
                        playerInput = null
                    } else {
                        validInput = playerInput!!
                    }
                }
            }
            if (validInput != null) {
                return validInput!!
            }
        }
    }

    // 全局变量 playerInput 保持不变
    @Volatile
    private var playerInput: List<Card>? = null

    // 显示游戏结果
    private fun showResults() {
        val scores = if (ruleVariant == RuleVariant.SOUTHERN) {
            rules.calculateSouthernScore(players)
        } else {
            rules.calculateNorthernScore(players)
        }

        println("\n游戏结束，得分情况：")
        scores.forEach { (player, score) ->
            println("${player.name}: $score 分")
        }
    }

}

fun main() {
    System.setOut(PrintStream(System.out, true, "UTF-8"))
    val playerInfos = mutableListOf<PlayerInfo>()
    println("请输入真人数量（1-4）：")
    val TrueHumanCount = readLine()?.toIntOrNull()?.coerceIn(1, 4) ?: 4

    repeat(TrueHumanCount) { index ->
        println("请输入真人${index + 1}的名称：")
        val name = readLine() ?: "玩家${index + 1}"
        playerInfos.add(PlayerInfo(name, true))
    }
    val aiCount = 4 - TrueHumanCount
    repeat(aiCount) { index ->
        playerInfos.add(PlayerInfo("AI玩家${index + 1}", false))
    }
    val gameManager = GameManager(playerInfos = playerInfos, autoPlay = false)
    gameManager.runGame()
}