package advpro_game.controller;

import advpro_game.model.*;
import advpro_game.view.GameStage;

import java.util.logging.Logger;

public class StageManager {
    private static final Logger LOG = Logger.getLogger(StageManager.class.getName());

    private final GameStage stage;
    private int currentStage = 1;
    private boolean bossSpawned = false;

    public StageManager(GameStage stage) { this.stage = stage; }

    public void start() {
        setStageBackgroundAndRealign(currentStage);
        spawnEnemiesForStage();
    }

    public void update() {
        // Remove dead enemies safely
        for (Enemy e : new java.util.ArrayList<>(stage.getEnemies())) {
            if (e.isDead()) stage.removeEnemy(e);
        }

        boolean noEnemies = stage.getEnemies().isEmpty();

        if (!bossSpawned) {
            if (noEnemies) {
                spawnBossForStage();
                bossSpawned = true;
            }
        } else {
            if (noEnemies) {
                if (currentStage < 3) {
                    currentStage++;
                    bossSpawned = false;
                    setStageBackgroundAndRealign(currentStage);
                    spawnEnemiesForStage();
                } else {
                    LOG.info("All stages cleared!");
                }
            }
        }
    }

    private void spawnEnemiesForStage() {
        switch (currentStage) {
            case 1 -> spawnStage1();
            case 2 -> spawnStage2();
            case 3 -> spawnStage3();
        }
    }

    // ========== STAGE 1 ENEMY POSITIONS ==========
    private void spawnStage1() {
        // Spawn 4 Minions at specific positions
        stage.addEnemy(new Minion(150, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(250, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(350, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(450, GameStage.GROUND - 50));

        // Spawn 1 Elite Minion
        stage.addEnemy(new EliteMinion(550, GameStage.GROUND - 60));
    }

    // ========== STAGE 2 ENEMY POSITIONS ==========
    private void spawnStage2() {
        // Spawn 6 Minions at specific positions
        stage.addEnemy(new Minion(100, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(200, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(300, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(400, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(500, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(600, GameStage.GROUND - 50));

        // Spawn 1 Elite Minion
        stage.addEnemy(new EliteMinion(700, GameStage.GROUND - 60));
    }

    // ========== STAGE 3 ENEMY POSITIONS ==========
    private void spawnStage3() {
        // Spawn 8 Minions at specific positions
        stage.addEnemy(new Minion(120, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(200, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(280, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(360, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(440, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(520, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(600, GameStage.GROUND - 50));
        stage.addEnemy(new Minion(680, GameStage.GROUND - 50));

        // Spawn 1 Elite Minion
        stage.addEnemy(new EliteMinion(750, GameStage.GROUND - 60));
    }

    // ========== BOSS SPAWN POSITIONS ==========
    private void spawnBossForStage() {
        Boss boss;
        switch (currentStage) {
            case 1 -> {
                // Stage 1 Boss: Right side
                boss = new Boss(650, GameStage.GROUND - 72);
                boss.addHp(0);
            }
            case 2 -> {
                // Stage 2 Boss: Center
                boss = new Boss(400, GameStage.GROUND - 72);
                boss.addHp(4);
            }
            case 3 -> {
                // Stage 3 Boss: Left side
                boss = new Boss(200, GameStage.GROUND - 72);
                boss.addHp(8);
            }
            default -> {
                boss = new Boss(600, GameStage.GROUND - 72);
            }
        }

        stage.addEnemy(boss);
        LOG.info("Stage " + currentStage + " Boss spawned at (" + boss.getX() + ", " + boss.getY() + ") with HP: " + boss.getHp());
    }

    // helper: use existing API on GameStage
    private void setStageBackgroundAndRealign(int stageIndex) {
        stage.setStageBackground(stageIndex);
        stage.setStage(stageIndex);  // ADD THIS LINE - loads stage-specific platforms

        GameCharacter p = stage.getPlayer();
        p.setX(30);
        p.setY(GameStage.GROUND - p.getCharacterHeight());
        p.setFalling(false);
        p.setCanJump(true);
        stage.updateLivesHUD(p.getLives());
    }
}