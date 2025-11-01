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
    private boolean sawAnyMinionThisStage = false;
    private boolean preparingBoss = false;
    private boolean bossSpawned = false;
    private boolean stageTransitioning = false;

    private static final int SPAWN_AFTER_STAGE_ARM_MS = 120;
    private static final int BOSS_DELAY_MS            = 1000;

    public StageManager(GameStage stage) { this.stage = stage; }

    // ------------ Lifecycle ------------
    public void start() {
        Platform.runLater(() -> {
            if (stage.getCurrentStage() != currentStage) {
                currentStage = Math.max(1, stage.getCurrentStage());
            } else {
                stage.setStage(currentStage);
            }

            PauseTransition pt = new PauseTransition(Duration.millis(SPAWN_AFTER_STAGE_ARM_MS));
            pt.setOnFinished(ev -> {
                if (!stage.isWorldReady()) {
                    PauseTransition retry = new PauseTransition(Duration.millis(30));
                    retry.setOnFinished(_ev -> spawnEnemiesForStageSafe());
                    retry.play();
                } else {
                    spawnEnemiesForStageSafe();
                }
            });
            pt.play();
            LOG.info("Stage " + currentStage + " started.");
        });
    }

    /** Call every tick from GameLoop. */
    public void update() {
        if (!stage.isWorldReady()) return;

        // purge dead safely
        for (Enemy e : new ArrayList<>(stage.getEnemies())) {
            if (e.isDead()) stage.removeEnemy(e);
        }

        boolean anyBossAlive   = stage.getEnemies().stream().anyMatch(e -> e instanceof Boss);
        boolean anyMinionAlive = stage.getEnemies().stream().anyMatch(this::isMinion);

        if (!sawAnyMinionThisStage && anyMinionAlive) {
            sawAnyMinionThisStage = true;
        }

        if (sawAnyMinionThisStage && !anyMinionAlive && !anyBossAlive && !preparingBoss && !bossSpawned) {
            preparingBoss = true;
            LOG.info("All minions cleared → preparing boss...");
            spawnBossDelayed();
        }

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
                    arm.setOnFinished(_ev -> spawnEnemiesForStageSafe());
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
    private void spawnEnemiesForStageSafe() {
        if (!stage.isWorldReady()) return;
        spawnEnemiesForStage();
    }

    private void spawnEnemiesForStage() {
        LOG.info("Spawning enemies for stage " + currentStage);
        switch (currentStage) {
            case 1 -> spawnStage1();
            case 2 -> spawnStage2();
            case 3 -> spawnStage3();
            default -> spawnStage1();
        }
    }

    private void spawnStage1() {
        stage.addEnemy(new Minion(150, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(250, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(350, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(450, GameStage.GROUND - 50));
        stage.addEnemy(new EliteMinion(550, GameStage.GROUND - 60));
    }

    private void spawnStage2() {
        stage.addEnemy(new Minion(100, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(200, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(300, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(400, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(500, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(600, GameStage.GROUND - 50));
        stage.addEnemy(new EliteMinion(700, GameStage.GROUND - 60));
    }

    private void spawnStage3() {
        stage.addEnemy(new Minion(120, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(200, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(280, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(360, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(440, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(520, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(600, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(680, GameStage.GROUND - 50));
        stage.addEnemy(new EliteMinion(750, GameStage.GROUND - 60));
    }

    // ------------ Boss spawns (constructor-safe) ------------
    private void spawnBossForStage() {
        Boss boss;
        try {
            switch (currentStage) {
                case 1 -> {
                    // Stage 1: plain boss (bossType=1). Ensure NO custom bullet.
                    boss = new Boss(590, GameStage.GROUND - 120);
                    try { boss.setBulletConfig(null); } catch (Throwable ignored) {}
                }
                case 2 -> {
                    // Stage 2: Java boss (bossType=2) with animated java_bullet ONLY here.
                    boss = new Boss(
                            550, GameStage.GROUND - 380,
                            102, 113,
                            "/advpro_game/assets/bossjava.png",
                            3, 3, 1,
                            102, 113,
                            2
                    );
                    try {
                        boss.setCustomAnimatedBullet(
                                "/advpro_game/assets/java_bullet.png",
                                1.6,
                                4, 4, 1,
                                24, 24
                        );
                    } catch (Throwable ignored) {
                        LOG.info("Boss.setCustomAnimatedBullet not available; continuing.");
                    }
                    try { boss.addHp(4); } catch (Throwable ignored) {}

                    // Optional elites around the boss
                    try {
                        stage.addEnemy(new EliteMinion(550, GameStage.GROUND - 80, 80, 80,
                                "/advpro_game/assets/elite_minion_2.png",
                                3, 2, 2, 32, 32, 300));
                    } catch (Throwable ignored) {}
                    try { stage.addEnemy(new EliteMinion(600, GameStage.GROUND - 60, 200)); } catch (Throwable ignored) {}
                    try { stage.addEnemy(new EliteMinion(650, GameStage.GROUND - 60, 100)); } catch (Throwable ignored) {}
                }
                default -> {
                    // Stage 3 (or others): plain boss (bossType defaults). Ensure NO custom bullet.
                    boss = new Boss(200, GameStage.GROUND - 72);
                    try { boss.addHp(8); } catch (Throwable ignored) {}
                    try { boss.setBulletConfig(null); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            boss = new Boss(580, GameStage.GROUND - 72);
            try { boss.setBulletConfig(null); } catch (Throwable ignored) {}
        }

        stage.addEnemy(boss);
        try {
            LOG.info("Stage " + currentStage + " Boss spawned at (" + boss.getX() + ", " + boss.getY() + ") with HP: " + boss.getHp());
        } catch (Throwable ignored) {
            LOG.info("Stage " + currentStage + " Boss spawned.");
        }
    }

    // ------------ External controls / hooks ------------
    public void hardResetToStage(int stageIndex) {
        currentStage = Math.max(1, stageIndex);
        resetPerStageFlags();

        Platform.runLater(() -> {
            stage.setStage(currentStage);
            PauseTransition arm = new PauseTransition(Duration.millis(SPAWN_AFTER_STAGE_ARM_MS));
            arm.setOnFinished(_ev -> spawnEnemiesForStageSafe());
            arm.play();
        });
    }

    public void spawnEnemiesForStage(int stageIndex) {
        this.currentStage = Math.max(1, stageIndex);
        resetPerStageFlags();
        PauseTransition arm = new PauseTransition(Duration.millis(SPAWN_AFTER_STAGE_ARM_MS));
        arm.setOnFinished(_ev -> spawnEnemiesForStageSafe());
        arm.play();
        LOG.info("Enemies (re)spawned for stage " + currentStage);
    }

    // ------------ Helpers ------------
    private boolean isMinion(Enemy e) {
        if (e == null) return false;
        if (e instanceof Boss) return false;
        return true;
    }
}
