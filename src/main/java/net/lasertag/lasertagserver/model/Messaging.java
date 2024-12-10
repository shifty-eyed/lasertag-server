package net.lasertag.lasertagserver.model;

import lombok.Getter;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public abstract class Messaging {

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
	public static final byte DEVICE_PLAYER_STATE = 13;

	public static final byte GAME_TIMER = 101;
	public static final byte LOST_CONNECTION = 102;

	public static final int TEAM_RED = 0;
	public static final int TEAM_BLUE = 1;
	public static final int TEAM_GREEN = 2;
	public static final int TEAM_YELLOW = 3;
	public static final int TEAM_PURPLE = 4;
	public static final int TEAM_CYAN = 5;


	@Getter
	public static class MessageFromClient extends Messaging {

		private final byte type;
		private final byte playerId;
		private final byte otherPlayerId;
		private final byte health;
		private final byte score;
		private final byte bulletsLeft;
		private final boolean firstEverMessage;

		public MessageFromClient(byte[] bytes, int length) {
			if (length < 3) {
				throw new IllegalArgumentException("Invalid message: " + Arrays.toString(bytes));
			}
			this.type = bytes[0];
			this.playerId = bytes[1];
			if (this.type == PING) {
				this.firstEverMessage = bytes[2] != 0;
				this.otherPlayerId = 0;
				this.health = 0;
				this.score = 0;
				this.bulletsLeft = 0;
			} else if (length == 6) {
				this.otherPlayerId = bytes[2];
				this.health = bytes[3];
				this.score = bytes[4];
				this.bulletsLeft = bytes[5];
				this.firstEverMessage = false;
			} else {
				throw new IllegalArgumentException("Invalid message: " + Arrays.toString(bytes));
			}
		}
	}

	public static byte[] timeCorrectionToBytes(int minutes, int seconds) {
		return new byte[]{GAME_TIMER, (byte)minutes, (byte)seconds};
	}

	public static byte[] eventToBytes(byte type, int payload) {
		return new byte[]{type, (byte)payload};
	}

	public static byte[] eventStartGameToBytes(boolean teamPlay, int respawnTimeSeconds, int gameTimeMinutes) {
		return new byte[] { GAME_START,
			(byte)(teamPlay ? 1 : 0),
			(byte) respawnTimeSeconds,
			(byte) gameTimeMinutes,
		};
	}


	public static byte[] playerStatsToBytes(List<Player> players, boolean gameRunning, boolean teamPlay) {
		var size = 4 + getPlayersSize(players);
		ByteBuffer data = ByteBuffer.allocate(size);
		data.put(FULL_STATS); //byte 1
		data.put((byte)(gameRunning ? 1 : 0)); //byte 2
		data.put((byte)(teamPlay ? 1 : 0)); //byte 3
		data.put((byte)players.size()); //byte 4
		for (Player player : players) {
			data.put((byte)player.getId()); //byte 1
			data.put((byte)player.getHealth()); //byte 2
			data.put((byte)player.getScore()); //byte 3
			data.put((byte)player.getTeamId()); //byte 4
			data.put((byte)player.getDamage()); //byte 5
			data.put((byte)player.getBulletsLeft()); //byte 6
			data.put((byte)player.getName().length()); //byte 7
			data.put(player.getName().getBytes());
		}
		return data.array();
	}

	private static int getPlayersSize(List<Player> players) {
		int size = 0;
		for (Player player : players) {
			size += 7 + player.getName().length();
		}
		return size;
	}

}
