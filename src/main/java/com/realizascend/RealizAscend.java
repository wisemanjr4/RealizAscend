package com.realizascend;

import com.realizascend.core.ConfigManager;
import com.realizascend.core.ModuleManager;
import com.realizascend.data.DataManager;
import com.realizascend.modules.hud.HudManager;
import com.realizascend.modules.season.SeasonManager;
import com.realizascend.modules.temperature.TemperatureManager;
import com.realizascend.modules.nutrition.NutritionManager;
import com.realizascend.modules.bleeding.BleedingManager;
import com.realizascend.modules.stress.StressManager;
import com.realizascend.modules.stamina.StaminaManager;
import com.realizascend.modules.weight.WeightManager;
import com.realizascend.modules.skill.SkillManager;
import com.realizascend.modules.cooking.CookingManager;
import com.realizascend.modules.grave.GraveManager;
import com.realizascend.modules.codex.CodexManager;
import com.realizascend.modules.world.WorldManager;
import com.realizascend.modules.death.DeathManager;
import com.realizascend.modules.brewery.BreweryManager;
import com.realizascend.modules.medical.MedicalManager;
import com.realizascend.modules.recovery.RecoveryManager;
import com.realizascend.modules.tool.ToolManager;
import com.realizascend.modules.food.FoodManager;
import com.realizascend.modules.trap.TrapManager;
import com.realizascend.modules.farm.FarmingManager;
import com.realizascend.modules.cookingstation.CookingStationManager;
import com.realizascend.modules.stealth.StealthManager;
import com.realizascend.modules.status.StatusManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class RealizAscend extends JavaPlugin {

    private static RealizAscend instance;

    private ConfigManager configManager;
    private DataManager dataManager;
    private ModuleManager moduleManager;

    private SeasonManager seasonManager;
    private TemperatureManager temperatureManager;
    private NutritionManager nutritionManager;
    private BleedingManager bleedingManager;
    private StressManager stressManager;
    private StaminaManager staminaManager;
    private WeightManager weightManager;
    private SkillManager skillManager;
    private CookingManager cookingManager;
    private GraveManager graveManager;
    private CodexManager codexManager;
    private WorldManager worldManager;
    private DeathManager deathManager;
    private HudManager hudManager;
    private BreweryManager breweryManager;
    private MedicalManager medicalManager;
    private RecoveryManager recoveryManager;
    private ToolManager toolManager;
    private FoodManager foodManager;
    private TrapManager trapManager;
    private StatusManager statusManager;
    private FarmingManager farmingManager;
    private CookingStationManager cookingStationManager;
    private StealthManager stealthManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        dataManager = new DataManager(this);
        moduleManager = new ModuleManager();

        seasonManager = new SeasonManager(this);
        temperatureManager = new TemperatureManager(this);
        nutritionManager = new NutritionManager(this);
        bleedingManager = new BleedingManager(this);
        stressManager = new StressManager(this);
        staminaManager = new StaminaManager(this);
        weightManager = new WeightManager(this);
        skillManager = new SkillManager(this);
        cookingManager = new CookingManager(this);
        graveManager = new GraveManager(this);
        codexManager = new CodexManager(this);
        worldManager = new WorldManager(this);
        deathManager = new DeathManager(this);
        hudManager = new HudManager(this);
        breweryManager = new BreweryManager(this);
        medicalManager = new MedicalManager(this);
        recoveryManager = new RecoveryManager(this);
        toolManager = new ToolManager(this);
        foodManager = new FoodManager(this);
        trapManager = new TrapManager(this);
        statusManager = new StatusManager(this);
        farmingManager = new FarmingManager(this);
        cookingStationManager = new CookingStationManager(this);
        stealthManager = new StealthManager(this);

        moduleManager.registerAll(
            seasonManager,
            temperatureManager,
            nutritionManager,
            bleedingManager,
            stressManager,
            staminaManager,
            weightManager,
            skillManager,
            cookingManager,
            graveManager,
            codexManager,
            worldManager,
            deathManager,
            hudManager,
            breweryManager,
            medicalManager,
            recoveryManager,
            toolManager,
            foodManager,
            trapManager,
            farmingManager,
            cookingStationManager,
            stealthManager,
            statusManager
        );

        moduleManager.enableAll();
        dataManager.startAutosave();
        dataManager.loadAll();

        if (getCommand("realiz") != null) {
            getCommand("realiz").setExecutor(new RealizCommand(this));
        }

        getLogger().info("RealizAscend enabled successfully!");
    }

    @Override
    public void onDisable() {
        dataManager.saveAll();
        dataManager.stopAutosave();
        moduleManager.disableAll();
        getLogger().info("RealizAscend disabled.");
    }

    public void reload() {
        reloadConfig();
        configManager.loadConfig();
        moduleManager.reloadAll();
        getLogger().info("RealizAscend reloaded.");
    }

    public static RealizAscend getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public DataManager getDataManager() { return dataManager; }
    public SeasonManager getSeasonManager() { return seasonManager; }
    public TemperatureManager getTemperatureManager() { return temperatureManager; }
    public NutritionManager getNutritionManager() { return nutritionManager; }
    public BleedingManager getBleedingManager() { return bleedingManager; }
    public StressManager getStressManager() { return stressManager; }
    public StaminaManager getStaminaManager() { return staminaManager; }
    public WeightManager getWeightManager() { return weightManager; }
    public SkillManager getSkillManager() { return skillManager; }
    public CookingManager getCookingManager() { return cookingManager; }
    public GraveManager getGraveManager() { return graveManager; }
    public CodexManager getCodexManager() { return codexManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public DeathManager getDeathManager() { return deathManager; }
    public HudManager getHudManager() { return hudManager; }
    public BreweryManager getBreweryManager() { return breweryManager; }
    public MedicalManager getMedicalManager() { return medicalManager; }
    public RecoveryManager getRecoveryManager() { return recoveryManager; }
    public ToolManager getToolManager() { return toolManager; }
    public FoodManager getFoodManager() { return foodManager; }
    public TrapManager getTrapManager() { return trapManager; }
    public FarmingManager getFarmingManager() { return farmingManager; }
    public CookingStationManager getCookingStationManager() { return cookingStationManager; }
    public StealthManager getStealthManager() { return stealthManager; }
    public StatusManager getStatusManager() { return statusManager; }
}
