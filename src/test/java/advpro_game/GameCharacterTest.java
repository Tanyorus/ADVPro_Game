package advpro_game;

import advpro_game.model.GameCharacter;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class GameCharacterTest {

    private static final AtomicBoolean FX_INITIALISED = new AtomicBoolean(false);

    @BeforeAll
    static void setupFx() throws Exception {
        if (FX_INITIALISED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX platform failed to start");
        }
    }

    @Test
    void movementProducesDebugLog() throws Exception {
        GameCharacter character = createCharacter();
        character.setFalling(false);

        try (LoggerSession session = new LoggerSession(Level.TRACE)) {
            character.moveRight();
            character.beginFrame();
            character.repaint();
            waitForFxEvents();

            assertTrue(character.getX() > 30, "Character should have moved horizontally");
            assertTrue(session.appender().contains(Level.DEBUG, "Movement[horizontal-right]"),
                    "Movement log at DEBUG level should be present");
        }
    }

    @Test
    void jumpActionLogsAtInfoLevel() throws Exception {
        GameCharacter character = createCharacter();
        character.setFalling(false);
        character.setCanJump(true);

        try (LoggerSession session = new LoggerSession(Level.TRACE)) {
            character.jump();
            waitForFxEvents();

            assertEquals(character.getyMaxVelocity(), character.getyVelocity(),
                    "Jump should set upward velocity");
            assertTrue(session.appender().contains(Level.INFO, "Action[jump]"),
                    "Jump action should log at INFO level");
        }
    }

    @Test
    void scoringLogsAtTraceLevel() {
        GameCharacter character = createCharacter();

        try (LoggerSession session = new LoggerSession(Level.TRACE)) {
            character.addScore(15);

            assertEquals(15, character.getScore(), "Score should increase by delta");
            assertTrue(session.appender().contains(Level.TRACE, "Score change +15 -> 15"),
                    "Score change should be logged at TRACE level");
        }
    }

    private static void waitForFxEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for JavaFX tasks");
    }

    private static GameCharacter createCharacter() {
        GameCharacter character = new GameCharacter(
                0,
                30,
                30,
                "/advpro_game/assets/Character.png",
                32,
                16,
                2,
                65,
                65,
                KeyCode.A,
                KeyCode.D,
                KeyCode.W,
                KeyCode.S,
                MouseButton.PRIMARY
        );
        character.setBulletSink(b -> {
        });
        return character;
    }

    private static final class LoggerSession implements AutoCloseable {
        private final org.apache.logging.log4j.core.Logger logger;
        private final TestAppender appender;
        private final Level originalLevel;

        LoggerSession(Level level) {
            Logger apiLogger = LogManager.getLogger(GameCharacter.class);
            this.logger = (org.apache.logging.log4j.core.Logger) apiLogger;
            this.originalLevel = this.logger.getLevel();
            this.appender = new TestAppender();
            this.appender.start();
            this.logger.addAppender(this.appender);
            this.logger.setLevel(level);
        }

        TestAppender appender() {
            return appender;
        }

        @Override
        public void close() {
            logger.removeAppender(appender);
            if (originalLevel != null) {
                logger.setLevel(originalLevel);
            } else {
                logger.setLevel(null);
            }
            appender.stop();
        }
    }

    private static final class TestAppender extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        TestAppender() {
            super("TestAppender", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            if (event != null) {
                events.add(event.toImmutable());
            }
        }

        boolean contains(Level level, String fragment) {
            return events.stream()
                    .filter(e -> e.getLevel().equals(level))
                    .map(e -> e.getMessage().getFormattedMessage())
                    .anyMatch(msg -> msg != null && msg.contains(fragment));
        }
    }
}