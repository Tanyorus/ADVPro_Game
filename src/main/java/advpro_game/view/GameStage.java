package advpro_game.view;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import advpro_game.Launcher;
import advpro_game.model.GameCharacter;
import advpro_game.model.Keys;

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
    private Keys keys;

    public GameStage() {
        gameCharacterList = new ArrayList<>();
        scoreList = new ArrayList();
        keys = new Keys();
        gameStageImg = new Image(Launcher.class.getResourceAsStream("assets/Stage_1.png"));
        ImageView backgroundImg = new ImageView(gameStageImg);
        backgroundImg.setFitHeight(HEIGHT);
        backgroundImg.setFitWidth(WIDTH);
        gameCharacterList.add(new GameCharacter(0,30, 30,"assets/Normalwalk.png", 12, 1 ,1, 64,64, KeyCode.A, KeyCode.D, KeyCode.W));
//        gameCharacterList.add(new GameCharacter(1, GameStage.WIDTH-60, 30, "assets/Normalwalk.png", 4, 4 ,1, 129,66, KeyCode.LEFT, KeyCode.RIGHT, KeyCode.UP));
        scoreList.add(new Score(30,GROUND + 30));
        scoreList.add(new Score(GameStage.WIDTH-60, GROUND + 30));
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