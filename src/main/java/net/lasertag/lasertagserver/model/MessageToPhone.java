package net.lasertag.lasertagserver.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

@AllArgsConstructor
@Getter
public class MessageToPhone {

	public static final byte PING = 1;
	public static final byte GUN_SHOT = 2;
	public static final byte GUN_RELOAD = 3;
	public static final byte YOU_HIT_SOMEONE = 4;
	public static final byte GOT_HIT = 5;
	public static final byte RESPAWN = 6;
	public static final byte GAME_OVER = 7;
	public static final byte GAME_START = 8;
	public static final byte YOU_KILLED = 9;
	public static final byte YOU_SCORED = 10;
	public static final byte FULL_STATS = 11;
	public static final byte GUN_NO_BULLETS = 12;

	private byte type;
	private byte counterpartPlayerId;
	private byte health;
	private byte score;
	private byte bulletsLeft;

	public static byte[] eventToBytes(byte type, Player player, int counterpartPlayerId) {
		return new byte[]{type, (byte)counterpartPlayerId, (byte)player.getHealth(), (byte)player.getScore(), (byte)player.getBulletsLeft()};
	}

	public static byte[] playerStatsToBytes(List<Player> players) {
		ByteBuffer data = ByteBuffer.allocate(1 + players.size() * 4);
		data.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		data.put(FULL_STATS);
		for (Player player : players) {
			data.put((byte)player.getId());
			data.put((byte)player.getScore());
			data.put(Arrays.copyOf(player.getName().getBytes(), 32));
		}

		return data.array();
	}

}
