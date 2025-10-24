package advpro_game.controller;

import advpro_game.model.*;
import advpro_game.view.GameStage;

import java.util.logging.Logger;

public class StageManager {
    private static final Logger LOG = Logger.getLogger(StageManager.class.getName());

    private final GameStage stage;
    private int currentStage = 1;
    private boolean bossSpawned = false;

    // {minionCount, bossHpBonus, stageIndex}
    private final int[][] stages = {
            {4, 0, 1},
            {6, 4, 2},
            {8, 8, 3}
    };

    public StageManager(GameStage stage) { this.stage = stage; }

    public void start() {
        setStageBackgroundAndRealign(stages[currentStage - 1][2]);
        spawnMinionsForStage();
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
                if (currentStage < stages.length) {
                    currentStage++;
                    bossSpawned = false;
                    setStageBackgroundAndRealign(stages[currentStage - 1][2]);
                    spawnMinionsForStage();
                } else {
                    LOG.info("All stages cleared! 🎉");
                }
            }
        }
    }

    private void spawnMinionsForStage() {
        int minionCount = stages[currentStage - 1][0];
        double y = GameStage.GROUND - 36;
        for (int i = 0; i < minionCount; i++) {
            double x = 100 + i * 60;
            stage.addEnemy(new Minion(x, y));
        }
    }

    private void spawnBossForStage() {
        int hpBonus = stages[currentStage - 1][1];
        Boss b = new Boss(600, GameStage.GROUND - 72);
        b.addHp(hpBonus);
        stage.addEnemy(b);
    }

    // helper: use existing API on GameStage
    private void setStageBackgroundAndRealign(int index) {
        stage.setStageBackground(index);
        GameCharacter p = stage.getPlayer();
        p.setX(30);
        p.setY(GameStage.GROUND - p.getCharacterHeight());
        p.setFalling(false);
        p.setCanJump(true);
        stage.updateLivesHUD(p.getLives());
    }
}
