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
	private int bulletsLeft;
	@Setter
	private int teamId;
	@Setter
	private int damage;

	@Setter
	private InetAddress clientIp;

	public Player(int id, String name, int maxHealth) {
		this.id = id;
		this.name = name;
		this.health = maxHealth;
		this.score = 0;
		this.teamId = Messaging.TEAM_YELLOW;
		this.damage = 10;
	}

	public boolean isOnline() {
		return clientIp != null;
	}

	public String devicesOnline() {
		return (clientIp != null ? "\u2713" : "");
	}

}
