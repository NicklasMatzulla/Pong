package net.limitmedia.pong.client.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.concurrent.atomic.AtomicBoolean;
import net.limitmedia.pong.core.audio.AudioMixer;
import net.limitmedia.pong.core.localization.LocalizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JavaFxHud {
    private static final Logger LOG = LoggerFactory.getLogger(JavaFxHud.class);
    private static final AtomicBoolean FX_INITIALISED = new AtomicBoolean();

    private final LocalizationService localization;
    private final AudioMixer mixer;

    private Stage stage;
    private Label scoreLabel;
    private Label pingLabel;
    private Label timerLabel;

    public JavaFxHud(LocalizationService localization, AudioMixer mixer) {
        this.localization = localization;
        this.mixer = mixer;
        ensureToolkit();
    }

    private void ensureToolkit() {
        if (FX_INITIALISED.compareAndSet(false, true)) {
            try {
                Platform.startup(() -> LOG.info("JavaFX runtime started"));
            } catch (IllegalStateException ex) {
                LOG.warn("JavaFX runtime already started: {}", ex.getMessage());
            }
        }
    }

    public void show() {
        Platform.runLater(() -> {
            if (stage == null) {
                buildStage();
            }
            stage.show();
        });
    }

    public void dispose() {
        Platform.runLater(() -> {
            if (stage != null) {
                stage.hide();
            }
        });
    }

    public void togglePause() {
        Platform.runLater(() -> {
            if (stage != null) {
                stage.setIconified(!stage.isIconified());
            }
        });
    }

    public void updateScore(String text) {
        Platform.runLater(() -> {
            if (scoreLabel != null) {
                scoreLabel.setText(text);
            }
        });
    }

    public void updatePing(String text) {
        Platform.runLater(() -> {
            if (pingLabel != null) {
                pingLabel.setText(text);
            }
        });
    }

    public void updateTimer(String text) {
        Platform.runLater(() -> {
            if (timerLabel != null) {
                timerLabel.setText(text);
            }
        });
    }

    public void pushNotification(String message) {
        LOG.info("HUD notification: {}", message);
    }

    public void flashCombo() {
        LOG.debug("Combo flash triggered");
    }

    private void buildStage() {
        stage = new Stage();
        stage.setTitle("Pong HUD");
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);

        scoreLabel = new Label(localization.translate("hud.score", 0, 0));
        pingLabel = new Label(localization.translate("hud.ping", 0));
        timerLabel = new Label(localization.translate("hud.timer", "00:00"));

        VBox hudBox = new VBox(10, scoreLabel, pingLabel, timerLabel);
        hudBox.setAlignment(Pos.TOP_LEFT);

        Slider musicSlider = buildSlider(AudioMixer.Bus.MUSIC);
        Slider sfxSlider = buildSlider(AudioMixer.Bus.SFX);
        Slider uiSlider = buildSlider(AudioMixer.Bus.UI);
        VBox mixerBox = new VBox(8,
                new Label("Music"), musicSlider,
                new Label("SFX"), sfxSlider,
                new Label("UI"), uiSlider);
        mixerBox.setAlignment(Pos.CENTER_LEFT);

        Button closeButton = new Button(localization.translate("menu.quit"));
        closeButton.getStyleClass().add("danger");
        closeButton.setOnAction(e -> stage.hide());

        HBox footer = new HBox(closeButton);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setSpacing(12);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("hud-root");
        root.setTop(hudBox);
        root.setCenter(mixerBox);
        root.setBottom(footer);
        root.setPrefSize(360, 220);

        Scene scene = new Scene(root);
        var stylesheet = JavaFxHud.class.getResource("/ui/hud.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        } else {
            LOG.warn("Missing HUD stylesheet");
        }
        stage.setScene(scene);
    }

    private Slider buildSlider(AudioMixer.Bus bus) {
        Slider slider = new Slider(0, 1, mixer.getVolume(bus));
        slider.valueProperty().addListener((obs, oldVal, newVal) -> mixer.setVolume(bus, newVal.floatValue()));
        return slider;
    }
}
