package net.lasertag.lasertagserver.core;

import lombok.Data;
import net.lasertag.lasertagserver.model.Actor;
import net.lasertag.lasertagserver.model.Dispenser;
import net.lasertag.lasertagserver.model.Messaging;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Data
public class GameSettings {

    @Data
	public static class PlayerSettings {
		private String name;
		private Integer bulletsMax;
		private Integer damage;
		private Integer teamId;
	}

	@Data
	public static class DispenserSettings {
		private int timeout;
		private int amount;

		public DispenserSettings(int timeout, int amount) {
			this.timeout = timeout;
			this.amount = amount;
		}
	}

	private int fragLimit = 10;
	private boolean teamPlay = false;
	private int timeLimitMinutes = 15;

	private final Map<Integer, PlayerSettings> playerSettings = new HashMap<>();

	private final DispenserSettings healthDispenserSettings = new DispenserSettings(60, 40);
	private final DispenserSettings ammoDispenserSettings = new DispenserSettings(60, 40);

	public GameSettings() {
        //Default settings
		for (int i = 0; i < ActorRegistry.PLAYER_COUNT; i++) {
			PlayerSettings settings = new PlayerSettings();
			settings.setName("Player-%d".formatted(i));
			settings.setDamage(10);
			settings.setBulletsMax(40);
			settings.setTeamId(Messaging.TEAM_YELLOW);
			playerSettings.put(i, settings);
		}
	}

	public Map<String, Object> getAllSettings() {
		Map<String, Object> allSettings = new HashMap<>();
        Map<String, Object> general = new HashMap<>();
		general.put("fragLimit", fragLimit);
		general.put("teamPlay", teamPlay);
		general.put("timeLimitMinutes", timeLimitMinutes);
		allSettings.put("general", general);
		allSettings.put("players", getAllPlayerSettings());
		allSettings.put("dispensers", Map.of(
			"health", Map.of(
				"timeout", healthDispenserSettings.getTimeout(),
				"amount", healthDispenserSettings.getAmount()
			),
			"ammo", Map.of(
				"timeout", ammoDispenserSettings.getTimeout(),
				"amount", ammoDispenserSettings.getAmount()
			)
		));
		return allSettings;
	}

	// Player settings methods
	public PlayerSettings getPlayerSettings(int playerId) {
		return playerSettings.get(playerId);
	}

	public Map<Integer, PlayerSettings> getAllPlayerSettings() {
		return new HashMap<>(playerSettings);
	}

	public void setPlayerSettings(int playerId, PlayerSettings newSettings) {
		if (newSettings == null) {
			throw new IllegalArgumentException("Player settings cannot be null");
		}

		PlayerSettings storedSettings = playerSettings.computeIfAbsent(playerId, id -> new PlayerSettings());
		storedSettings.setName(newSettings.getName());
		storedSettings.setBulletsMax(newSettings.getBulletsMax());
		storedSettings.setDamage(newSettings.getDamage());
		storedSettings.setTeamId(newSettings.getTeamId());
	}

	// Dispenser settings methods
	public DispenserSettings getDispenserSettings(Actor.Type type) {
		if (type == Actor.Type.HEALTH) {
			return healthDispenserSettings;
		} else if (type == Actor.Type.AMMO) {
			return ammoDispenserSettings;
		}
		throw new IllegalArgumentException("Unknown dispenser type: " + type);
	}

	public void setDispenserTimeout(Actor.Type type, int timeout) {
		getDispenserSettings(type).setTimeout(timeout);
	}

	public void setDispenserAmount(Actor.Type type, int amount) {
		getDispenserSettings(type).setAmount(amount);
	}

	public void syncToActors(ActorRegistry actorRegistry) {
		actorRegistry.streamPlayers().forEach(player -> {
			PlayerSettings settings = playerSettings.get(player.getId());
            player.setName(settings.getName());
            player.setBulletsMax(settings.getBulletsMax());
            player.setDamage(settings.getDamage());
            player.setTeamId(settings.getTeamId());
		});

		actorRegistry.streamByType(Actor.Type.HEALTH).forEach(actor -> {
			Dispenser dispenser = (Dispenser) actor;
			dispenser.setDispenseTimeoutSec(healthDispenserSettings.getTimeout());
			dispenser.setAmount(healthDispenserSettings.getAmount());
		});

		actorRegistry.streamByType(Actor.Type.AMMO).forEach(actor -> {
			Dispenser dispenser = (Dispenser) actor;
			dispenser.setDispenseTimeoutSec(ammoDispenserSettings.getTimeout());
			dispenser.setAmount(ammoDispenserSettings.getAmount());
		});
	}

}

