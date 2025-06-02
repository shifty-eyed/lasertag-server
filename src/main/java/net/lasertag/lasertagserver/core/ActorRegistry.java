package net.lasertag.lasertagserver.core;

import lombok.Getter;
import net.lasertag.lasertagserver.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Getter
public class ActorRegistry {

	private final List<Actor> actors = new ArrayList<>();

	public ActorRegistry() {// this should be in config screen before running the game
		for (int i = 0; i < 6; i++) {
			actors.add(new Player(i, "Player-%d".formatted(i+1), 100));
			actors.add(new RespawnPoint(i));
		}
		actors.add(new Dispenser(0, Actor.Type.AMMO_DISPENSER));
		actors.add(new Dispenser(1, Actor.Type.AMMO_DISPENSER));

		actors.add(new Dispenser(0, Actor.Type.HEALTH_DISPENSER));
		actors.add(new Dispenser(1, Actor.Type.HEALTH_DISPENSER));
	}

	public Stream<Actor> streamByType(Actor.Type type) {
		return actors.stream()
			.filter(actor -> actor.getType() == type);
	}

	public int getActorCountByType(Actor.Type type) {
		return (int) streamByType(type).count();
	}

	public Stream<Player> streamPlayers() {
		return streamByType(Actor.Type.PLAYER)
			.map(actor -> (Player) actor);
	}

	public List<Player> getPlayers() {
		return streamPlayers().toList();
	}

	public Actor getActorByTypeAndId(Actor.Type type, int id) {
		return actors.stream()
			.filter(actor -> actor.getType() == type && actor.getId() == id)
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("Actor not found: type:" + type + ", id: " + id));
	}

	public Collection<RespawnPoint> getRespawnPoints() {
		return actors.stream()
			.filter(actor -> actor.getType() == Actor.Type.RESPAWN_POINT)
			.map(actor -> (RespawnPoint) actor)
			.toList();
	}

	public Actor getActorByMessage(Messaging.MessageFromClient message) {
		var id = message.getActorId();
		var type = switch (message.getType()) {
			case Messaging.RESPAWN_POINT_PING -> Actor.Type.RESPAWN_POINT;
			case Messaging.HEALTH_DISPENSER_PING -> Actor.Type.HEALTH_DISPENSER;
			case Messaging.AMMO_DISPENSER_PING -> Actor.Type.AMMO_DISPENSER;
			default -> Actor.Type.PLAYER;
		};
		return getActorByTypeAndId(type, id);
	}

	public LinkedHashMap<Integer, Integer> getTeamScores() {
		// teamId -> score, sorted by highest score
		return streamPlayers()
			.collect(Collectors.groupingBy(Player::getTeamId, Collectors.summingInt(Player::getScore)))
			.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
	}

	public Player getPlayerById(int id) {
		return (Player)getActorByTypeAndId(Actor.Type.PLAYER, id);
	}

	public List<Player> getPlayersSortedByScore() {
		return streamPlayers().sorted((p1, p2) -> p2.getScore() - p1.getScore()).toList();
	}

	public Player getLeadPlayer() {
		var maxScore = streamPlayers().mapToInt(Player::getScore).max().orElse(0);
		var leadPlayers = streamPlayers().filter(player -> player.getScore() == maxScore).toList();
		return leadPlayers.size() == 1 ? leadPlayers.get(0) : null;
	}

	public int getLeadTeam() {
		var teamScores = getTeamScores();
		var maxScore = teamScores.values().stream().max(Integer::compareTo).orElse(0);
		var leadTeams = teamScores.entrySet().stream()
			.filter(entry -> Objects.equals(entry.getValue(), maxScore))
			.map(Map.Entry::getKey)
			.toList();
		return leadTeams.size() == 1 ? leadTeams.get(0) : -1;
	}

	public List<Integer> shuffledRespawnPointIds() {
		var respawnPointsIds = streamByType(Actor.Type.RESPAWN_POINT).map(Actor::getId).collect(Collectors.toList());
		Collections.shuffle(respawnPointsIds);
		var pointsToMakeUp = streamPlayers().count() - respawnPointsIds.size();
		for (int i = 0; i < pointsToMakeUp; i++) {
		    respawnPointsIds.add(respawnPointsIds.get(i % respawnPointsIds.size()));
		}
		return respawnPointsIds;
	}

	public int getRandomRespawnPointId() {
		var respawnPointsIds = streamByType(Actor.Type.RESPAWN_POINT).map(Actor::getId).toList();
		return respawnPointsIds.get(new Random().nextInt(respawnPointsIds.size()));
	}

}
