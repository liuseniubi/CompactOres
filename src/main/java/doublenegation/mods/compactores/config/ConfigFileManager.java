package doublenegation.mods.compactores.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.google.common.collect.ImmutableMap;
import doublenegation.mods.compactores.CompactOres;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

public class ConfigFileManager {

    private static final Logger LOGGER = LogManager.getLogger();

    private Path configDir;
    private Path definitionConfigDir;
    private Path customizationConfigDir;
    private Path versionConfig;

    private CommentedFileConfig configVersionConfig;
    private Set<FileConfig> definitionConfigs = new HashSet<>();
    private Set<FileConfig> customizationConfigs = new HashSet<>();

    public ConfigFileManager() {
        // prepare directory structure
        Path forgeConfigDir = FMLPaths.CONFIGDIR.get();
        configDir = forgeConfigDir.resolve("compactores");
        definitionConfigDir = configDir.resolve("definitions");
        customizationConfigDir = configDir.resolve("customizations");
        versionConfig = configDir.resolve("README.toml");
    }

    public void load() {
        requireDirectory(configDir);
        requireDirectory(definitionConfigDir);
        requireDirectory(customizationConfigDir);

        // do config version check
        boolean generateNewConfig = !Files.exists(versionConfig);
        if(!generateNewConfig/* = Files.exists(versionConfig) */ && (!Files.isRegularFile(versionConfig) || !Files.isReadable(versionConfig))) {
            throw new RuntimeException("Unable to initialize compactores config: Version config (.minecraft/config/compactores/README.toml) is invalid!");
        }
        if(!generateNewConfig) {
            generateNewConfig = !loadVersionConfig(versionConfig);
            if(generateNewConfig) configVersionConfig.close();
        }
        if(generateNewConfig) {
            LOGGER.info("No valid configuration was found - generating new default configuration...");
            cleanOldConfigs(definitionConfigDir);
            cleanOldConfigs(customizationConfigDir);
            // note that the version config is written AFTER all other configs.
            // this means that if writing a config file fails, we won't have half of a
            // functional config that we'll load later.
            // this way we have either all or nothing.
            exportDefaultConfig(ImmutableMap.of("definitions", definitionConfigDir, "customizations", customizationConfigDir));
            writeVersionConfig(versionConfig);
            LOGGER.info("Configuration generated!");
        }

        // load configs
        LOGGER.info("Loading configuration files...");
        loadConfigFiles(definitionConfigDir, definitionConfigs);
        LOGGER.info("Loaded " + definitionConfigs.size() + " ore definition files");
        loadConfigFiles(customizationConfigDir, customizationConfigs);
        LOGGER.info("Loaded " + customizationConfigs.size() + " ore customization files");

        LOGGER.info("Configuration files loaded successfully!");
    }

    private void requireDirectory(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectory(dir);
            } catch (IOException e) {
                throw new RuntimeException("Unable to initialize compactores configs: Unable to create directory " + dir + ", reason: " + e.getClass().getName() + ": " + e.getMessage());
            }
        } else {
            if (!Files.isDirectory(dir)) {
                throw new RuntimeException("Unable to initialize compactores configs: Expected directory, but found other object at " + dir);
            }
        }
    }

    private boolean loadVersionConfig(Path loc) {
        // load and validate the config file
        configVersionConfig = CommentedFileConfig.of(loc);
        configVersionConfig.load();
        if(!configVersionConfig.contains("versions") || !(configVersionConfig.get("versions") instanceof Config)) {
            return false;
        }
        Config versions = configVersionConfig.get("versions");
        if(!versions.contains("created") || !(versions.get("created") instanceof String)) {
            return false;
        }
        String created = versions.get("created");
        if(!versions.contains("updated") || !(versions.get("updated") instanceof String)) {
            return false;
        }
        String updated = versions.get("updated");
        // compare the loaded version numbers to the active one
        String active = getOwnVersion();
        if(!active.equals(created) && !active.equals(updated)) {
            LOGGER.warn("WARNING");
            LOGGER.warn("~~~~~~~");
            LOGGER.warn("Your current Compact Ores configuration is based on an outdated version of");
            LOGGER.warn("the Compact Ores default config. Please consider generating a new version");
            LOGGER.warn("of the default config by deleting the .minecraft/config/compactores directory");
            LOGGER.warn("config version: " + created + "      mod version: " + active);
            DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
                CompactOres.setLoadFinishScreen(new ConfigUpdateConfirmScreen(created, active, btn -> {
                    // update confirmed
                    LOGGER.info("Updating configuration...");
                    cleanOldConfigs(definitionConfigDir);
                    cleanOldConfigs(customizationConfigDir);
                    exportDefaultConfig(ImmutableMap.of("definitions", definitionConfigDir, "customizations", customizationConfigDir));
                    // write new version config
                    configVersionConfig.close();
                    writeVersionConfig(versionConfig);
                    configVersionConfig = CommentedFileConfig.of(versionConfig);
                    configVersionConfig.load();
                    // quit the game
                    Minecraft.getInstance().shutdown();
                }, btn -> {
                    // update denied
                    LOGGER.info("Not updating configuration - writing new version to version config...");
                    // update "updated" field in version config (so we don't ask again until next update)
                    versions.set("updated", active);
                    configVersionConfig.save();
                    if(Minecraft.getInstance().currentScreen != null) {
                        ((ConfigUpdateConfirmScreen) Minecraft.getInstance().currentScreen).returnToPreviousScreen();
                    }
                }));
            });

        }
        return true;
    }

    private String getOwnVersion() {
        Optional<? extends ModContainer> mod = ModList.get().getModContainerById(CompactOres.MODID);
        if(!mod.isPresent()) {
            throw new RuntimeException("Mod compactores couldn't find itself in the mod list - this should never happen.");
        }
        return mod.get().getModInfo().getVersion().toString();
    }

    private void cleanOldConfigs(Path directory) {
        try {
            Files.list(directory)
                    .filter(p -> Files.isRegularFile(p))
                    .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".toml"))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch(IOException e) {
                            throw new RuntimeException("Unable to initialize compactores configs: When deleting outdated configs: " + e.getClass().getName() + ": " + e.getMessage());
                        }
                    });
        } catch(IOException e) {
            throw new RuntimeException("Unable to initialize compactores configs: When deleting outdated configs: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private void writeVersionConfig(Path versionConfigPath) {
        configVersionConfig = CommentedFileConfig.of(versionConfigPath);
        CommentedConfig versionCfg = CommentedConfig.inMemory();
        String version = getOwnVersion();
        versionCfg.add("created", version);
        versionCfg.add("updated", version);
        configVersionConfig.add("versions", versionCfg);
        configVersionConfig.setComment("versions", getConfigReadme());
        configVersionConfig.save();
    }

    private String getConfigReadme() {
        try(InputStream is = getClass().getResourceAsStream("/assets/compactores/default_config/config_readme.txt");
            Scanner sc = new Scanner(is)) {
            StringBuilder sb = new StringBuilder();
            while(sc.hasNextLine()) {
                sb.append(sc.nextLine());
                sb.append('\n');
            }
            return sb.toString();
        } catch(IOException e) {
            throw new RuntimeException("Unable to initialize compactores configs: When preparing readme for new configs: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private void exportDefaultConfig(Map<String, Path> exports) {
        // load modid list
        List<String> modids = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream("/assets/compactores/default_config/modid_list.txt");
             Scanner sc = new Scanner(is)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (!line.isEmpty()) {
                    modids.add(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to initialize compactores configs: When preparing to export default configs: " + e.getClass().getName() + ": " + e.getMessage());
        }
        // copy configs
        for (String k : exports.keySet()) {
            Path targetDir = exports.get(k);
            for (String modid : modids) {
                Path targetFile = targetDir.resolve(modid + ".toml");
                URL sourceFile = getClass().getResource("/assets/compactores/default_config/" + k + "/" + modid + ".toml");
                LOGGER.debug("Exporting default config file: " + sourceFile.toExternalForm() + " -> " + targetFile.toAbsolutePath().toString());
                try {
                    Files.copy(sourceFile.openStream(), targetFile);
                } catch (IOException e) {
                    throw new RuntimeException("Unable to initialize compactores config: Failed to copy default configuration files: " + e.getClass().getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private void loadConfigFiles(Path sourceDir, Set<FileConfig> destination) {
        try {
            Files.list(sourceDir)
                    .filter(p -> Files.isRegularFile(p))
                    .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".toml"))
                    .forEach(p -> {
                        FileConfig cfg = FileConfig.of(p);
                        cfg.load();
                        destination.add(cfg);
                    });
        } catch(IOException e) {
            throw new RuntimeException("Unable to load compactores config: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public Set<FileConfig> getDefinitionConfigs() {
        return Collections.unmodifiableSet(definitionConfigs);
    }

    public Set<FileConfig> getCustomizationConfigs() {
        return Collections.unmodifiableSet(customizationConfigs);
    }

    public void handleConfigLoadingFailed(Exception e) {
        String msg = e.getClass().getName() + ": " + e.getMessage();
        LOGGER.error("Config loading failed: " + msg);
        DistExecutor.runForDist(() -> () -> {
            // client
            CompactOres.setLoadFinishScreen(new ConfigLoadingFailedScreen(msg, btn -> {
                Minecraft.getInstance().shutdown();
            }, btn -> {
                LOGGER.info("Resetting configuration...");
                cleanOldConfigs(definitionConfigDir);
                cleanOldConfigs(customizationConfigDir);
                exportDefaultConfig(ImmutableMap.of("definitions", definitionConfigDir, "customizations", customizationConfigDir));
                configVersionConfig.close();
                writeVersionConfig(versionConfig);
                Minecraft.getInstance().shutdown();
            }));
            return null;
        }, () -> () -> {
            // server
            LOGGER.error(e);
            throw new IllegalStateException("Failed to load Compact Ores config. See log for stack trace. Error: " + msg);
        });
    }

}
