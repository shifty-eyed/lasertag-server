package net.lasertag.lasertagserver.core;

import jakarta.annotation.PostConstruct;
import net.lasertag.lasertagserver.model.MessageFromDevice;
import net.lasertag.lasertagserver.model.MessageToPhone;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.Executor;

@Component
public class PhoneCommunication extends AbstractUdpServer {

	private final Executor daemonExecutor;

	public PhoneCommunication(PlayerRegistry playerRegistry, Executor daemonExecutor) {
		super(9878, 1234, playerRegistry);
		this.daemonExecutor = daemonExecutor;
	}

	@PostConstruct
	public void init() {
		daemonExecutor.execute(this::startUdpServer);
		Runtime.getRuntime().addShutdownHook(new Thread(this::stopUdpServer));
	}

	@Override
	protected InetAddress getDeviceIp(Player player) {
		return player.getPhoneIp();
	}

	@Override
	protected void setDeviceIp(Player player, InetAddress ip) {
		player.setPhoneIp(ip);
	}

	@Override
	protected void onMessageReceived(MessageFromDevice message) {}

	public void sendEventToPhone(byte type, Player player, int extraValue) {
		var bytes = MessageToPhone.eventToBytes(type, player, extraValue);
		sendBytesToClient(player, bytes);
	}

	public void sendStatsToAll(boolean isGameRunning, boolean teamPlay) {
		var bytes = MessageToPhone.playerStatsToBytes(playerRegistry.getPlayersSortedByScore(), isGameRunning, teamPlay);
		playerRegistry.getOnlinePlayers().forEach(player -> sendBytesToClient(player, bytes));
	}

	public void sendGameTimeToAll(int munites, int seconds) {
		var bytes = MessageToPhone.gameTimeToBytes(munites, seconds);
		playerRegistry.getPlayers().forEach(player -> sendBytesToClient(player, bytes));
	}

	public void sendGameTimeToPlayer(Player player, int munites, int seconds) {
		var bytes = MessageToPhone.gameTimeToBytes(munites, seconds);
		sendBytesToClient(player, bytes);
	}

}
