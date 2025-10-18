package advpro_game.view;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import advpro_game.Launcher;
import advpro_game.model.GameCharacter;
import advpro_game.model.Keys;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class GameStage extends Pane {
    public static final int WIDTH = 800;
    public static final int HEIGHT = 400;
    public final static int GROUND = 333;
    private Image gameStageImg;
    private List<GameCharacter> gameCharacterList;
    private int life;

    public List<Score> getScoreList() {
        return scoreList;
    }

    public void setScoreList(List<Score> scoreList) {
        this.scoreList = scoreList;
    }

    private List<Score> scoreList;
    private  Keys keys;

    public GameStage() {
        gameCharacterList = new ArrayList<>();
        scoreList = new ArrayList();
        keys = new Keys();
        InputStream stream = Launcher.class.getResourceAsStream("/advpro_game/assets/Stage1.png");
        if (stream == null) {
            System.err.println("Stage1.png not found! Check resource path.");
        } else {
            gameStageImg = new Image(stream);
        }
        ImageView backgroundImg = new ImageView(gameStageImg);
        backgroundImg.setFitHeight(HEIGHT);
        backgroundImg.setFitWidth(WIDTH);
        backgroundImg.setMouseTransparent(true);
        gameCharacterList.add(new GameCharacter(0,30, 30, "/advpro_game/assets/Character.png", 32, 16 ,2, 65,65, KeyCode.A, KeyCode.D, KeyCode.W, KeyCode.S));
        gameCharacterList.add(new GameCharacter(1,400, 30, "/advpro_game/assets/Character.png", 32, 16 ,2, 65,65, KeyCode.LEFT, KeyCode.RIGHT, KeyCode.UP, KeyCode.DOWN));
        scoreList.add(new Score(30,-15));
        getChildren().add(backgroundImg);
        getChildren().addAll(gameCharacterList);
        getChildren().addAll(scoreList);
    }
    public List<GameCharacter> getGameCharacterList() {

        return gameCharacterList;
    }
    public Keys getKeys() {

        return keys;
    }
}