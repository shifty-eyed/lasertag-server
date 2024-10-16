	package net.lasertag.lasertagserver.core;

import jakarta.annotation.PostConstruct;
import net.lasertag.lasertagserver.model.MessageFromDevice;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.net.InetAddress;
import java.util.concurrent.Executor;

@Component
public class VestCommunication extends AbstractUdpServer {

	private final Executor daemonExecutor;
	private final Game game;

	public VestCommunication(PlayerRegistry playerRegistry,
							 Executor daemonExecutor,
							 Game game) {
		super(9877, playerRegistry);
		this.daemonExecutor = daemonExecutor;
		this.game = game;
	}

	@PostConstruct
	public void init() {
		daemonExecutor.execute(this::startUdpServer);
		Runtime.getRuntime().addShutdownHook(new Thread(this::stopUdpServer));
	}

	@Override
	protected InetAddress getDeviceIp(Player player) {
		return player.getVestIp();
	}

	@Override
	protected void setDeviceIp(Player player, InetAddress ip) {
		player.setVestIp(ip);
	}

	@Override
	protected void onMessageReceived(MessageFromDevice message) {
		var player = playerRegistry.getPlayerById(message.getPlayerId());
		var hitByPlayer = playerRegistry.getPlayerById(message.getHitByPlayerId());
		switch (message.getType()) {
			case MessageFromDevice.TYPE_VEST_HIT -> game.eventVestGotHit(player, hitByPlayer);
			default -> throw new IllegalArgumentException("Unknown message type from vest: " + message.getType());
		}
	}

}
