package smack.down.wizards;

import smack.down.Base;
import smack.down.Callback;
import smack.down.Faction;
import smack.down.Minion;
import smack.down.moves.PlayAction;

public class Chronomage extends Minion {

	public Chronomage() {
		super("Chronomage", Faction.Wizards, 3);
	}
	
	public void play(Base base, Callback callback) {
		getOwner().addMove(new PlayAction());
	}
}
