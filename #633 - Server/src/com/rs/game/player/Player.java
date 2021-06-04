package com.rs.game.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.rs.GameConstants;
import com.rs.cores.CoresManager;
import com.rs.cores.WorldThread;
import com.rs.game.Entity;
import com.rs.game.HintIconsManager;
import com.rs.game.Hit;
import com.rs.game.World;
import com.rs.game.item.FloorItem;
import com.rs.game.item.Item;
import com.rs.game.map.Region;
import com.rs.game.minigames.duel.DuelRules;
import com.rs.game.npc.familiar.Familiar;
import com.rs.game.npc.others.Pet;
import com.rs.game.player.actions.ActionManager;
import com.rs.game.player.content.FriendChatsManager;
import com.rs.game.player.content.MusicsManager;
import com.rs.game.player.content.Notes;
import com.rs.game.player.content.PriceCheckManager;
import com.rs.game.player.content.pet.PetManager;
import com.rs.game.player.controllers.ControllerManager;
import com.rs.game.player.dialogues.DialogueManager;
import com.rs.game.player.type.CombatEffect;
import com.rs.game.route.CoordsEvent;
import com.rs.game.route.RouteEvent;
import com.rs.game.task.Task;
import com.rs.game.task.impl.CombatEffectTask;
import com.rs.net.AccountCreation;
import com.rs.net.LogicPacket;
import com.rs.net.Session;
import com.rs.net.decoders.WorldPacketsDecoder;
import com.rs.net.encoders.WorldPacketsEncoder;
import com.rs.net.host.HostListType;
import com.rs.net.host.HostManager;
import com.rs.utils.IsaacKeyPair;
import com.rs.utils.Logger;
import com.rs.utils.Utils;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class Player extends Entity {

	public static final byte TELE_MOVE_TYPE = 127, WALK_MOVE_TYPE = 1,
			RUN_MOVE_TYPE = 2;

	// transient stuff
	private transient String username;
	private transient Session session;
	private transient boolean clientLoadedMapRegion;
	private transient byte displayMode;
	private transient short screenWidth;
	private transient short screenHeight;
	private transient InterfaceManager interfaceManager;
	private transient DialogueManager dialogueManager;
	private transient HintIconsManager hintIconsManager;
	private transient ActionManager actionManager;
	private transient PriceCheckManager priceCheckManager;
	private transient RouteEvent routeEvent;
	private transient FriendChatsManager currentFriendChat;
	private transient boolean toogleLootShare;
	private transient Trade trade;
	private transient DuelRules lastDuelRules;
	private transient IsaacKeyPair isaacKeyPair;
	private transient Pet pet;
	private transient VarsManager varsManager;
	private transient CoordsEvent coordsEvent; 
	private transient Region region;
	
	// used for packets logic
	private transient ConcurrentLinkedQueue<LogicPacket> logicPackets;

	// used for update
	private transient LocalPlayerUpdate localPlayerUpdate;
	private transient LocalNPCUpdate localNPCUpdate;

	private transient byte temporaryMovementType;
	private transient boolean updateMovementType;

	// player stages
	private transient boolean started;
	private transient boolean running;

	private transient byte resting;
	private transient boolean canPvp;
	private transient boolean cantTrade;
	private transient long lockDelay; // used for doors and stuff like that
	private transient Runnable closeInterfacesEvent;
	private transient long lastPublicMessage;
	private transient List<Byte> switchItemCache;
	private transient boolean disableEquip;
	private transient boolean invulnerable;
	private transient boolean finishing;
	
	/**
	 * Personal details & information stored for a Player
	 */
	private PlayerDetails details;
	
	private Appearance appearance;
	private Inventory inventory;
	private Equipment equipment;
	private Skills skills;
	private CombatDefinitions combatDefinitions;
	private Prayer prayer;
	private Bank bank;
	private ControllerManager controllerManager;
	private MusicsManager musicsManager;
	private Notes notes;
	private FriendsIgnores friendsIgnores;
	private Familiar familiar;
	private PetManager petManager;

	// creates Player and saved classes
	public Player(String password) {
		super(GameConstants.START_PLAYER_LOCATION);
		setHitpoints(100);
		appearance = new Appearance();
		inventory = new Inventory();
		equipment = new Equipment();
		skills = new Skills();
		combatDefinitions = new CombatDefinitions();
		prayer = new Prayer();
		bank = new Bank();
		controllerManager = new ControllerManager();
		musicsManager = new MusicsManager();
		notes = new Notes();
		friendsIgnores = new FriendsIgnores();
		petManager = new PetManager();
		details = new PlayerDetails();
		getDetails().setPassword(password);
	}

	public void init(Session session, String username, byte displayMode,
			short screenWidth, short screenHeight, IsaacKeyPair isaacKeyPair) {
		// temporary deleted after reset all chars
		if (details == null)
			details = new PlayerDetails();
		if (petManager == null)
			petManager = new PetManager();
		if (notes == null)
			notes = new Notes();
		this.session = session;
		this.username = username;
		this.displayMode = displayMode;
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		this.isaacKeyPair = isaacKeyPair;
		interfaceManager = new InterfaceManager(this);
		dialogueManager = new DialogueManager(this);
		hintIconsManager = new HintIconsManager(this);
		priceCheckManager = new PriceCheckManager(this);
		localPlayerUpdate = new LocalPlayerUpdate(this);
		localNPCUpdate = new LocalNPCUpdate(this);
		actionManager = new ActionManager(this);
		trade = new Trade(this);
		varsManager = new VarsManager(this);
		// loads player on saved instances
		appearance.setPlayer(this);
		inventory.setPlayer(this);
		equipment.setPlayer(this);
		skills.setPlayer(this);
		combatDefinitions.setPlayer(this);
		prayer.setPlayer(this);
		bank.setPlayer(this);
		controllerManager.setPlayer(this);
		musicsManager.setPlayer(this);
		notes.setPlayer(this);
		friendsIgnores.setPlayer(this);
		getDetails().getCharges().setPlayer(this);
		petManager.setPlayer(this);
		setDirection((byte) Utils.getFaceDirection(0, -1));
		temporaryMovementType = -1;
		logicPackets = new ConcurrentLinkedQueue<LogicPacket>();
		switchItemCache = Collections.synchronizedList(new ArrayList<Byte>());
		initEntity();
		World.addPlayer(this);
		updateEntityRegion(this);
		if (GameConstants.DEBUG)
			Logger.log(this, "Initiated player: " + username + ", pass: "
					+ getDetails().getPassword());
		getSession().updateIPnPass(this);
	}

	public void start() {
		Logger.globalLog(username, session.getIP(), new String(
				" has logged in."));
		loadMapRegions();
		setStarted(true);
		run();
		if (isDead())
			sendDeath(null);
	}

	public void stopAll() {
		stopAll(true);
	}

	public void stopAll(boolean stopWalk) {
		stopAll(stopWalk, true);
	}

	public void stopAll(boolean stopWalk, boolean stopInterface) {
		stopAll(stopWalk, stopInterface, true);
	}

	public void stopAll(boolean stopWalk, boolean stopInterfaces,
			boolean stopActions) {
		setRouteEvent(null);
		if (stopInterfaces)
			getInterfaceManager().closeInterfaces();
		if (stopWalk) {
			setCoordsEvent(null);
			resetWalkSteps();
		}
		if (stopActions)
			getActionManager().forceStop();
		getCombatDefinitions().resetSpells(false);
	}

	@Override
	public void reset(boolean attributes) {
		super.reset(attributes);
		getInterfaceManager().refreshHitPoints();
		getHintIconsManager().removeAll();
		getSkills().restoreSkills();
		getCombatDefinitions().resetSpecialAttack();
		getPrayer().reset();
		getCombatDefinitions().resetSpells(true);
		setResting((byte) 0);
		getDetails().getPoisonImmunity().set(0);
		getDetails().setAntifireDetails(Optional.empty());
		getDetails().setRunEnergy((byte) 100);
		getAppearance().generateAppearenceData();
	}

	@Override
	public void reset() {
		reset(true);
	}
	
	@Override
	public void loadMapRegions() {
		boolean wasAtDynamicRegion = isAtDynamicRegion();
		super.loadMapRegions();
		setClientLoadedMapRegion(false);
		if (isAtDynamicRegion()) {
			getPackets().sendDynamicGameScene(!isStarted());
			if (!wasAtDynamicRegion)
				getLocalNPCUpdate().reset();
		} else {
			getPackets().sendGameScene(!isStarted());
			if (wasAtDynamicRegion)
				getLocalNPCUpdate().reset();
		}
		getDetails().setForceNextMapLoadRefresh(false);
	}

	public void processLogicPackets() {
		LogicPacket packet;
		while ((packet = getLogicPackets().poll()) != null)
			WorldPacketsDecoder.decodeLogicPacket(this, packet);
	}

	@Override
	public void processEntityUpdate() {
		super.processEntityUpdate();
	}

	@Override
	public void processEntity() {
		processLogicPackets();
		if (isDead())
			return;
		super.processEntity();
		if (getCoordsEvent() != null && getCoordsEvent().processEvent(this))
			setCoordsEvent(null);
		if (getRouteEvent() != null && getRouteEvent().processEvent(this))
			setRouteEvent(null);
		getActionManager().process();
		getPrayer().processPrayer();
		getControllerManager().process();
		getDetails().getCharges().process();
		if (getMusicsManager().musicEnded())
			getMusicsManager().replayMusic();
	}

	@Override
	public void processReceivedHits() {
		if (isLocked())
			return;
		super.processReceivedHits();
	}

	@Override
	public boolean needMasksUpdate() {
		return super.needMasksUpdate() || getTemporaryMovementType() != -1
				|| isUpdateMovementType();
	}

	@Override
	public void resetMasks() {
		super.resetMasks();
		setTemporaryMovementType((byte) -1);
		setUpdateMovementType(false);
		if (!isClientLoadedMapRegion()) {
			// load objects and items here
			setClientLoadedMapRegion(true);
			World.getRegion(getRegionId()).refreshSpawnedObjects(this);
			World.getRegion(getRegionId()).refreshSpawnedItems(this);
		}
	}

	@Override
	public void setRun(boolean run) {
		if (run != getRun()) {
			super.setRun(run);
			setUpdateMovementType(true);
			getInterfaceManager().sendRunButtonConfig();
		}
	}

	public void run() {
		if (World.get().getExiting_start() != 0) {
			short delayPassed = (short) ((Utils.currentTimeMillis() - World.get().getExiting_start()) / 1000);
			getPackets().sendSystemUpdate(World.get().getExiting_delay() - delayPassed);
		}
		getDetails().setLastIP(getSession().getIP());
		getInterfaceManager().sendInterfaces();
		getPackets().sendRunEnergy();
		getInterfaceManager().sendRunButtonConfig();
		CombatEffect.values().forEach($it -> {
			if($it.onLogin(this))
				World.get().submit(new CombatEffectTask(this, $it));
		});
		getPackets().sendGameMessage("Welcome to " + GameConstants.SERVER_NAME + ".");
		
		GameConstants.STAFF.entrySet().parallelStream().filter(p -> getUsername().equalsIgnoreCase(p.getKey())).forEach(staff -> getDetails().setRights(staff.getValue()));
		
		sendDefaultPlayersOptions();
		checkMultiArea();
		getInventory().init();
		getEquipment().checkItems();
		getEquipment().init();
		getSkills().init();
		getCombatDefinitions().init();
		getPrayer().init();
		getFriendsIgnores().init();
		getInterfaceManager().refreshHitPoints();
		getPrayer().refreshPrayerPoints();
		getPackets().sendGameBarStages();
		getMusicsManager().init();
		getNotes().init();
		if (getFamiliar() != null)
			getFamiliar().respawnFamiliar(this);
		else
			getPetManager().init();
		setRunning(true);
		setUpdateMovementType(true);
		getAppearance().generateAppearenceData();
		getControllerManager().login(); // checks what to do on login after welcome
		OwnedObjectManager.linkKeys(this);
		
		if (!HostManager.contains(getUsername(), HostListType.STARTER_RECEIVED)) {
			GameConstants.STATER_KIT.forEach(getInventory()::addItem);
			HostManager.add(this, HostListType.STARTER_RECEIVED, true);
			World.sendWorldMessage("[New Player] " + getDisplayName() + " has just joined " + GameConstants.SERVER_NAME, canPvp);
		}
	}

	public void sendDefaultPlayersOptions() {
		getPackets().sendPlayerOption("Follow", 2, false);
		getPackets().sendPlayerOption("Trade with", 4, false);
		getPackets().sendPlayerOption("Req Assist", 5, false);
	}

	@Override
	public void checkMultiArea() {
		if (!isStarted())
			return;
		boolean isAtMultiArea = isForceMultiArea() ? true : World
				.isMultiArea(this);
		if (isAtMultiArea && !isMultiArea()) {
			setMultiArea(isAtMultiArea);
			getPackets().sendGlobalConfig(616, 1);
		} else if (!isAtMultiArea && isMultiArea()) {
			setMultiArea(isAtMultiArea);
			getPackets().sendGlobalConfig(616, 0);
		}
	}

	/**
	 * Logs the player out.
	 * 
	 * @param lobby
	 *            If we're logging out to the lobby.
	 */
	public void logout(boolean lobby) {
		if (!isRunning())
			return;
		long currentTime = Utils.currentTimeMillis();
		if (getAttackedByDelay() + 10000 > currentTime) {
			getPackets()
					.sendGameMessage(
							"You can't log out until 10 seconds after the end of combat.");
			return;
		}
//		if (getEmotesManager().getNextEmoteEnd() >= currentTime) {
//			getPackets().sendGameMessage(
//					"You can't log out while performing an emote.");
//			return;
//		}
		if (isLocked()) {
			getPackets().sendGameMessage(
					"You can't log out while performing an action.");
			return;
		}
		getPackets().sendLogout(lobby);
		setRunning(false);
	}

	public void forceLogout() {
		getPackets().sendLogout(false);
		setRunning(false);
		realFinish(false);
	}

	@Override
	public void finish() {
		finish(0);
	}

	public void finish(final int tryCount) {
		if (isFinishing() || hasFinished())
			return;
		setFinishing(true);
		// if combating doesnt stop when xlog this way ends combat
		stopAll(false, true,
				!(getActionManager().getAction() instanceof PlayerCombat));
		if (isDead() || (getCombatDefinitions().isUnderCombat() && tryCount < 6) || isLocked()
		/* || getEmotesManager().isDoingEmote() */) {
			CoresManager.slowExecutor.schedule(new Runnable() {
				@Override
				public void run() {
					try {
						setFinishing(false);
						finish(tryCount + 1);
					} catch (Throwable e) {
						Logger.handle(e);
					}
				}
			}, 10, TimeUnit.SECONDS);
			return;
		}
		realFinish(false);
	}
	
	public void realFinish(boolean shutdown) {
		if (hasFinished())
			return;
		Logger.globalLog(username, session.getIP(), new String(
				" has logged out."));
		stopAll();
		getControllerManager().logout();
		setRunning(false);
		getFriendsIgnores().sendFriendsMyStatus(false);
		if (getCurrentFriendChat() != null)
			getCurrentFriendChat().leaveChat(this, true);
		if (getFamiliar() != null && !getFamiliar().isFinished())
			getFamiliar().dissmissFamiliar(true);
		else if (getPet() != null)
			getPet().finish();
		setFinished(true);
		getSession().setDecoder(-1);
		AccountCreation.savePlayer(this);
		updateEntityRegion(this);
		World.removePlayer(this);
		if (GameConstants.DEBUG)
			Logger.log(this, "Finished Player: " + username + ", pass: "
					+ getDetails().getPassword());
	}

	@Override
	public boolean restoreHitPoints() {
		boolean update = super.restoreHitPoints();
		if (update) {
			if (prayer.usingPrayer(0, 9))
				super.restoreHitPoints();
			if (resting != 0)
				super.restoreHitPoints();
			getInterfaceManager().refreshHitPoints();
		}
		return update;
	}

	@Override
	public void removeHitpoints(Hit hit) {
		super.removeHitpoints(hit);
		getInterfaceManager().refreshHitPoints();
	}

	@Override
	public int getMaxHitpoints() {
		return skills.getLevel(Skills.HITPOINTS) * 10
				+ equipment.getEquipmentHpIncrease();
	}

	public int getMessageIcon() {
		return getDetails().getRights() == Rights.ADMINISTRATOR ? 2 : getDetails().getRights() == Rights.MODERATOR ? 1 : 0;
	}

	public WorldPacketsEncoder getPackets() {
		return session.getWorldPackets();
	}

	public void drainRunEnergy() {
		setRunEnergy(getDetails().getRunEnergy() - 1);
	}

	public void setRunEnergy(int runEnergy) {
		if (runEnergy < 0)
			runEnergy = 0;
		else if (runEnergy > 100)
			runEnergy = 100;
		getDetails().setRunEnergy((byte) runEnergy);
		getPackets().sendRunEnergy();
	}

	public boolean isResting() {
		return resting != 0;
	}

	@Override
	public double getMagePrayerMultiplier() {
		return 0.6;
	}

	@Override
	public double getRangePrayerMultiplier() {
		return 0.6;
	}

	@Override
	public double getMeleePrayerMultiplier() {
		return 0.6;
	}

	@Override
	public void handleIngoingHit(final Hit hit) {
		PlayerCombat.handleIncomingHit(this, hit);
	}
	
	@Override
	public void sendDeath(final Entity source) {
		World.get().submit(new PlayerDeath(this));
	}

	public void sendItemsOnDeath(Player killer) {
		if (getDetails().getRights().isStaff())
			return;
		getDetails().getCharges().die();
		CopyOnWriteArrayList<Item> containedItems = new CopyOnWriteArrayList<Item>();
		for (int i = 0; i < 14; i++) {
			if (getEquipment().getItem(i) != null && getEquipment().getItem(i).getId() != -1
					&& getEquipment().getItem(i).getAmount() != -1)
				containedItems.add(new Item(getEquipment().getItem(i).getId(), getEquipment().getItem(i).getAmount()));
		}
		for (int i = 0; i < getInventory().getItemsContainerSize(); i++) {
			if (getInventory().getItem(i) != null && getInventory().getItem(i).getId() != -1
					&& getInventory().getItem(i).getAmount() != -1)
				containedItems.add(new Item(getInventory().getItem(i).getId(), getInventory().getItem(i).getAmount()));
		}
		if (containedItems.isEmpty())
			return;
		int keptAmount = 0;

		keptAmount = getAppearance().hasSkull() ? 0 : 3;
		if (getPrayer().usingPrayer(0, 10) || getPrayer().usingPrayer(1, 0))
			keptAmount++;
		
		CopyOnWriteArrayList<Item> keptItems = new CopyOnWriteArrayList<Item>();
		Item lastItem = new Item(1, 1);
		for (int i = 0; i < keptAmount; i++) {
			for (Item item : containedItems) {
				int price = item.getDefinitions().getValue();
				if (price >= lastItem.getDefinitions().getValue()) {
					lastItem = item;
				}
			}
			keptItems.add(lastItem);
			containedItems.remove(lastItem);
			lastItem = new Item(1, 1);
		}
		getInventory().reset();
		getEquipment().reset();
		for (Item item : keptItems) {
			getInventory().addItem(item);
		}
		/** This Checks which items that is listed in the 'PROTECT_ON_DEATH' **/
		for (Item item : containedItems) {	// This checks the items you had in your inventory or equipped
			for (String string : GameConstants.PROTECT_ON_DEATH) {	//	This checks the matched items from the list 'PROTECT_ON_DEATH'
				if (item.getDefinitions().getName().toLowerCase().contains(string) || item.getDefinitions().exchangableItem) {
					getInventory().addItem(item);	//	This adds the items that is matched and listed in 'PROTECT_ON_DEATH'
					containedItems.remove(item);	//	This remove the whole list of the contained items that is matched
				}
			}
		}

		/** This to avoid items to be dropped in the list 'PROTECT_ON_DEATH' **/
		for (Item item : containedItems) {	//	This checks the items you had in your inventory or equipped
			for (String string : GameConstants.PROTECT_ON_DEATH) {	//	This checks the matched items from the list 'PROTECT_ON_DEATH'
				if (item.getDefinitions().getName().toLowerCase().contains(string)) {
					containedItems.remove(item);	//	This remove the whole list of the contained items that is matched
				}
			}
			FloorItem.createGroundItem(item, getLastWorldTile(), killer == null ? this : killer, false, 180, true, true);	//	This dropps the items to the killer, and is showed for 180 seconds
		}
		for (Item item : containedItems) {
			FloorItem.createGroundItem(item, getLastWorldTile(), killer == null ? this : killer, false, 180, true, true);
		}
	}

	@Override
	public int getSize() {
		return getAppearance().getSize();
	}

	public void setCanPvp(boolean canPvp) {
		setCanPvp(canPvp);
		getAppearance().generateAppearenceData();
		getPackets().sendPlayerOption(canPvp ? "Attack" : "null", 1, true);
		getPackets().sendPlayerUnderNPCPriority(canPvp);
	}

	public boolean isLocked() {
		return getLockDelay() > WorldThread.WORLD_CYCLE;// Utils.currentTimeMillis();
	}

	public void lock() {
		setLockDelay(Long.MAX_VALUE);
	}

	public void lock(long time) {
		setLockDelay(time == -1 ? Long.MAX_VALUE : WorldThread.WORLD_CYCLE + time);
	}

	public void unlock() {
		setLockDelay(0);
	}

	@Override
	public void heal(int ammount, int extra) {
		super.heal(ammount, extra);
		getInterfaceManager().refreshHitPoints();
	}

	public void addLogicPacketToQueue(LogicPacket packet) {
		getLogicPackets().stream().filter(type -> type.getId() == packet.getId()).forEach(logical -> getLogicPackets().remove(logical));
		getLogicPackets().add(packet);
	}

	public int getMovementType() {
		if (getTemporaryMovementType() != -1)
			return getTemporaryMovementType();
		return getRun() ? RUN_MOVE_TYPE : WALK_MOVE_TYPE;
	}
	
	public String getDisplayName() {
		return Utils.formatPlayerNameForDisplay(username);
	}

	/**
	 * Sends a delayed task for this player.
	 */
	public void task(int delay, Consumer<Player> action) {
		Player player = this;
		new Task(delay, false) {
			@Override
			protected void execute() {
				action.accept(player);
				cancel();
			}
		}.submit();
	}
}