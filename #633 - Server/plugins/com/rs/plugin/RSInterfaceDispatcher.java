package com.rs.plugin;

import java.lang.annotation.Annotation;
import java.lang.annotation.IncompleteAnnotationException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TimerTask;
import java.util.stream.Collectors;

import com.rs.Settings;
import com.rs.cores.CoresManager;
import com.rs.game.World;
import com.rs.game.item.Item;
import com.rs.game.item.ItemConstants;
import com.rs.game.npc.familiar.Familiar.SpecialAttack;
import com.rs.game.player.CombatDefinitions;
import com.rs.game.player.Equipment;
import com.rs.game.player.Inventory;
import com.rs.game.player.Player;
import com.rs.game.player.Skills;
import com.rs.game.task.Task;
import com.rs.io.InputStream;
import com.rs.plugin.listener.RSInterface;
import com.rs.plugin.wrapper.RSInterfaceSignature;
import com.rs.utils.Logger;
import com.rs.utils.Utils;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

/**
 * @author Dennis
 */
public final class RSInterfaceDispatcher {

	/**
	 * The object map which contains all the interface on the world.
	 */
	private static final Object2ObjectArrayMap<RSInterfaceSignature, RSInterface> INTERFACES = new Object2ObjectArrayMap<>();

	/**
	 * Executes the specified interface if it's registered.
	 * 
	 * @param player the player executing the interface.
	 * @param parts  the string which represents a interface.
	 */
	public static void execute(Player player, int interfaceId, int componentId, int packetId, byte slotId,
			int slotId2) {
		Optional<RSInterface> rsInterface = getRSInterface(interfaceId);
		if (!rsInterface.isPresent()) {
			player.getPackets().sendGameMessage(interfaceId + " is not handled yet.");
			return;
		}

		try {
			rsInterface.get().execute(player, interfaceId, componentId, packetId, slotId, slotId2);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Gets a interface which matches the {@code identifier}.
	 * 
	 * @param identifier the identifier to check for matches.
	 * @return an Optional with the found value, {@link Optional#empty} otherwise.
	 */
	private static Optional<RSInterface> getRSInterface(int interfaceId) {
		for (Entry<RSInterfaceSignature, RSInterface> rsInterface : INTERFACES.entrySet()) {
			if (isInterface(rsInterface.getValue(), interfaceId)) {
				return Optional.of(rsInterface.getValue());
			}
		}
		return Optional.empty();
	}

	private static boolean isInterface(RSInterface rsInterface, int interfaceId) {
		Annotation annotation = rsInterface.getClass().getAnnotation(RSInterfaceSignature.class);
		RSInterfaceSignature signature = (RSInterfaceSignature) annotation;
		return Arrays.stream(signature.interfaceId()).anyMatch(right -> interfaceId == right);
	}

	/**
	 * Loads all the interface into the {@link #INTERFACES} list.
	 * <p>
	 * </p>
	 * <b>Method should only be called once on start-up.</b>
	 */
	public static void load() {
		List<RSInterface> interfaces = Utils.getClassesInDirectory("com.rs.plugin.impl.interfaces").stream()
				.map(clazz -> (RSInterface) clazz).collect(Collectors.toList());

		for (RSInterface rsInterface : interfaces) {
			if (rsInterface.getClass().getAnnotation(RSInterfaceSignature.class) == null) {
				throw new IncompleteAnnotationException(RSInterfaceSignature.class,
						rsInterface.getClass().getName() + " has no annotation.");
			}
			INTERFACES.put(rsInterface.getClass().getAnnotation(RSInterfaceSignature.class), rsInterface);
		}
	}

	/**
	 * Reloads all the interface into the {@link #INTERFACES} list.
	 * <p>
	 * </p>
	 * <b>This method can be invoked on run-time to clear all the commands in the
	 * list and add them back in a dynamic fashion.</b>
	 */
	public static void reload() {
		INTERFACES.clear();
		load();
	}

	public static void handleButtons(final Player player, InputStream stream, int packetId) {
		int interfaceHash = stream.readInt();
		int interfaceId = interfaceHash >> 16;
		if (Utils.getInterfaceDefinitionsSize() <= interfaceId) {
			return;
		}
		if (player.isDead() || player.isLocked()) {
			return;
		}
		if (!player.getInterfaceManager().containsInterface(interfaceId)) {
			return;
		}
		final int componentId = interfaceHash - (interfaceId << 16);
		if (componentId != 65535 && Utils.getInterfaceDefinitionsComponentsSize(interfaceId) <= componentId) {
			return;
		}
		final int slotId2 = stream.readUnsignedShortLE128();// item slot?
		final int slotId = stream.readUnsignedShortLE128();
		RSInterfaceDispatcher.execute(player, interfaceId, componentId, packetId, (byte) slotId, slotId2);

		if (Settings.DEBUG)
			Logger.log("ButtonHandler",
					"Interface ID: " + interfaceId + " - Comonent: " + componentId + " - PacketId: " + packetId);
	}

	/**
	 * The external methods used are stored here. For sake of ease of access.
	 */
	public static void sendRemove(Player player, int slotId) {
		if (slotId >= 15)
			return;
		player.stopAll(false, false);
		Item item = player.getEquipment().getItem(slotId);
		if (item == null || !player.getInventory().addItem(item.getId(), item.getAmount()))
			return;
		player.getEquipment().getItems().set(slotId, null);
		player.getEquipment().refresh(slotId);
		player.getAppearence().generateAppearenceData();
		player.getPackets().sendGlobalConfig(779, player.getEquipment().getWeaponRenderEmote());
//		if (Runecrafting.isTiara(item.getId()))
//			player.getVarsManager().sendVar(491, 0);
		if (slotId == 3)
			player.getCombatDefinitions().decreaseSpecialAttack(0);
		RSInterfaceDispatcher.refreshEquipBonuses(player);
	}

	public static boolean sendWear(Player player, int slotId, int itemId) {
		player.stopAll(false, false);
		Item item = player.getInventory().getItem(slotId);
		if (item == null || item.getId() != itemId)
			return false;
		if (item.getDefinitions().isNoted() || !item.getDefinitions().isWearItem(player.getAppearence().isMale())) {
			player.getPackets().sendGameMessage("You can't wear that.");
			return true;
		}
		int targetSlot = Equipment.getItemSlot(itemId);
		if (targetSlot == -1) {
			player.getPackets().sendGameMessage("You can't wear that.");
			return true;
		}
		if (!ItemConstants.canWear(item, player))
			return true;
		boolean isTwoHandedWeapon = targetSlot == 3 && Equipment.isTwoHandedWeapon(item);
		if (isTwoHandedWeapon && !player.getInventory().hasFreeSlots() && player.getEquipment().hasShield()) {
			player.getPackets().sendGameMessage("Not enough free space in your inventory.");
			return true;
		}
		HashMap<Integer, Integer> requiriments = item.getDefinitions().getWearingSkillRequiriments();
		boolean hasRequiriments = true;
		if (requiriments != null) {
			for (int skillId : requiriments.keySet()) {
				if (skillId > 24 || skillId < 0)
					continue;
				int level = requiriments.get(skillId);
				if (level < 0 || level > 120)
					continue;
				if (player.getSkills().getLevelForXp(skillId) < level) {
					if (hasRequiriments) {
						player.getPackets().sendGameMessage("You are not high enough level to use this item.");
					}
					hasRequiriments = false;
					String name = Skills.SKILL_NAME[skillId].toLowerCase();
					player.getPackets().sendGameMessage("You need to have a" + (name.startsWith("a") ? "n" : "") + " "
							+ name + " level of " + level + ".");
				}

			}
		}
		if (!hasRequiriments)
			return true;
		if (!player.getControlerManager().canEquip(targetSlot, itemId))
			return false;
		player.stopAll(false, false);
		player.getInventory().deleteItem(slotId, item);
		if (targetSlot == 3) {
			if (isTwoHandedWeapon && player.getEquipment().getItem(5) != null) {
				if (!player.getInventory().addItem(player.getEquipment().getItem(5).getId(),
						player.getEquipment().getItem(5).getAmount())) {
					player.getInventory().getItems().set(slotId, item);
					player.getInventory().refresh(slotId);
					return true;
				}
				player.getEquipment().getItems().set(5, null);
			}
		} else if (targetSlot == 5) {
			if (player.getEquipment().getItem(3) != null
					&& Equipment.isTwoHandedWeapon(player.getEquipment().getItem(3))) {
				if (!player.getInventory().addItem(player.getEquipment().getItem(3).getId(),
						player.getEquipment().getItem(3).getAmount())) {
					player.getInventory().getItems().set(slotId, item);
					player.getInventory().refresh(slotId);
					return true;
				}
				player.getEquipment().getItems().set(3, null);
			}

		}
		if (player.getEquipment().getItem(targetSlot) != null
				&& (itemId != player.getEquipment().getItem(targetSlot).getId()
						|| !item.getDefinitions().isStackable())) {
			if (player.getInventory().getItems().get(slotId) == null) {
				player.getInventory().getItems().set(slotId, new Item(player.getEquipment().getItem(targetSlot).getId(),
						player.getEquipment().getItem(targetSlot).getAmount()));
				player.getInventory().refresh(slotId);
			} else
				player.getInventory().addItem(new Item(player.getEquipment().getItem(targetSlot).getId(),
						player.getEquipment().getItem(targetSlot).getAmount()));
			player.getEquipment().getItems().set(targetSlot, null);
		}
		int oldAmt = 0;
		if (player.getEquipment().getItem(targetSlot) != null) {
			oldAmt = player.getEquipment().getItem(targetSlot).getAmount();
		}
		Item item2 = new Item(itemId, oldAmt + item.getAmount());
		player.getEquipment().getItems().set(targetSlot, item2);
		player.getEquipment().refresh(targetSlot, targetSlot == 3 ? 5 : targetSlot == 3 ? 0 : 3);
		player.getAppearence().generateAppearenceData();
		player.getPackets().sendSound(2240, 0, 1);
		if (targetSlot == 3)
			player.getCombatDefinitions().decreaseSpecialAttack(0);
//		player.getDetails().getCharges().wear(targetSlot);
		return true;
	}

	public static boolean sendWear2(Player player, int slotId, int itemId) {
		if (player.hasFinished() || player.isDead())
			return false;
		player.stopAll(false, false);
		Item item = player.getInventory().getItem(slotId);
		if (item == null || item.getId() != itemId)
			return false;
		if (item.getDefinitions().isNoted()
				|| !item.getDefinitions().isWearItem(player.getAppearence().isMale()) && itemId != 4084) {
			player.getPackets().sendGameMessage("You can't wear that.");
			return false;
		}
		int targetSlot = Equipment.getItemSlot(itemId);
		if (itemId == 4084)
			targetSlot = 3;
		if (targetSlot == -1) {
			player.getPackets().sendGameMessage("You can't wear that.");
			return false;
		}
		if (!ItemConstants.canWear(item, player))
			return false;
		boolean isTwoHandedWeapon = targetSlot == 3 && Equipment.isTwoHandedWeapon(item);
		if (isTwoHandedWeapon && !player.getInventory().hasFreeSlots() && player.getEquipment().hasShield()) {
			player.getPackets().sendGameMessage("Not enough free space in your inventory.");
			return false;
		}
		HashMap<Integer, Integer> requiriments = item.getDefinitions().getWearingSkillRequiriments();
		boolean hasRequiriments = true;
		if (requiriments != null) {
			for (int skillId : requiriments.keySet()) {
				if (skillId > 24 || skillId < 0)
					continue;
				int level = requiriments.get(skillId);
				if (level < 0 || level > 120)
					continue;
				if (player.getSkills().getLevelForXp(skillId) < level) {
					if (hasRequiriments)
						player.getPackets().sendGameMessage("You are not high enough level to use this item.");
					hasRequiriments = false;
					String name = Skills.SKILL_NAME[skillId].toLowerCase();
					player.getPackets().sendGameMessage("You need to have a" + (name.startsWith("a") ? "n" : "") + " "
							+ name + " level of " + level + ".");
				}

			}
		}
		if (!hasRequiriments)
			return false;
		if (!player.getControlerManager().canEquip(targetSlot, itemId))
			return false;
		player.getInventory().getItems().remove(slotId, item);
		if (targetSlot == 3) {
			if (isTwoHandedWeapon && player.getEquipment().getItem(5) != null) {
				if (!player.getInventory().getItems().add(player.getEquipment().getItem(5))) {
					player.getInventory().getItems().set(slotId, item);
					return false;
				}
				player.getEquipment().getItems().set(5, null);
			}
		} else if (targetSlot == 5) {
			if (player.getEquipment().getItem(3) != null
					&& Equipment.isTwoHandedWeapon(player.getEquipment().getItem(3))) {
				if (!player.getInventory().getItems().add(player.getEquipment().getItem(3))) {
					player.getInventory().getItems().set(slotId, item);
					return false;
				}
				player.getEquipment().getItems().set(3, null);
			}

		}
		if (player.getEquipment().getItem(targetSlot) != null
				&& (itemId != player.getEquipment().getItem(targetSlot).getId()
						|| !item.getDefinitions().isStackable())) {
			if (player.getInventory().getItems().get(slotId) == null) {
				player.getInventory().getItems().set(slotId, new Item(player.getEquipment().getItem(targetSlot).getId(),
						player.getEquipment().getItem(targetSlot).getAmount()));
			} else
				player.getInventory().getItems().add(new Item(player.getEquipment().getItem(targetSlot).getId(),
						player.getEquipment().getItem(targetSlot).getAmount()));
			player.getEquipment().getItems().set(targetSlot, null);
		}
		int oldAmt = 0;
		if (player.getEquipment().getItem(targetSlot) != null) {
			oldAmt = player.getEquipment().getItem(targetSlot).getAmount();
		}
		Item item2 = new Item(itemId, oldAmt + item.getAmount());
		player.getEquipment().getItems().set(targetSlot, item2);
		player.getEquipment().refresh(targetSlot, targetSlot == 3 ? 5 : targetSlot == 3 ? 0 : 3);
		if (targetSlot == 3)
			player.getCombatDefinitions().decreaseSpecialAttack(0);
//		player.getDetails().getCharges().wear(targetSlot);
		return true;
	}

	public static void submitSpecialRequest(final Player player) {
		CoresManager.fastExecutor.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					World.get().submit(new Task(1) {
						@Override
						protected void execute() {
							if (player.isDead())
								return;
							player.getCombatDefinitions().switchUsingSpecialAttack();
							this.cancel();
						}
					});
				} catch (Throwable e) {
					Logger.handle(e);
				}
			}
		}, 300);
	}

	public static void sendWear(Player player, int[] slotIds) {
		if (player.hasFinished() || player.isDead())
			return;
		boolean worn = false;
		Item[] copy = player.getInventory().getItems().getItemsCopy();
		for (int slotId : slotIds) {
			Item item = player.getInventory().getItem(slotId);
			if (item == null)
				continue;
			if (sendWear2(player, slotId, item.getId()))
				worn = true;
		}
		player.getInventory().refreshItems(copy);
		if (worn) {
			player.getAppearence().generateAppearenceData();
			player.getPackets().sendSound(2240, 0, 1);
		}
	}

	public static void openItemsKeptOnDeath(Player player) {
		player.getInterfaceManager().sendInterface(17);
		sendItemsKeptOnDeath(player, false);
	}

	public static void sendItemsKeptOnDeath(Player player, boolean wilderness) {
//		boolean skulled = player.hasSkull();
//		Integer[][] slots = GraveStone.getItemSlotsKeptOnDeath(player,
//				wilderness, skulled, player.getPrayer().isProtectingItem());
//		Item[][] items = GraveStone.getItemsKeptOnDeath(player, slots);
//		long riskedWealth = 0;
//		long carriedWealth = 0;
////		for (Item item : items[1])
////			carriedWealth = riskedWealth += GrandExchange
////					.getPrice(item.getId()) * item.getAmount();
////		for (Item item : items[0])
////			carriedWealth += GrandExchange.getPrice(item.getId())
////					* item.getAmount();
//		if (slots[0].length > 0) {
//			for (int i = 0; i < slots[0].length; i++)
//				player.getVarsManager().sendVarBit(9222 + i, slots[0][i]);
//			player.getVarsManager().sendVarBit(9227, slots[0].length);
//		} else {
//			player.getVarsManager().sendVarBit(9222, -1);
//			player.getVarsManager().sendVarBit(9227, 1);
//		}
//		player.getVarsManager().sendVarBit(9226, wilderness ? 1 : 0);
//		player.getVarsManager().sendVarBit(9229, skulled ? 1 : 0);
//		StringBuffer text = new StringBuffer();
//		text.append("The number of items kept on").append("<br>")
//				.append("death is normally 3.").append("<br>").append("<br>")
//				.append("<br>");
//		if (wilderness) {
//			text.append("Your gravestone will not").append("<br>")
//					.append("appear.");
//		} else {
//			int time = GraveStone.getMaximumTicks(player.getGraveStone());
//			int seconds = (int) (time * 0.6);
//			int minutes = seconds / 60;
//			seconds -= minutes * 60;
//
//			text.append("Gravestone:")
//					.append("<br>")
//					.append(ClientScriptMap.getMap(1099).getStringValue(
//							player.getGraveStone()))
//					.append("<br>")
//					.append("<br>")
//					.append("Initial duration:")
//					.append("<br>")
//					.append(minutes + ":" + (seconds < 10 ? "0" : "") + seconds)
//					.append("<br>");
//		}
//		text.append("<br>")
//				.append("<br>")
//				.append("Carried wealth:")
//				.append("<br>")
//				.append(carriedWealth > Integer.MAX_VALUE ? "Too high!" : Utils
//						.getFormattedNumber((int) carriedWealth))
//				.append("<br>")
//				.append("<br>")
//				.append("Risked wealth:")
//				.append("<br>")
//				.append(riskedWealth > Integer.MAX_VALUE ? "Too high!" : Utils
//						.getFormattedNumber((int) riskedWealth)).append("<br>")
//				.append("<br>");
//		if (wilderness) {
//			text.append("Your hub will be set to:").append("<br>")
//					.append("Edgeville.");
//		} else {
//			text.append("Current hub: "
//					+ ClientScriptMap.getMap(3792).getStringValue(
//							DeathEvent.getCurrentHub(player)));
//		}
//		player.getPackets().sendGlobalString(352, text.toString());
	}

	public static void openEquipmentBonuses(final Player player, boolean banking) {
		player.stopAll();
		player.getInterfaceManager().closeInterface(11, 0);
		player.getInterfaceManager().sendInventoryInterface(670);
		player.getInterfaceManager().sendInterface(667);
		player.getPackets().sendRunScript(787, 1);
		player.getPackets().sendItems(93, player.getInventory().getItems());
		player.getPackets().sendInterSetItemsOptionsScript(670, 0, 93, 4, 7, "Equip", "Compare", "Stats", "Examine");
		player.getPackets().sendUnlockIComponentOptionSlots(670, 0, 0, 27, 0, 1, 2, 3);
		player.getPackets().sendIComponentSettings(667, 7, 0, 14, 1538);
		player.getPackets().sendGlobalConfig(779, player.getEquipment().getWeaponRenderEmote());
		refreshEquipBonuses(player);
		if (banking) {
			player.getTemporaryAttributes().put("Banking", Boolean.TRUE);
			player.setCloseInterfacesEvent(new Runnable() {
				@Override
				public void run() {
					player.getTemporaryAttributes().remove("Banking");
					player.getVarsManager().sendVarBit(4894, 0);
				}
			});
		}
	}

	private static String equipmentBonusText(Player player, String msg, int bonusId) {
		int bonus = player.getCombatDefinitions().getBonuses()[bonusId];
		if (bonus < 0)
			return msg.replace("+", "") + "" + bonus;
		return msg + "" + bonus; // only use if it requires it to be negative.

	}

	public static void refreshEquipBonuses(Player player) {

		player.getPackets().sendIComponentText(667, 31, equipmentBonusText(player, "Slash +", 0));
		player.getPackets().sendIComponentText(667, 32, equipmentBonusText(player, "Slashs: +", 1));
		player.getPackets().sendIComponentText(667, 33, equipmentBonusText(player, "Crush: +", 2));
		player.getPackets().sendIComponentText(667, 34, equipmentBonusText(player, "Magic: +", 3));
		player.getPackets().sendIComponentText(667, 35, equipmentBonusText(player, "Range: +", 4));
		player.getPackets().sendIComponentText(667, 36, equipmentBonusText(player, "Stab: +", 5));
		player.getPackets().sendIComponentText(667, 37, equipmentBonusText(player, "Slash: +", 6));
		player.getPackets().sendIComponentText(667, 38, equipmentBonusText(player, "Crush: +", 7));
		player.getPackets().sendIComponentText(667, 39, equipmentBonusText(player, "Magic: +", 8));
		player.getPackets().sendIComponentText(667, 40, equipmentBonusText(player, "Range: +", 9));
		player.getPackets().sendIComponentText(667, 41, equipmentBonusText(player, "Summoning: +", 10));
		player.getPackets().sendIComponentText(667, 42,
				"Absorb Melee: +" + player.getCombatDefinitions().getBonuses()[CombatDefinitions.ABSORB_MELEE] + "%");
		player.getPackets().sendIComponentText(667, 43,
				"Absorb Magic: +" + player.getCombatDefinitions().getBonuses()[CombatDefinitions.ABSORB_MAGIC] + "%");
		player.getPackets().sendIComponentText(667, 44,
				"Absorb Ranged: +" + player.getCombatDefinitions().getBonuses()[CombatDefinitions.ABSORB_RANGE] + "%");
		player.getPackets().sendIComponentText(667, 45, "Strength: " + player.getCombatDefinitions().getBonuses()[14]);
		player.getPackets().sendIComponentText(667, 46,
				"Ranged Str: " + player.getCombatDefinitions().getBonuses()[15]);
		player.getPackets().sendIComponentText(667, 47, equipmentBonusText(player, "Prayer: +", 16));
		player.getPackets().sendIComponentText(667, 48,
				"Magic Damage: +" + player.getCombatDefinitions().getBonuses()[17] + "%");
	}

	public static void openSkillGuide(Player player) {
		player.getInterfaceManager().setScreenInterface(317, 1218);
		player.getInterfaceManager().setInterface(false, 1218, 1, 1217); // seems
		// to
		// fix
	}

	/**
	 * TODO: Needs to be tested
	 */

	public static void handleInterfaceOnInterface(final Player player, InputStream stream) {
		int usedWithId = stream.readShort();
		int toSlot = stream.readUnsignedShortLE128();
		int interfaceId = stream.readUnsignedShort();
		int interfaceComponent = stream.readUnsignedShort();
		int interfaceId2 = stream.readInt() >> 16;
		int fromSlot = stream.readUnsignedShort();
		int itemUsedId = stream.readUnsignedShortLE128();
		if ((interfaceId == 747 || interfaceId == 662) && interfaceId2 == Inventory.INVENTORY_INTERFACE) {
			if (player.getFamiliar() != null) {
				player.getFamiliar().setSpecial(true);
				if (player.getFamiliar().getSpecialAttack() == SpecialAttack.ITEM) {
					if (player.getFamiliar().hasSpecialOn())
						player.getFamiliar().submitSpecial(toSlot);
				}
			}
			return;
		}
		if (interfaceId == Inventory.INVENTORY_INTERFACE && interfaceId == interfaceId2
				&& !player.getInterfaceManager().containsInventoryInter()) {
			if (toSlot >= 28 || fromSlot >= 28 || toSlot == fromSlot)
				return;
			Item usedWith = player.getInventory().getItem(toSlot);
			Item itemUsed = player.getInventory().getItem(fromSlot);
			if (itemUsed == null || usedWith == null || itemUsed.getId() != itemUsedId
					|| usedWith.getId() != usedWithId)
				return;
			if (player.isLocked() || player.getEmotesManager().isDoingEmote())
				return;
			player.stopAll();
			if (!player.getControlerManager().canUseItemOnItem(itemUsed, usedWith))
				return;

		}
		if (Settings.DEBUG)
			Logger.log("ItemHandler", "ItemOnItem " + usedWithId + ", " + toSlot + ", " + interfaceId + ", "
					+ interfaceComponent + ", " + fromSlot + ", " + itemUsedId);
	}
}