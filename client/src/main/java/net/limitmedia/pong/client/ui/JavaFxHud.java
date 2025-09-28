package net.limitmedia.pong.client.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
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
    private BorderPane root;
    private Label scoreLabel;
    private Label pingLabel;
    private Label timerLabel;
    private Label statusLabel;
    private volatile float requestedScale = 1f;

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

    public void setScale(float scale) {
        requestedScale = scale;
        Platform.runLater(() -> {
            if (root != null) {
                root.setScaleX(scale);
                root.setScaleY(scale);
            }
            if (stage != null && stage.getScene() != null && stage.getScene().getRoot() != null) {
                stage.getScene().getRoot().setScaleX(scale);
                stage.getScene().getRoot().setScaleY(scale);
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
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
                if (!statusLabel.getStyleClass().contains("status-visible")) {
                    statusLabel.getStyleClass().add("status-visible");
                }
                Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), e ->
                        statusLabel.getStyleClass().remove("status-visible")));
                timeline.play();
            }
        });
    }

    public void flashCombo() {
        Platform.runLater(() -> {
            if (scoreLabel != null && !scoreLabel.getStyleClass().contains("highlight")) {
                scoreLabel.getStyleClass().add("highlight");
                Timeline timeline = new Timeline(new KeyFrame(Duration.millis(320), e ->
                        scoreLabel.getStyleClass().remove("highlight")));
                timeline.play();
            }
        });
    }

    private void buildStage() {
        stage = new Stage();
        stage.setTitle("Pong HUD");
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
        stage.initStyle(StageStyle.UNDECORATED);

        scoreLabel = metricLabel(localization.translate("hud.score", 0, 0));
        pingLabel = metricLabel(localization.translate("hud.ping", 0));
        timerLabel = metricLabel(localization.translate("hud.timer", "00:00"));
        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        VBox hudBox = new VBox(6, scoreLabel, pingLabel, timerLabel, statusLabel);
        hudBox.setAlignment(Pos.TOP_LEFT);

        Slider musicSlider = buildSlider(AudioMixer.Bus.MUSIC);
        Slider sfxSlider = buildSlider(AudioMixer.Bus.SFX);
        Slider uiSlider = buildSlider(AudioMixer.Bus.UI);
        VBox mixerBox = new VBox(10,
                labelledSlider("Music", musicSlider),
                labelledSlider("SFX", sfxSlider),
                labelledSlider("UI", uiSlider));
        mixerBox.setAlignment(Pos.CENTER_LEFT);

        Button closeButton = new Button(localization.translate("menu.quit"));
        closeButton.getStyleClass().add("danger");
        closeButton.setOnAction(e -> stage.hide());

        Button resumeButton = new Button("Resume");
        resumeButton.getStyleClass().add("primary");
        resumeButton.setOnAction(e -> stage.hide());

        HBox footer = new HBox(12, resumeButton, closeButton);
        footer.setAlignment(Pos.CENTER_RIGHT);

        root = new BorderPane();
        root.getStyleClass().add("hud-root");
        root.setPadding(new Insets(24));
        root.setTop(hudBox);
        root.setCenter(mixerBox);
        root.setBottom(footer);
        root.setPrefSize(420, 260);

        StackPane container = new StackPane(root);
        container.getStyleClass().add("hud-stage");
        container.setScaleX(requestedScale);
        container.setScaleY(requestedScale);

        Scene scene = new Scene(container);
        var stylesheet = JavaFxHud.class.getResource("/ui/hud.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        } else {
            LOG.warn("Missing HUD stylesheet");
        }
        stage.setScene(scene);
    }

    private Label metricLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("metric");
        return label;
    }

    private VBox labelledSlider(String caption, Slider slider) {
        Label label = new Label(caption);
        label.getStyleClass().add("slider-title");
        VBox box = new VBox(4, label, slider);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Slider buildSlider(AudioMixer.Bus bus) {
        Slider slider = new Slider(0, 1, mixer.getVolume(bus));
        slider.valueProperty().addListener((obs, oldVal, newVal) -> mixer.setVolume(bus, newVal.floatValue()));
        return slider;
    }
}
