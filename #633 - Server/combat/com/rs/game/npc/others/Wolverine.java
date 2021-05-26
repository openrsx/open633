package com.rs.game.npc.others;

import java.util.Random;

import com.rs.game.WorldTile;
import com.rs.game.npc.NPC;
import com.rs.game.player.Player;

public class Wolverine extends NPC {

	public Wolverine(Player target, int id, WorldTile tile, int mapAreaNameHash, boolean canBeAttackFromOutOfArea) {
		super((short) id, tile, (byte) mapAreaNameHash, canBeAttackFromOutOfArea);
		setCombatLevel((short) (target.getSkills().getCombatLevel() + new Random().nextInt(100) + 100));
		int hitpoints = 1000 + this.getCombatLevel() + target.getSkills().getCombatLevel() / 2
				+ new Random().nextInt(10);
		super.getCombatDefinitions().setHitpoints(hitpoints);
		setHitpoints(hitpoints);
		setWalkType(NORMAL_WALK);
		setForceAgressive(true);
		setAttackedBy(target);
		setMultiArea(true);
		faceEntity(target);
	}
}