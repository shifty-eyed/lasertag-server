package net.lasertag.lasertagserver.model;

import lombok.Getter;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class Messaging {

	public static final byte PLAYER_PING = 1;
	public static final byte YOU_HIT_SOMEONE = 4;
	public static final byte GOT_HIT = 5;
	public static final byte RESPAWN = 6;
	public static final byte GAME_OVER = 7;
	public static final byte GAME_START = 8;
	public static final byte YOU_KILLED = 9;
	public static final byte YOU_SCORED = 10;
	public static final byte FULL_STATS = 11;

	public static final byte DEVICE_PLAYER_STATE = 13;

	public static final byte RESPAWN_POINT_PING = 14;
	public static final byte HEALTH_DISPENSER_PING = 15;
	public static final byte AMMO_DISPENSER_PING = 16;

	//todo implement sending of these events
	public static final byte DISPENSER_USED = 17;
	public static final byte DISPENSER_SET_AMOUNT = 18;
	public static final byte DISPENSER_SET_TIMEOUT = 19;



	public static final byte GAME_TIMER = 101;
	public static final byte LOST_CONNECTION = 102;

	public static final int TEAM_RED = 0;
	public static final int TEAM_BLUE = 1;
	public static final int TEAM_GREEN = 2;
	public static final int TEAM_YELLOW = 3;
	public static final int TEAM_PURPLE = 4;
	public static final int TEAM_CYAN = 5;

	public static final Set<Byte> PING_GROUP = new HashSet<>(Arrays.asList(PLAYER_PING, RESPAWN_POINT_PING, HEALTH_DISPENSER_PING, AMMO_DISPENSER_PING));

	@Getter
	public static class MessageFromClient extends Messaging {

		private final byte type;
		private final byte actorId;
		private final byte extraValue;
		private final byte health;
		private final byte score;//?
		private final boolean firstEverMessage;

		public MessageFromClient(byte[] bytes, int length) {
			if (length < 3) {
				throw new IllegalArgumentException("Invalid message: " + Arrays.toString(bytes));
			}
			this.type = bytes[0];
			this.actorId = bytes[1];
			if (PING_GROUP.contains(this.type)) {
				this.firstEverMessage = bytes[2] != 0;
				this.extraValue = 0;
				this.health = 0;
				this.score = 0;
			} else if (length == 6) {
				this.extraValue = bytes[2];
				this.health = bytes[3];
				this.score = bytes[4];
				this.firstEverMessage = false;
			} else {
				throw new IllegalArgumentException("Invalid message: " + Arrays.toString(bytes));
			}
		}

		@Override
		public String toString() {
			return "MessageFromClient{" +
				"type=" + type +
				", p=" + actorId +
				", extraValue=" + extraValue +
				", h=" + health +
				", s=" + score +
				", first=" + firstEverMessage +
				'}';
		}
	}

	public static byte[] eventToBytes(byte type, int payload) {
		return new byte[]{type, (byte)payload};
	}

	public static byte[] eventStartGameToBytes(boolean teamPlay, int gameTimeMinutes) {
		return new byte[] { GAME_START,
			(byte)(teamPlay ? 1 : 0),
			(byte) gameTimeMinutes
		};
	}

	public static byte[] playerStatsToBytes(boolean includeNames, List<Player> players, boolean gameRunning, boolean teamPlay, int timeSeconds) {
		var size = 6 + getPlayersSize(players, includeNames);
		ByteBuffer data = ByteBuffer.allocate(size);
		data.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		data.put(FULL_STATS); //byte 1
		data.put((byte)(gameRunning ? 1 : 0)); //byte 2
		data.put((byte)(teamPlay ? 1 : 0)); //byte 3
		data.putShort((short)(timeSeconds)); //byte 4,5
		data.put((byte)players.size()); //byte 6
		for (Player player : players) {
			data.put((byte)player.getId()); //byte 1
			data.put((byte)player.getHealth()); //byte 2
			data.put((byte)player.getScore()); //byte 3
			data.put((byte)player.getTeamId()); //byte 4
			data.put((byte)player.getDamage()); //byte 5
			if (includeNames) {
				data.put((byte) player.getName().length()); //byte 6
				data.put(player.getName().getBytes());
			} else {
				data.put((byte) 0); //byte 6
			}
		}
		return data.array();
	}

	private static int getPlayersSize(List<Player> players, boolean includeNames) {
		int size = 0;
		for (Player player : players) {
			size += 6 + (includeNames ? player.getName().length() : 0);
		}
		return size;
	}

}
