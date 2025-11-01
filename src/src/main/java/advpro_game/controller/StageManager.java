package advpro_game.controller;

import advpro_game.model.*;
import advpro_game.view.GameStage;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.logging.Logger;

public class StageManager {
    private static final Logger LOG = Logger.getLogger(StageManager.class.getName());

    private final GameStage stage;
    private int currentStage = 1;

    // Per-stage state flags (one-shots)
    private boolean sawAnyMinionThisStage = false; // becomes true once minions actually present at least once
    private boolean preparingBoss = false;         // boss spawn scheduled (delayed)
    private boolean bossSpawned = false;           // boss node created and added
    private boolean stageTransitioning = false;    // prevents duplicate stage-clear handling

    // Small defers so we don't fight with GameStage.setStage()
    private static final int SPAWN_AFTER_STAGE_ARM_MS = 120;  // wait after setStage arm
    private static final int BOSS_DELAY_MS            = 1000; // dramatic pause

    public StageManager(GameStage stage) { this.stage = stage; }

    // ------------ Lifecycle ------------
    public void start() {
        Platform.runLater(() -> {
            // If GameStage already set a stage, honor it; otherwise set to currentStage
            if (stage.getCurrentStage() != currentStage) {
                currentStage = Math.max(1, stage.getCurrentStage());
            } else {
                stage.setStage(currentStage); // build platforms + background + BGM
            }

            // Spawn minions only AFTER world is ready (avoid "empty -> boss" race)
            PauseTransition pt = new PauseTransition(Duration.millis(SPAWN_AFTER_STAGE_ARM_MS));
            pt.setOnFinished(ev -> {
                if (!stage.isWorldReady()) {
                    // try one more frame later
                    PauseTransition retry = new PauseTransition(Duration.millis(30));
//                    retry.setOnFinished(_ev -> spawnEnemiesForStageSafe());
                    retry.play();
                } else {
//                    spawnEnemiesForStageSafe();
                }
            });
            pt.play();
            LOG.info("Stage " + currentStage + " started.");
        });
    }

    /** Call every tick from GameLoop. */
    public void update() {
        // While world rebuilding, do nothing this frame
        if (!stage.isWorldReady()) return;

        // 0) Purge dead enemies safely (avoid CME)
        for (Enemy e : new ArrayList<>(stage.getEnemies())) {
            if (e.isDead()) stage.removeEnemy(e);
        }

        boolean anyBossAlive   = stage.getEnemies().stream().anyMatch(e -> e instanceof Boss);
        boolean anyMinionAlive = stage.getEnemies().stream().anyMatch(this::isMinion);

        // 1) Flip when we actually see any minion at least once this stage
        if (!sawAnyMinionThisStage && anyMinionAlive) {
            sawAnyMinionThisStage = true;
        }

        // 2) ONE-TIME boss spawn after all minions are cleared (and we had minions)
        if (sawAnyMinionThisStage && !anyMinionAlive && !anyBossAlive && !preparingBoss && !bossSpawned) {
            preparingBoss = true;
            LOG.info("All minions cleared → preparing boss...");
            spawnBossDelayed(); // schedules exactly one boss
        }

        // 3) Stage clear only after boss had spawned and now no enemies remain
        if (bossSpawned && !anyBossAlive && !anyMinionAlive && !stageTransitioning) {
            handleStageClearOnce();
        }
    }

    // ------------ Stage flow helpers ------------

    private void handleStageClearOnce() {
        stageTransitioning = true; // guard to prevent spam
        LOG.info("Stage " + currentStage + " cleared!");

        if (currentStage < 3) {
            int nextStage = currentStage + 1;
            PauseTransition delay = new PauseTransition(Duration.seconds(0.6));
            delay.setOnFinished(ev -> {
                currentStage = nextStage;

                // Reset per-stage flags BEFORE building the next stage
                resetPerStageFlags();

                Platform.runLater(() -> {
                    stage.setStage(currentStage);
                    // Spawn minions after the arm window again
                    PauseTransition arm = new PauseTransition(Duration.millis(SPAWN_AFTER_STAGE_ARM_MS));
//                    arm.setOnFinished(_ev -> spawnEnemiesForStageSafe());
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
            if (bossSpawned || !stage.isWorldReady()) { preparingBoss = false; return; }
            spawnBossForStage();
            bossSpawned = true;
            preparingBoss = false;
            LOG.info("Boss spawned for stage " + currentStage);
        }));
        delay.play();
    }

    // ------------ Enemy sets per stage ------------

//    private void spawnEnemiesForStageSafe() {
//        if (!stage.isWorldReady()) return;
//        spawnEnemiesForStage();
//    }

//    private void spawnEnemiesForStage() {
//        LOG.info("Spawning enemies for stage " + currentStage);
//        switch (currentStage) {
//            case 1 -> spawnStage1();
//            case 2 -> spawnStage2();
//            case 3 -> spawnStage3();
//            default -> spawnStage1();
//        }
//        // NOTE: we intentionally do NOT set sawAnyMinionThisStage here.
//        // update() flips it once enemies actually appear.
//    }

    // Stage 1: 4 minions + 1 elite (positions from your v1)
//    private void spawnStage1() {
////        stage.addEnemy(new Minion(150, GameStage.GROUND - 50));
////        stage.addEnemy(new Minion(250, GameStage.GROUND - 50));
////        stage.addEnemy(new Minion(350, GameStage.GROUND - 50));
////        stage.addEnemy(new Minion(450, GameStage.GROUND - 50));
////        stage.addEnemy(new EliteMinion(550, GameStage.GROUND - 60));
//    }
//
//    // Stage 2
//    private void spawnStage2() {
////        stage.addEnemy(new Minion(100, GameStage.GROUND - 50));
////        stage.addEnemy(new Minion(200, GameStage.GROUND - 50));
////        stage.addEnemy(new Minion(300, GameStage.GROUND - 50));
////        stage.addEnemy(new Minion(400, GameStage.GROUND - 50));
////        stage.addEnemy(new Minion(500, GameStage.GROUND - 50));
////        stage.addEnemy(new Minion(600, GameStage.GROUND - 50));
////        stage.addEnemy(new EliteMinion(700, GameStage.GROUND - 60));
//    }
//
//    // Stage 3
//    private void spawnStage3() {
//        stage.addEnemy(new Minion(120, GameStage.GROUND - 50));
//        stage.addEnemy(new Minion(200, GameStage.GROUND - 50));
//        stage.addEnemy(new Minion(280, GameStage.GROUND - 50));
//        stage.addEnemy(new Minion(360, GameStage.GROUND - 50));
//        stage.addEnemy(new Minion(440, GameStage.GROUND - 50));
//        stage.addEnemy(new Minion(520, GameStage.GROUND - 50));
//        stage.addEnemy(new Minion(600, GameStage.GROUND - 50));
//        stage.addEnemy(new Minion(680, GameStage.GROUND - 50));
//        stage.addEnemy(new EliteMinion(750, GameStage.GROUND - 60));
//    }

    // ------------ Boss spawns (constructor-safe) ------------

    private void spawnBossForStage() {
        // Be tolerant to different Boss constructors in your codebase
        Boss boss = null;
        try {
            switch (currentStage) {
                case 1 -> {
                    boss = new Boss(580, 208,86, 20,
                        "/advpro_game/assets/boss1_1.png",
                        2, 2, 1, 43, 10,
                        1);
                    boss.setCustomAnimatedBullet(
                            "/advpro_game/assets/boss1B.png",  // sprite sheet path
                            3.0,                                         // scale (makes it 32x32)
                            6,                                           // frame count
                            6,                                           // columns in sprite sheet
                            1,                                           // rows in sprite sheet
                            14,                                          // frame width (pixels)
                            10                                           // frame height (pixels)
                    );

                }
                case 2 -> {
                    // Try rich ctor first; fall back to simple if not available
                    try {
                        boss = new Boss(
                                550, GameStage.GROUND - 380,
                                170, 170,
                                "/advpro_game/assets/bossjava.png",
                                2, 2, 0, 112, 112,
                                1
                        );
                        boss.setCustomAnimatedBullet(
                                "/advpro_game/assets/java_bullet.png",  // sprite sheet path
                                2.0,                                         // scale (makes it 32x32)
                                4,                                           // frame count
                                4,                                           // columns in sprite sheet
                                1,                                           // rows in sprite sheet
                                25,                                          // frame width (pixels)
                                27                                           // frame height (pixels)
                        );
                        try { boss.addHp(4); } catch (Throwable ignored) {}
                        // Optional elites to spice up the fight (ignore if missing that ctor)
                        try {
                            stage.addEnemy(new EliteMinion(550, GameStage.GROUND - 80, 80, 80,
                                    "/advpro_game/assets/elite_minion_2.png",
                                    3, 2, 2, 32, 32, 300));
                        } catch (Throwable ignored) {

                        }
                        try { stage.addEnemy(new EliteMinion(600, GameStage.GROUND - 80, 80, 80,
                                "/advpro_game/assets/elite_minion_2.png",
                                3, 2, 2, 32, 32,200));
                        } catch (Throwable ignored) {

                        }
                        try { stage.addEnemy(new EliteMinion(650, GameStage.GROUND - 80,80, 80,
                                "/advpro_game/assets/elite_minion_2.png",
                                3, 2, 2, 32, 32, 100));
                        } catch (Throwable ignored) {

                        }
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
                            "/advpro_game/assets/boss_3B.png",  // sprite sheet path
                            3.0,                                         // scale (makes it 32x32)
                            3,                                           // frame count
                            3,                                           // columns in sprite sheet
                            1,                                           // rows in sprite sheet
                            32,                                          // frame width (pixels)
                            32                                           // frame height (pixels)
                    );

                    stage.addEnemy(new Minion(50,  50,60, 60,
                            "/advpro_game/assets/minion_3-2.png",
                            3, 3, 1, 24, 16));

                    stage.addEnemy(new Minion(200,  80,60, 60,
                            "/advpro_game/assets/minion_3-2.png",
                            3, 3, 1, 24, 16));

                    stage.addEnemy(new Minion(350,  50,60, 60,
                            "/advpro_game/assets/minion_3-2.png",
                            3, 3, 1, 24, 16));

                    stage.addEnemy(new EliteMinion(650, GameStage.GROUND - 120, 80, 160,
                            "/advpro_game/assets/elite_3.png",
                            10, 5, 1, 50, 66, 200));

                    try { boss.addHp(8);
                    } catch (Throwable ignored) {

                    }
                }
                default -> boss = new Boss(580, GameStage.GROUND - 72);
            }
        } catch (Throwable t) {
            // Absolute fallback
            boss = new Boss(580, GameStage.GROUND - 72);
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
//            arm.setOnFinished(_ev -> spawnEnemiesForStageSafe());
            arm.play();
        });
    }

    /** Used by GameStage retry flow to (re)spawn enemies for the current stage. */
    public void spawnEnemiesForStage(int stageIndex) {
        this.currentStage = Math.max(1, stageIndex);
        resetPerStageFlags(); // fresh wave behavior
        PauseTransition arm = new PauseTransition(Duration.millis(SPAWN_AFTER_STAGE_ARM_MS));
//        arm.setOnFinished(_ev -> spawnEnemiesForStageSafe());
        arm.play();
        LOG.info("Enemies (re)spawned for stage " + currentStage);
    }

    // ------------ Helpers ------------

    private boolean isMinion(Enemy e) {
        if (e == null) return false;
        if (e instanceof Boss) return false;
        // Treat anything not Boss as a "minion"-type for gating,
        // but you can tighten it to (e instanceof Minion || e instanceof EliteMinion) if you prefer.
        return true;
    }
}
