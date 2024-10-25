package net.lasertag.lasertagserver.core;

import jakarta.annotation.PostConstruct;
import net.lasertag.lasertagserver.model.MessageFromDevice;
import net.lasertag.lasertagserver.model.MessageToPhone;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.stereotype.Component;

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
	protected void onMessageReceived(MessageFromDevice message) {
		//nothing to do as ping already handled in AbstractUdpServer
	}

	public void sendEventToPhone(byte type, Player player, int counterpartPlayerId) {
		var bytes = MessageToPhone.eventToBytes(type, player, counterpartPlayerId);
		sendBytesToClient(player, bytes);
	}

	public void sendStatsToAll() {
		var bytes = MessageToPhone.playerStatsToBytes(playerRegistry.getPlayersSortedByScore());
		playerRegistry.getPlayers().forEach(player -> sendBytesToClient(player, bytes));
	}

}
