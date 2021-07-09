package com.rs.game.player.actions;

import com.rs.game.Animation;
import com.rs.game.player.Player;
import com.rs.utilities.RandomUtils;

public class Rest extends Action {

	private static int[][] REST_DEFS = { { 5713, 1549, 5748 },
			{ 11786, 1550, 11788 }, { 5713, 1551, 2921 }
	};

	private int index;
	private boolean musician;

	public Rest(boolean musician) {
		this.musician = musician;
	}

	@Override
	public boolean start(Player player) {
		if (!process(player))
			return false;
		index = RandomUtils.random(REST_DEFS.length -1);
		player.setResting((byte) (musician ? 2 : 1));
		player.getInterfaceManager().sendRunButtonConfig();
		player.setNextAnimation(new Animation(REST_DEFS[index][0]));
		player.getAppearance().setRenderEmote((short) REST_DEFS[index][1]);
		return true;
	}

	@Override
	public boolean process(Player player) {
		if (player.getPoisonDamage().get() > 0) {
			player.getPackets().sendGameMessage(
					"You can't rest while you're poisoned.");
			return false;
		}
		if (player.getCombatDefinitions().isUnderCombat()) {
			player.getPackets().sendGameMessage(
					"You can't rest until 10 seconds after the end of combat.");
			return false;
		}
		return true;
	}

	@Override
	public int processWithDelay(Player player) {
		return 0;
	}

	@Override
	public void stop(Player player) {
		player.setResting((byte) 0);
		player.getInterfaceManager().sendRunButtonConfig();
		player.setNextAnimation(new Animation(REST_DEFS[index][2]));
		player.getAppearance().setRenderEmote((short) -1);
	}
}