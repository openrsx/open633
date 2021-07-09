package com.rs.game.npc.godwars.saradomin;

import com.rs.game.Animation;
import com.rs.game.Entity;
import com.rs.game.item.Item;
import com.rs.game.map.World;
import com.rs.game.map.WorldTile;
import com.rs.game.npc.NPC;
import com.rs.game.npc.combat.NPCCombatDefinitions;
import com.rs.game.player.Player;
import com.rs.game.task.Task;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class GodwarsSaradominFaction extends NPC {

	public GodwarsSaradominFaction(int id, WorldTile tile, int mapAreaNameHash, boolean canBeAttackFromOutOfArea,
			boolean spawned) {
		super((short) id, tile, (byte) mapAreaNameHash, canBeAttackFromOutOfArea, spawned);
	}

	@Override
	public ObjectArrayList<Entity> getPossibleTargets() {
		if (!withinDistance(new WorldTile(2881, 5306, 0), 200))
			return super.getPossibleTargets();
		else {
			ObjectArrayList<Entity> targets = getPossibleTargets(true, true);
			ObjectArrayList<Entity> targetsCleaned = new ObjectArrayList<Entity>();
			for (Entity t : targets) {
				if (t instanceof GodwarsSaradominFaction || (t.isPlayer() && hasGodItem((Player) t)))
					continue;
				targetsCleaned.add(t);
			}
			return targetsCleaned;
		}
	}

	public static boolean hasGodItem(Player player) {
		for (Item item : player.getEquipment().getItems().getItems()) {
			if (item == null)
				continue; // shouldn't happen
			String name = item.getDefinitions().getName().toLowerCase();
			// using else as only one item should count
			if (name.contains("saradomin coif") || name.contains("citharede hood") || name.contains("saradomin mitre")
					|| name.contains("saradomin full helm") || name.contains("saradomin halo")
					|| name.contains("torva full helm") || name.contains("pernix cowl") || name.contains("virtus mask"))
				return true;
			else if (name.contains("saradomin cape") || name.contains("saradomin cloak"))
				return true;
			else if (name.contains("holy symbol") || name.contains("citharede symbol")
					|| name.contains("saradomin stole"))
				return true;
			else if (name.contains("saradomin arrow"))
				return true;
			else if (name.contains("saradomin godsword") || name.contains("saradomin sword")
					|| name.contains("saradomin staff") || name.contains("saradomin crozier")
					|| name.contains("zaryte Bow"))
				return true;
			else if (name.contains("saradomin robe top") || name.contains("saradomin d'hide")
					|| name.contains("citharede robe top") || name.contains("monk's robe top")
					|| name.contains("saradomin platebody") || name.contains("torva platebody")
					|| name.contains("pernix body") || name.contains("virtus robe top"))
				return true;
			else if (name.contains("illuminated holy book") || name.contains("holy book")
					|| name.contains("saradomin kiteshield"))
				return true;
		}
		return false;
	}

	public void sendDeath(final Entity source) {
		final NPCCombatDefinitions defs = getCombatDefinitions();
		resetWalkSteps();
		getCombat().removeTarget();
		setNextAnimation(null);
		World.get().submit(new Task(1) {
			int loop;
			@Override
			protected void execute() {
				if (loop == 0) {
					setNextAnimation(new Animation(defs.getDeathAnim()));
				} else if (loop >= defs.getDeathDelay()) {
					source.ifPlayer(player -> {
//						Controller controler = player.getControllerManager().getController();
//						if (controler != null && controler instanceof GodWars) {
//							GodWars godControler = (GodWars) controler;
//							godControler.incrementKillCount(2);
//						}
					});
					drop();
					reset();
					setLocation(getRespawnTile());
					finish();
					if (!isSpawned())
						setRespawnTask();
					this.cancel();
				}
				loop++;
				this.cancel();
			}
		});
	}
}
