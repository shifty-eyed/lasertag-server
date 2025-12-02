package net.lasertag.lasertagserver.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.lasertag.lasertagserver.model.Actor;
import net.lasertag.lasertagserver.model.Dispenser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Component
@Slf4j
public class GameSettings {

    private static final String PRESETS_DIR = "presets";
    private static final String JSON_EXTENSION = ".json";

    private final ObjectMapper objectMapper;

    @Getter
    private GameSettingsPreset current;

    public GameSettings() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.current = new GameSettingsPreset();
    }

    public void savePreset(String fileName) throws IOException {
        Path presetsPath = Paths.get(PRESETS_DIR);
        if (!Files.exists(presetsPath)) {
            Files.createDirectories(presetsPath);
            log.info("Created presets directory: {}", presetsPath.toAbsolutePath());
        }

        String normalizedFileName = normalizeFileName(fileName);
        Path filePath = presetsPath.resolve(normalizedFileName);
        objectMapper.writeValue(filePath.toFile(), current);
        log.info("Saved preset to: {}", filePath.toAbsolutePath());
    }

    public void loadPreset(String fileName) throws IOException {
        String normalizedFileName = normalizeFileName(fileName);
        Path filePath = Paths.get(PRESETS_DIR).resolve(normalizedFileName);

        if (!Files.exists(filePath)) {
            throw new IOException("Preset file not found: " + filePath.toAbsolutePath());
        }

        current = objectMapper.readValue(filePath.toFile(), GameSettingsPreset.class);
        log.info("Loaded preset from: {}", filePath.toAbsolutePath());
    }

    public List<String> listPresets() throws IOException {
        Path presetsPath = Paths.get(PRESETS_DIR);
        if (!Files.exists(presetsPath)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(presetsPath)) {
            return files
                .filter(path -> path.toString().endsWith(JSON_EXTENSION))
                .map(path -> path.getFileName().toString().replace(JSON_EXTENSION, ""))
                .toList();
        }
    }

    private String normalizeFileName(String fileName) {
        if (!fileName.endsWith(JSON_EXTENSION)) {
            return fileName + JSON_EXTENSION;
        }
        return fileName;
    }

    public void syncToActors(ActorRegistry actorRegistry) {
        actorRegistry.streamPlayers().forEach(player -> {
            GameSettingsPreset.PlayerSettings settings = current.getPlayerSettings(player.getId());
            player.setName(settings.getName());
            player.setBulletsMax(settings.getBulletsMax());
            player.setDamage(settings.getDamage());
            player.setTeamId(settings.getTeamId());
        });

        actorRegistry.streamByType(Actor.Type.HEALTH).forEach(actor -> {
            Dispenser dispenser = (Dispenser) actor;
            var healthSettings = current.getHealthDispenserSettings();
            dispenser.setDispenseTimeoutSec(healthSettings.getTimeout());
            dispenser.setAmount(healthSettings.getAmount());
        });

        actorRegistry.streamByType(Actor.Type.AMMO).forEach(actor -> {
            Dispenser dispenser = (Dispenser) actor;
            var ammoSettings = current.getAmmoDispenserSettings();
            dispenser.setDispenseTimeoutSec(ammoSettings.getTimeout());
            dispenser.setAmount(ammoSettings.getAmount());
        });
    }

}

