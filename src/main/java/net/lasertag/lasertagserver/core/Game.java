package net.lasertag.lasertagserver.core;

import lombok.Getter;
import net.lasertag.lasertagserver.model.MessageToPhone;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.stereotype.Component;

@Component
@Getter
public class Game {

	private final int SHOT_DAMAGE = 1;

	private final PlayerRegistry playerRegistry;
	private final PhoneCommunication phoneComm;
	private final GunCommunication gunComm;
	private final VestCommunication vestComm;

	private boolean gameStarted = false;
	private int fragLimit = 10;
	private int timeLimitMinutes = 10;
	private int timeLeftSeconds = 0;


	public Game(PlayerRegistry playerRegistry, PhoneCommunication phoneComm, GunCommunication gunComm, VestCommunication vestComm) {
		this.playerRegistry = playerRegistry;
		this.phoneComm = phoneComm;
		this.gunComm = gunComm;
		this.vestComm = vestComm;
	}

	public void eventGunShot(Player player) {
		if (player.getBulletsLeft() > 0) {
			player.setBulletsLeft(player.getBulletsLeft() - 1);
			phoneComm.sendEventToPhone(MessageToPhone.GUN_SHOT, player, 0);
		} else {
			phoneComm.sendEventToPhone(MessageToPhone.GUN_NO_BULLETS, player, 0);
		}
	}

	public void eventGunReload(Player player) {
		player.setBulletsLeft(player.getMagazineSize());
		phoneComm.sendEventToPhone(MessageToPhone.GUN_RELOAD, player, 0);
	}

	public void eventVestGotHit(Player player, Player hitByPlayer) {
		player.setHealth(player.getHealth() - SHOT_DAMAGE);
		if (player.getHealth() >= 0) {
			phoneComm.sendEventToPhone(MessageToPhone.GOT_HIT, player, hitByPlayer.getId());
			phoneComm.sendEventToPhone(MessageToPhone.YOU_HIT_SOMEONE, hitByPlayer, player.getId());
		} else {
			hitByPlayer.setScore(hitByPlayer.getScore() + 1);
			phoneComm.sendEventToPhone(MessageToPhone.YOU_KILLED, player, hitByPlayer.getId());
			phoneComm.sendEventToPhone(MessageToPhone.YOU_SCORED, hitByPlayer, player.getId());
			if (hitByPlayer.getScore() >= fragLimit) {
				endGame(hitByPlayer);
			}
		}
	}

	public void endGame(Player winner) {
		phoneComm.sendEventToPhone(MessageToPhone.GAME_OVER, winner, 0);
		gameStarted = false;
	}


}
