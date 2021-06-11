package com.rs.game.player.controllers;

import com.rs.game.Animation;
import com.rs.game.World;
import com.rs.game.WorldObject;
import com.rs.game.WorldTile;
import com.rs.game.player.Player;
import com.rs.game.task.Task;
import com.rs.utilities.Utils;

import skills.Skills;

public class GodWars extends Controller {

	public static final WorldTile[] GODS = { new WorldTile(2863, 5357, 0),
			new WorldTile(2862, 5357, 0), // bandos
			new WorldTile(2835, 5295, 0), new WorldTile(2835, 5294, 0), // armadyl
			new WorldTile(2923, 5256, 0), new WorldTile(2923, 5257, 0), // saradomin
			new WorldTile(2925, 5332, 0), new WorldTile(2925, 5333, 0), // zamorak
	};
	private int[] killCount = new int[5];
	private long lastPrayerRecharge;

	@Override
	public void start() {
		sendInterfaces(0);
	}

	@Override
	public boolean logout() {
		setArguments(new Object[] { killCount, lastPrayerRecharge });
		return false; // so doesnt remove script
	}

	@Override
	public boolean login() {
		killCount = (int[]) this.getArguments()[0];
		if (getArguments().length == 2)
			lastPrayerRecharge = (long) this.getArguments()[1];
		sendInterfaces();
		refresh();
		return false; // so doesnt remove script
	}

	@Override
	public void sendInterfaces() {
		sendInterfaces(player.withinArea(2916, 5317, 2937, 5332) ? 2
				: inZamorakPrepare(player) ? 1 : 0);
	}

	@Override
	public boolean processObjectClick1(final WorldObject object) {
		if (object.getId() == 26287 || object.getId() == 26286
				|| object.getId() == 26288 || object.getId() == 26289) {
			if (lastPrayerRecharge >= Utils.currentTimeMillis()) {
				player.getPackets()
						.sendGameMessage(
								"You must wait a total of 10 minutes before being able to recharge your prayer points.");
				return false;
			} else if (player.getAttackedByDelay() >= Utils.currentTimeMillis()) {
				player.getPackets()
						.sendGameMessage(
								"You cannot recharge your prayer while engaged in combat.");
				return false;
			}
			player.getPrayer().restorePrayer(
					player.getSkills().getLevelForXp(Skills.PRAYER) * 10);
			player.setNextAnimation(new Animation(645));
			player.getPackets().sendGameMessage(
					"Your prayer points feel rejuvinated.");
			lastPrayerRecharge = 600000 + Utils.currentTimeMillis();
		} else if (object.getId() == 26293) {
//			player.useStairs(828, new WorldTile(2913, 3741, 0), 1, 2);
			player.getControllerManager().forceStop();
		} else if (object.getId() == 26384) { // bandos
//			if (!player.getInventory().containsItemToolBelt(Smithing.HAMMER)) {
//				player.getPackets()
//						.sendGameMessage(
//								"You look at the door but find no knob, maybe it opens some other way.");
//				return false;
//			}
			if (player.getSkills().getLevel(Skills.STRENGTH) < 70) {
				player.getPackets()
						.sendGameMessage(
								"You attempt to hit the door, but realize that you are not yet experienced enough.");
				return false;
			}
			final boolean withinBandos = inBandosPrepare(player);
			if (!withinBandos)
				player.setNextAnimation(new Animation(7002));
			World.get().submit(new Task( withinBandos ? 0 : 1) {
				@Override
				protected void execute() {
					player.addWalkSteps(withinBandos ? 2851 : 2850, 5334, -1,
							false);
					this.cancel();
				}
			});
			return false;
		} else if (object.getId() == 26439) {
//			if (!Agility.hasLevel(player, 70))
//				return false;
			final boolean withinZamorak = inZamorakPrepare(player);
			final WorldTile tile = new WorldTile(2887, withinZamorak ? 5336
					: 5346, 0);
			player.lock();
			player.safeForceMoveTile(object);
			World.get().submit(new Task(1) {
				@Override
				protected void execute() {
					player.setNextAnimation(new Animation(17454));
					if (withinZamorak)
						sendInterfaces(0);
					else {
						sendInterfaces(1);
						player.getPrayer().drainPrayer();
					}
					player.setNextFaceWorldTile(tile);
					this.cancel();
				}
			});
			World.get().submit(new Task(5) {
				@Override
				protected void execute() {
					player.unlock();
					player.setNextAnimation(new Animation(-1));
					player.safeForceMoveTile(tile);
					this.cancel();
				}
			});
			return false;
		} else if (object.getId() == 75462) {
			if (object.getX() == 2912
					&& (object.getY() == 5298 || object.getY() == 5299))
				useAgilityStones(player, object,
						new WorldTile(2915, object.getY(), 0), 70, 15239, 7);
			else if (object.getX() == 2914
					&& (object.getY() == 5298 || object.getY() == 5299))
				useAgilityStones(player, object,
						new WorldTile(2911, object.getY(), 0), 70, 3378, 7);
			else if ((object.getX() == 2919 || object.getX() == 2920)
					&& object.getY() == 5278)
				useAgilityStones(player, object, new WorldTile(object.getX(),
						5275, 0), 70, 15239, 7);
			else if ((object.getX() == 2920 || object.getX() == 2919)
					&& object.getY() == 5276)
				useAgilityStones(player, object, new WorldTile(object.getX(),
						5279, 0), 70, 3378, 7);
		} else if (object.getId() >= 26425 && object.getId() <= 26428) {
			int index = object.getId() - 26425;
			boolean returning = player.withinArea(2819, 5295, 2839, 5309)
					|| player.withinArea(2863, 5352, 2878, 5375)
					|| player.withinArea(2916, 5317, 2937, 5332)
					|| player.withinArea(2915, 5242, 2931, 5256);
			int requiredKc = 15;
			if (returning || killCount[index] >= requiredKc) {
				if (returning && object.getId() == 26428)
					sendInterfaces(1);
				else if (returning)
					sendInterfaces(0);
				else if (object.getId() == 26428)
					sendInterfaces(2);
				WorldTile tile = GODS[returning ? (index * 2) + 1 : (index * 2)];
				player.addWalkSteps(tile.getX(), tile.getY(), -1, false);
				if (!returning) {
					killCount[index] -= requiredKc;
					refresh();
				}
			} else
				player.getPackets()
						.sendGameMessage(
								"You don't have enough kills to enter the lair of the gods.");
			return false;
		}
		return true;
	}

	@SuppressWarnings("unused")
	@Override
	public boolean processObjectClick2(WorldObject object) {
		if (object.getId() == 26287 || object.getId() == 26286
				|| object.getId() == 26288 || object.getId() == 26289) {
			int index = object.getId() == 26289 ? 0
					: object.getId() == 26286 ? 3 : object.getId() == 26287 ? 2
							: 1;
			player.getPackets()
					.sendGameMessage(
							"The god's pitty you and allow you to leave the encampment.");
//			player.useStairs(-1, GODS[(index * 2) + 1], 1, 2);
			return false;
		}
		return true;
	}

	public void incrementKillCount(int index) {
		killCount[index]++;
		refresh();
	}

	public void resetKillCount(int index) {
		killCount[index] = 0;
		refresh();
	}

	public void refresh() {
		player.getVarsManager().sendVarBit(3939, killCount[1]); // arma
		player.getVarsManager().sendVarBit(3941, killCount[0]); // bando
		player.getVarsManager().sendVarBit(3938, killCount[2]);// sara
		player.getVarsManager().sendVarBit(3942, killCount[3]);// zamy
		player.getVarsManager().sendVarBit(8725, killCount[4]);
	}

	public static void useAgilityStones(final Player player,
			final WorldObject object, final WorldTile tile, int levelRequired,
			final int emote, final int delay) {
//		if (!Agility.hasLevel(player, levelRequired))
//			return;
		player.faceObject(object);
		player.addWalkSteps(object.getX(), object.getY());
		World.get().submit(new Task(1) {
			@Override
			protected void execute() {
//				player.useStairs(emote, tile, delay, delay + 1);
				this.cancel();
			}
		});
	}

	private static final int[] INTERFACES = new int[] { 601, 599, 598 };

	public void sendInterfaces(int interfaceIndex) {
		player.getInterfaceManager().setOverlay(INTERFACES[interfaceIndex],
				true);
		refresh();
	}

	public static boolean inZarosArea(Player player) {
		return player.withinArea(2843, 5184, 2944, 5228);
	}

	@Override
	public boolean sendDeath() {
		player.getControllerManager().forceStop();
		return true;
	}

	@Override
	public void magicTeleported(int type) {
		player.getControllerManager().forceStop();
	}

	@Override
	public void forceClose() {
		player.getInterfaceManager().removeOverlay(true);
	}

	public static boolean inArmadylPrepare(Player player) {
		return player.withinArea(2866, 5268, 2874, 5273);
	}

	public static boolean inBandosPrepare(Player player) {
		return player.withinArea(2823, 5313, 2850, 5432);
	}

	public static boolean inZamorakPrepare(Player player) {
		return player.withinArea(2884, 5343, 2890, 5352);
	}

	@SuppressWarnings("unused")
	public static void passGiantBoulder(Player player, WorldObject object,
			boolean liftBoulder) {
		if (player.getSkills().getLevel(
				liftBoulder ? Skills.STRENGTH : Skills.AGILITY) < 60) {
			player.getPackets().sendGameMessage(
					"You need a " + (liftBoulder ? "Agility" : "Strength")
							+ " of 60 in order to "
							+ (liftBoulder ? "lift" : "squeeze past")
							+ "this boulder.");
			return;
		}
		if (liftBoulder)
			WorldObject.sendObjectAnimation(object, new Animation(318));
		boolean isReturning = player.getY() >= 3709;
		int baseAnimation = liftBoulder ? 3725 : 3466;
//		player.useStairs(isReturning ? baseAnimation-- : baseAnimation,
//				new WorldTile(player.getX(), player.getY()
//						+ (isReturning ? -4 : 4), 0), liftBoulder ? 10 : 5,
//				liftBoulder ? 11 : 6, null, true);
	}
}
