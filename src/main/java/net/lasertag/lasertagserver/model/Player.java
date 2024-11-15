package net.lasertag.lasertagserver.model;

import lombok.Getter;
import lombok.Setter;
import net.lasertag.lasertagserver.core.Game;

import java.net.InetAddress;

import static net.lasertag.lasertagserver.core.Game.GUN_FIRE_INTERVAL_MILLIS;

@Getter
public class Player {

	private final int id;

	@Setter
	private String name;
	@Setter
	private int score;
	@Setter
	private int health;
	@Setter
	private int maxHealth;

	private int bulletsLeft;
	@Setter
	private int magazineSize;
	@Setter
	private int respawnTimeSeconds;
	@Setter
	private int teamId;
	@Setter
	private int damage;

	@Setter
	private int respawnCounter;
	@Setter
	private InetAddress gunIp;
	@Setter
	private InetAddress vestIp;
	@Setter
	private InetAddress phoneIp;

	private long lastShotTime = 0;

	public Player(int id, String name, int maxHealth) {
		this.id = id;
		this.name = name;
		this.maxHealth = maxHealth;
		this.health = maxHealth;
		this.magazineSize = 10;
		this.score = 0;
		this.respawnTimeSeconds = 20;
		this.teamId = Game.TEAM_YELLOW;
		this.damage = 10;
		this.respawnCounter = 0;
	}

	public void shoot() {
		if (bulletsLeft > 0) {
			bulletsLeft--;
			lastShotTime = System.currentTimeMillis();
		}
	}

	public void reload() {
		bulletsLeft = magazineSize;
	}

	public boolean canHit() {
		return bulletsLeft > 0
			|| (System.currentTimeMillis() - lastShotTime) < GUN_FIRE_INTERVAL_MILLIS;
	}

	public boolean isOnline() {
		return gunIp != null || vestIp != null || phoneIp != null;
	}

	public String devicesOnline() {
		return (gunIp != null ? "G" : "") + (vestIp != null ? "V" : "") + (phoneIp != null ? "P" : "");
	}

	public boolean isAlive() {
		return health > 0;
	}

	public void reset() {
		health = maxHealth;
		reload();
	}

}
