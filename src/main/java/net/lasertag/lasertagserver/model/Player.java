package net.lasertag.lasertagserver.model;

import lombok.Getter;
import lombok.Setter;

import java.net.InetAddress;

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
	@Setter
	private int bulletsLeft;
	@Setter
	private int magazineSize;
	@Setter
	private InetAddress gunIp;
	@Setter
	private InetAddress vestIp;
	@Setter
	private InetAddress phoneIp;

	public Player(int id, String name, int maxHealth) {
		this.id = id;
		this.name = name;
		this.maxHealth = maxHealth;
		this.health = maxHealth;
		this.magazineSize = 10;
		this.score = 0;
	}

	public boolean isOnline() {
		return gunIp != null && vestIp != null && phoneIp != null;
	}

	public String devicesOnline() {
		return (gunIp != null ? "G" : "") + (vestIp != null ? "V" : "") + (phoneIp != null ? "P" : "");
	}

	public boolean isAlive() {
		return health > 0;
	}

	public void respawn() {
		health = maxHealth;
		bulletsLeft = magazineSize;
	}

}
