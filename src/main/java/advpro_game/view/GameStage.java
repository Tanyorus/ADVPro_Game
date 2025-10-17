package advpro_game.view;

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
    public final static int GROUND = 300;
    private Image gameStageImg;
    private List<GameCharacter> gameCharacterList;

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
        scoreList = new ArrayList<>();
        keys = new Keys();
        try {

            InputStream stream = new FileInputStream("src/main/resources/advpro_game/assets/Stage1.png");
            gameStageImg = new Image(stream);
        } catch (FileNotFoundException e) {
            System.err.println("File not found! Check absolute path: " + e.getMessage());

        }
        ImageView backgroundImg = new ImageView(gameStageImg);
        backgroundImg.setFitHeight(HEIGHT);
        backgroundImg.setFitWidth(WIDTH);
        gameCharacterList.add(new GameCharacter(0,30, 30, "/advpro_game/assets/Normalwalk.png", 12, 12 ,1, 64,64, KeyCode.A, KeyCode.D, KeyCode.W));
        gameCharacterList.add(new GameCharacter(0,30, 30, "/advpro_game/assets/boss1.png", 2, 2 ,1, 64,128, KeyCode.LEFT, KeyCode.RIGHT, KeyCode.UP));
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