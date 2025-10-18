package advpro_game.controller;

import advpro_game.model.GameCharacter;
import advpro_game.view.GameStage;

import java.util.List;

public class DrawingLoop implements Runnable {
    private GameStage gameStage;
    private int frameRate;
    private float interval;
    private boolean running;
    private GameCharacter Character;
    public DrawingLoop(GameStage gameStage) {
        this.gameStage = gameStage;
        frameRate = 60;
        interval = 1000.0f / frameRate; // 1000 ms = 1 second
        running = true;
    }
    private void checkDrawCollisions(List<GameCharacter> gameCharacterList) {
        for (GameCharacter gameCharacter : gameCharacterList) {
            gameCharacter.checkReachGameWall();
            gameCharacter.checkReachHighest();
            gameCharacter.checkReachFloor();
        }
        GameCharacter cA = gameCharacterList.get(0);
        GameCharacter cB = gameCharacterList.get(1);
        if(cA.getBoundsInParent().intersects(cB.getBoundsInParent())){
            if(cA.collided(cB) == false){
                cB.collided(cA);
            }
        }
    }
    private void paint(List<GameCharacter> gameCharacterList,double dt) {
        for (GameCharacter gameCharacter : gameCharacterList) {
            gameCharacter.repaint(dt);
        }
    }
    @Override
    public void run() {
        long last = System.nanoTime();

        while (running) {
            long now = System.nanoTime();
            double dt = (now - last)/1000000.0; //ms
            last = now;

            checkDrawCollisions(gameStage.getGameCharacterList());
            paint(gameStage.getGameCharacterList(),dt);

            long frameTime = (System.nanoTime() - now)/1000000L;
            long sleepMs = (long)interval -frameTime;
            if (sleepMs < 1) sleepMs = 1;
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ignored) {}
        }
    }
}
