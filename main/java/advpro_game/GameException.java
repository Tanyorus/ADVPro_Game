package advpro_game;

public class GameException extends RuntimeException {
    public GameException(String msg) { super(msg); }
    public GameException(String msg, Throwable cause) { super(msg, cause); }
}
