package advpro_game.controller;

import advpro_game.model.*;
import advpro_game.view.GameStage;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StageManager {
    private static final Logger LOG = LogManager.getLogger(StageManager.class);

    private final GameStage stage;
    private int currentStage = 1;

    // Per-stage one-shots
    private boolean sawAnyMinionThisStage = false; // flipped when minions actually present
    private boolean preparingBoss = false;         // boss spawn scheduled
    private boolean bossSpawned = false;           // boss node created
    private boolean stageTransitioning = false;    // prevents duplicate clear

    // Timings
    private static final int SPAWN_AFTER_STAGE_ARM_MS = 120;  // wait a tick after GameStage.setStage(...)
    private static final int BOSS_DELAY_MS            = 1000; // dramatic pause before boss

    public StageManager(GameStage stage) {
        this.stage = stage;
        // Let GameStage know we exist (its setStageManager handles this safely)
        this.stage.setStageManager(this);
    }

    // ------------ Lifecycle ------------

    public void start() {
        Platform.runLater(() -> {
            // Build (or rebuild) the current stage
            stage.setStage(currentStage);

            // Spawn the first wave shortly after the stage is armed
            PauseTransition pt = new PauseTransition(Duration.millis(SPAWN_AFTER_STAGE_ARM_MS));
            pt.setOnFinished(_ev -> spawnEnemiesForStage(currentStage));
            pt.play();

            LOG.info("Stage " + currentStage + " started.");
        });
    }

    /** Call every tick from your GameLoop. */
    public void update() {
        // 0) Purge dead enemies safely
        for (Enemy e : new ArrayList<>(stage.getEnemies())) {
            if (e.isDead()) stage.removeEnemy(e);
        }

        // 1) Stage logic (minion -> boss -> clear)
        boolean anyBossAlive   = stage.getEnemies().stream().anyMatch(e -> e instanceof Boss);
        boolean anyMinionAlive = stage.getEnemies().stream().anyMatch(this::isMinion);

        if (!sawAnyMinionThisStage && anyMinionAlive) {
            sawAnyMinionThisStage = true;
        }

        // When all minions gone (and we had minions), schedule boss once
        if (sawAnyMinionThisStage && !anyMinionAlive && !anyBossAlive && !preparingBoss && !bossSpawned) {
            preparingBoss = true;
            LOG.info("All minions cleared → preparing boss...");
            spawnBossDelayed();
        }

        // Stage clear only after boss spawned and now nothing remains
        if (bossSpawned && !anyBossAlive && !anyMinionAlive && !stageTransitioning) {
            handleStageClearOnce();
        }
    }

    // ------------ Stage flow helpers ------------

    private void handleStageClearOnce() {
        stageTransitioning = true;
        LOG.info("Stage " + currentStage + " cleared!");

        if (currentStage < 3) {
            int nextStage = currentStage + 1;
            PauseTransition delay = new PauseTransition(Duration.seconds(0.6));
            delay.setOnFinished(ev -> {
                currentStage = nextStage;
                resetPerStageFlags();

                Platform.runLater(() -> {
                    stage.setStage(currentStage);
                    PauseTransition arm = new PauseTransition(Duration.millis(SPAWN_AFTER_STAGE_ARM_MS));
                    arm.setOnFinished(_ev -> spawnEnemiesForStage(currentStage));
                    arm.play();
                    LOG.info("Loading stage " + currentStage + "...");
                });
            });
            delay.play();
        } else {
            LOG.info("All stages cleared!");
            Platform.runLater(stage::requestGameClear);
        }
    }

    private void resetPerStageFlags() {
        sawAnyMinionThisStage = false;
        preparingBoss = false;
        bossSpawned = false;
        stageTransitioning = false;
    }

    private void spawnBossDelayed() {
        PauseTransition delay = new PauseTransition(Duration.millis(BOSS_DELAY_MS));
        delay.setOnFinished(e -> Platform.runLater(() -> {
            if (bossSpawned) { preparingBoss = false; return; }
            spawnBossForStage();
            bossSpawned = true;
            preparingBoss = false;
            LOG.info("Boss spawned for stage " + currentStage);
        }));
        delay.play();
    }

    // ------------ Enemy waves per stage ------------

    /** Public API called by GameStage on Retry (already wired in your patched GameStage). */
    public void spawnEnemiesForStage(int stageIndex) {
        this.currentStage = Math.max(1, stageIndex);
        resetPerStageFlags(); // fresh wave behavior

        // Let GameStage provide a safe default wave set (works even if you don’t customize below)
        stage.spawnDefaultMinionsFor(currentStage);

        // Mark as seen if anything actually appeared
        if (!stage.getEnemies().isEmpty()) {
            sawAnyMinionThisStage = true;
        }

        LOG.info("Enemies (re)spawned for stage " + currentStage);
    }

    // ------------ Boss spawns (constructor-safe) ------------

    private void spawnBossForStage() {
        Boss boss;
        try {
            switch (currentStage) {
                case 1 -> {
                    boss = new Boss(580, 208,86, 20,
                            "/advpro_game/assets/boss1_1.png",
                            2, 2, 1, 43, 10,
                            1);
                    boss.setCustomAnimatedBullet(
                            "/advpro_game/assets/boss1B.png",
                            3.0,  // scale
                            6,    // frames
                            6,    // cols
                            1,    // rows
                            14,   // fw
                            10    // fh
                    );
                }
                case 2 -> {
                    // Prefer rich ctor; fallback to simple if unavailable
                    try {
                        boss = new Boss(
                                550, GameStage.GROUND - 380,
                                170, 170,
                                "/advpro_game/assets/bossjava.png",
                                2, 2, 0, 112, 112,
                                1
                        );
                        boss.setCustomAnimatedBullet(
                                "/advpro_game/assets/java_bullet.png",
                                2.0, 4, 4, 1, 25, 27
                        );
                        try { boss.addHp(4); } catch (Throwable ignored) {}

                        // Optional elites to add pressure
                        try {
                            stage.addEnemy(new EliteMinion(550, GameStage.GROUND - 80, 80, 80,
                                    "/advpro_game/assets/elite_minion_2.png",
                                    3, 2, 2, 32, 32, 300));
                            stage.addEnemy(new EliteMinion(600, GameStage.GROUND - 80, 80, 80,
                                    "/advpro_game/assets/elite_minion_2.png",
                                    3, 2, 2, 32, 32, 200));
                            stage.addEnemy(new EliteMinion(650, GameStage.GROUND - 80, 80, 80,
                                    "/advpro_game/assets/elite_minion_2.png",
                                    3, 2, 2, 32, 32, 100));
                        } catch (Throwable ignored) {}
                    } catch (Throwable richCtorMissing) {
                        boss = new Boss(600, GameStage.GROUND - 72);
                        try { boss.addHp(4); } catch (Throwable ignored) {}
                    }
                }
                case 3 -> {
                    boss = new Boss(500, 50,200, 200,
                            "/advpro_game/assets/boss_3.png",
                            8, 8, 1, 80, 71,
                            1);
                    boss.setCustomAnimatedBullet(
                            "/advpro_game/assets/boss_3B.png",
                            3.0, 3, 3, 1, 32, 32
                    );

                    // Add some helpers for chaos
                    stage.addEnemy(new Minion(50,  50,60, 60,"/advpro_game/assets/minion_3-2.png",3,3,1,24,16));
                    stage.addEnemy(new Minion(200, 80,60, 60,"/advpro_game/assets/minion_3-2.png",3,3,1,24,16));
                    stage.addEnemy(new Minion(350, 50,60, 60,"/advpro_game/assets/minion_3-2.png",3,3,1,24,16));
                    stage.addEnemy(new EliteMinion(650, GameStage.GROUND - 120, 80, 160,"/advpro_game/assets/elite_3.png",10,5,1,50,66,200));
                    try { boss.addHp(8); } catch (Throwable ignored) {}
                }
                default -> boss = new Boss(580, GameStage.GROUND - 72);
            }
        } catch (Throwable t) {
            boss = new Boss(580, GameStage.GROUND - 72); // absolute fallback
        }

        stage.addEnemy(boss);
        try {
            LOG.info("Stage " + currentStage + " Boss spawned at (" + boss.getX() + ", " + boss.getY() + ") with HP: " + boss.getHp());
        } catch (Throwable ignored) {
            LOG.info("Stage " + currentStage + " Boss spawned.");
        }
    }

    // ------------ External controls / hooks ------------

    /** Hard reset to a specific stage (e.g., from main menu). */
    public void hardResetToStage(int stageIndex) {
        currentStage = Math.max(1, stageIndex);
        resetPerStageFlags();

        Platform.runLater(() -> {
            stage.setStage(currentStage);
            PauseTransition arm = new PauseTransition(Duration.millis(SPAWN_AFTER_STAGE_ARM_MS));
            arm.setOnFinished(_ev -> spawnEnemiesForStage(currentStage));
            arm.play();
        });
    }

    // ------------ Helpers ------------

    private boolean isMinion(Enemy e) {
        if (e == null) return false;
        if (e instanceof Boss) return false;
        // Treat everything not Boss as "minion" for gating
        return true;
    }
}
