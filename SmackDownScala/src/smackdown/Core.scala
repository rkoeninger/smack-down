package smackdown

import scala.util.Random
import Utils._

class Table {
  var players = List[Player]()
  var currentPlayer = null // FIXME: don't use null!
  var baseDrawPile = List[Base]()
  var basesInPlay = Set[Base]()
  var baseDiscardPile = Set[Base]()
  def minions() = basesInPlay.flatMap(_.minions)
  def factions() = Set[Faction]()
}

class Player(val name: String, val factions: List[Faction], val table: Table, val callback: Callback) {
  var points = 0 // TODO: player/callback needs pointsModified event
  var hand = Set[DeckCard]()
  var discardPile = Set[DeckCard]()
  var drawPile = List[DeckCard]()
  var bonuses = Set[Bonus]()
  var moves = Set[Move]()
  var onTurnBeginSet = Set[Unit => Unit]()
  var onTurnEndSet = Set[Unit => Unit]()
  
  def onTurnBegin(todo: => Unit) { onTurnBeginSet += (_ => todo) }
  
  def onTurnEnd(todo: => Unit) { onTurnEndSet += (_ => todo) }
  
  def minionsInPlay() = table.basesInPlay.flatMap(_.minions).filter(_.owner == this)
  
  def beginTurn() {
    moves = Set(new PlayMinion(), new PlayAction())
    
    onTurnBeginSet.foreach(_.apply())
    onTurnBeginSet = Set[Unit => Unit]()
  }
  
  def endTurn() {
    moves = Set()
    
    onTurnEndSet.foreach(_.apply())
    onTurnEndSet = Set[Unit => Unit]()
    
    draw(2)
    
    while (hand.size > 10)
      for (c <- chooseCardInHand) c.moveToDiscard
  }
  
  def draw() {
    if (replenishDrawPile) { if (! drawPile.isEmpty) drawPile(0).moveToHand }
  }
  
  def draw(count: Int) {
    (1 to count).foreach(x => draw)
    // without the x =>, the draw(Int) version will be used, causing infinite loop
  }
  
  // TODO: player/callback needs cardRevealed event
  def reveal() = {
    if (replenishDrawPile) Some(drawPile(0)) else None
  }
  
  def peek() = {
    if (replenishDrawPile) Some(drawPile(0)) else None
  }
  
  def shuffle() {
    drawPile = Random.shuffle(drawPile)
  }
  
  private def replenishDrawPile(): Boolean = {
    if (drawPile.isEmpty) {
      if (discardPile.isEmpty)
        return false
      drawPile = Random.shuffle(discardPile.toList)
      discardPile = Set()
    }
    return true
  }
  
  def randomDiscard() {
    if (hand.size > 0)
      hand = hand.toList.dropIndex(Random.nextInt(hand.size)).toSet
  }
  
  def randomDiscard(count: Int) {
    (1 to count).foreach(x => randomDiscard)
  }
  
  def otherPlayers() = table.players.filterNot(_ == this)
  
  def playMinion() {
    val move = new PlayMinion()
    if (move.isPlayable(this))
      move.play(this)
  }
  
  def playMinion(maxStrength: Int) {
    val move = new PlayMinion(maxStrength)
    if (move.isPlayable(this))
      move.play(this)
  }
  
  def playMinion(base: Base) {
    val move = new PlayMinionOnBase(base)
    if (move.isPlayable(this))
      move.play(this)
  }
  
  def playMinion(m: Minion) {
    for (m <- chooseMinionInHand;
         b <- chooseBaseInPlay) {
      m.play(b)
      b.cards += m
    }
  }
  
  def playAction() {
    val move = new PlayAction()
    if (move.isPlayable(this))
      move.play(this)
  }
  
  def playAction(a: Action) {
    a.play(this)
  }
  
  def chooseBaseInPlay = callback.choose(table.basesInPlay)
  def chooseOtherBaseInPlay(not: Base) = callback.choose(table.basesInPlay.filterNot(_ == not))
  def chooseCardInHand = callback.choose(hand)
  def chooseMinionInHand = callback.choose(hand.minions())
  def chooseMinionInHand(maxStrength: Int) = callback.choose(hand.minions().maxStrength(maxStrength))
  def chooseMinionInHand(player: Player) = callback.choose(player.hand)
  def chooseMinionInPlay = callback.choose(table.minions)
  def chooseMinionInPlay(maxStrength: Int) = callback.choose(table.minions.maxStrength(maxStrength))
  def chooseMyMinionInPlay = callback.choose(table.minions.ownedBy(this))
  def chooseTheirMinionInPlay = callback.choose(table.minions.filter(_.owner != this))
  def chooseMinionOnBase(base: Base) = callback.choose(base.minions)
  def chooseMinionOnBase(base: Base, maxStrength: Int) = callback.choose(base.minions.maxStrength(maxStrength))
  def chooseMyMinionOnBase(base: Base) = callback.choose(base.minions.ownedBy(this))
  def chooseActionInHand = callback.choose(hand.actions())
  def chooseActionInDrawPile = callback.choose(drawPile.toSet.ofType[Action])
  def choosePlayer = callback.choose(table.players.toSet)
  def chooseOtherPlayer = callback.choose(otherPlayers.toSet)
  def chooseFaction = callback.choose(table.factions)
  def chooseYesNo = callback.confirm
}

trait Callback {
  def choose[T](options: Set[T]): Option[T] = None
  def chooseOrder[T](options: List[T]): List[T] = options
  def confirm(): Boolean = false
}

abstract class Faction(val name: String) {
  def bases(table: Table): Set[Base]
  def cards(owner: Player): Set[DeckCard]
}

abstract class Card(val name: String, val faction: Faction)

class Base(name: String, faction: Faction, val breakPoint: Int, val scoreValues: (Int, Int, Int), val table: Table) extends Card(name, faction) {
  
  var cards = Set[DeckCard]()
  def minions() = cards.ofType[Minion]
  def actions() = cards.ofType[Minion]
  var bonuses = Set[Bonus]()
  
  def totalStrength() = minions.map(_.strength).sum
  
  def isInPlay() = table.basesInPlay.contains(this)
  
  def minionPlayed(minion: Minion) {}
  def minionMovedHere(minion: Minion) {}
  def minionMovedAway(minion: Minion) {}
  def minionDestroyed(minion: Minion, base: Base) {}
  def onTurnBegin(player: Player) {}
  def beforeScore() {}
  def onScore() {}
  def afterScore(newBase: Base) {}
  
  /**
   * Returns a mapping of players to (scoreReward, ranking) where the score reward is one of the
   * three values in scoreValues and the ranking is 1 for winner, 2 for runner-up, etc.
   * Rank values are 1, 2, 3... regardless of ties. If there is a tie for 1st and a third player
   * with less strength, the two players with greater strength will get scoreValues[0] and rank 1,
   * the weaker player will get scoreValues[2] and rank 2.
   */
  def score(): Set[Rank] = {
    val playerStrengths = minions.groupBy(_.owner).map(x => (x._1, x._2.map(_.strength).sum))
    val sortedStrengths = playerStrengths.values.toList.distinct.sorted.reverse
    var rewardCount = 0
    var rewardRank = 1
    
    val ranks = sortedStrengths.map(strength =>
      if (rewardCount < 3) {
        val reward = scoreValues.productElement(rewardCount).as[Int]
        val rewardGroup = playerStrengths.filter(_._2 == strength).map(x => new Rank(x._1, reward, rewardRank)).toSet
        rewardCount += rewardGroup.size
        rewardRank += 1
        rewardGroup
      }
      else Set[Rank]()
    )
    
    if (ranks.isEmpty) Set[Rank]() else ranks.reduce(_ ++ _)
  }
}

case class Rank(val player: Player, val score: Int, val rank: Int) {
  def winner() = rank == 1
  def runnerUp() = rank == 2
}

abstract class DeckCard(name: String, faction: Faction, val owner: Player) extends Card(name, faction) {
  var base: Option[Base] = None
  
  def table() = owner.table
  
  def moveToHand() {
    if (! isInHand) {
      remove
      owner.hand += this
    }
  }
  def moveToDiscard() {
    if (! isInDiscardPile) {
      remove
      owner.discardPile += this
    }
  }
  def moveToDrawPileTop() {
    remove
    owner.drawPile = this :: owner.drawPile
  }
  def moveToDrawPileBottom() {
    remove
    owner.drawPile = owner.drawPile :+ this
  }
  def moveToBase(base: Base) {
    remove
    if (this.base != Some(base)) {
      base.cards += this
      this.base = Some(base)
    }
  }
  private def remove() {
    if (isInHand) owner.hand -= this
    else if (isInDrawPile) owner.drawPile = owner.drawPile.filterNot(_ == this)
    else if (isInDiscardPile) owner.discardPile -= this
    else if (isOnBase) {
      base.map(_.cards -= this)
      base = None
    }
  }
  def isInHand() = owner.hand.contains(this)
  def isInDrawPile() = owner.drawPile.contains(this)
  def isInDiscardPile() = owner.discardPile.contains(this)
  def isOnBase() = base.isDefined
  def isOnBase(base: Base) = base.cards.contains(this)
  def beginTurn() {}
  def endTurn() {}
}

class Minion(name: String, faction: Faction, startingStrength: Int, owner: Player) extends DeckCard(name, faction, owner) {
  var bonuses = Set[Bonus]()
  def isOnTable() = base.isDefined
  def strength() = startingStrength
    + bonuses.map(_.getBonus(this)).sum
    + (if (isOnBase) owner.bonuses.map(_.getBonus(this)).sum else 0)
    + base.map(_.bonuses.map(_.getBonus(this)).sum).getOrElse(0)
  def play(base: Base) {}
  def destructable() = true
  def destroy(destroyer: Player) {
    if (destructable) moveToDiscard
  }
  def beforeScore(base: Base) {}
  def afterScore(base: Base, newBase: Base) {}
  def minionDestroyed(minion: Minion, base: Base) {}
  def minionPlayed(minion: Minion) {}
}

class Action(name: String, faction: Faction, owner: Player) extends DeckCard(name, faction, owner) {
  def play(user: Player) {}
  def beforeScore(base: Base) {}
  def afterScore(base: Base, newBase: Base) {}
  def detach(card: Card) {}
}

trait Bonus {
  def getBonus(minion: Minion): Int
}

object Bonus {
  def apply(func: Minion => Int) = new Bonus { def getBonus(minion: Minion) = func(minion) }
  def apply(value: Int) = new Bonus { def getBonus(minion: Minion) = value }
  def untilTurnEnd(player: Player, value: Int) {
    val bonus = Bonus(value)
    player.bonuses += bonus
    player.onTurnEnd { player.bonuses -= bonus }
  }
  def untilTurnEnd(minion: Minion, value: Int) {
    val bonus = Bonus(value)
    minion.bonuses += bonus
    minion.owner.onTurnEnd { minion.bonuses -= bonus }
  }
}

trait Move {
  def isPlayable(user: Player): Boolean
  def play(user: Player)
}

class PlayMinion(maxStrength: Int) extends Move {
  def this() = this(Int.MaxValue)
  def isPlayable(user: Player) = user.hand.exists(m => m.is[Minion] && m.as[Minion].strength <= maxStrength)
  def play(user: Player) {
    for (m <- user.chooseMinionInHand(maxStrength);
         b <- user.chooseBaseInPlay) {
      m.play(b)
      b.cards += m
    }
  }
}

class PlayMinionOnBase(base: Base) extends Move {
  def isPlayable(user: Player) = user.hand.exists(m => m.is[Minion])
  def play (user: Player) {
    for (m <- user.chooseMinionInHand) {
      m.play(base)
      base.cards += m
    }
  }
}

class PlayAction extends Move {
  def isPlayable(user: Player) = user.hand.exists(_.is[Action])
  def play(user: Player) {
    for (a <- user.chooseActionInHand)
      a.play(user)
  }
}