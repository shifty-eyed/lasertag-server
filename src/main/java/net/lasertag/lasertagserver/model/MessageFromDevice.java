package net.lasertag.lasertagserver.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.ByteBuffer;
import java.util.Arrays;

@AllArgsConstructor
@Getter
public class MessageFromDevice {

	public static final byte TYPE_PING = 1;
	public static final byte TYPE_GUN_SHOT = 2;
	public static final byte TYPE_GUN_RELOAD = 3;
	public static final byte TYPE_VEST_HIT = 4;


	private byte type;
	private byte playerId;
	private byte hitByPlayerId;

	public static MessageFromDevice fromBytes(byte[] bytes, int length) {
		if (length != 3) {
			throw new IllegalArgumentException("Invalid message: " + Arrays.toString(bytes));
		}
		ByteBuffer data = ByteBuffer.wrap(bytes);
		data.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		var type = data.get();
		var playerId = data.get();
		var hitByPlayerId = data.get();

		return new MessageFromDevice(type, playerId, hitByPlayerId);
	}

}
