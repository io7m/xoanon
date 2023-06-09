/*
 * Copyright © 2023 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.io7m.xoanon.commander.internal;

import com.io7m.xoanon.commander.XBVersion;
import com.io7m.xoanon.commander.api.XCApplicationInfo;
import com.io7m.xoanon.commander.api.XCCommanderType;
import com.io7m.xoanon.commander.api.XCFXThread;
import com.io7m.xoanon.commander.api.XCKey;
import com.io7m.xoanon.commander.api.XCKeyMap;
import com.io7m.xoanon.commander.api.XCRobotType;
import com.io7m.xoanon.commander.api.XCTestInfo;
import com.io7m.xoanon.commander.api.XCTestState;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.robot.Robot;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static javafx.animation.Interpolator.LINEAR;
import static javafx.scene.input.KeyCode.ADD;
import static javafx.scene.input.KeyCode.AMPERSAND;
import static javafx.scene.input.KeyCode.ASTERISK;
import static javafx.scene.input.KeyCode.BACK_QUOTE;
import static javafx.scene.input.KeyCode.BACK_SLASH;
import static javafx.scene.input.KeyCode.BRACELEFT;
import static javafx.scene.input.KeyCode.BRACERIGHT;
import static javafx.scene.input.KeyCode.CIRCUMFLEX;
import static javafx.scene.input.KeyCode.CLOSE_BRACKET;
import static javafx.scene.input.KeyCode.COLON;
import static javafx.scene.input.KeyCode.COMMA;
import static javafx.scene.input.KeyCode.DECIMAL;
import static javafx.scene.input.KeyCode.DIVIDE;
import static javafx.scene.input.KeyCode.DOLLAR;
import static javafx.scene.input.KeyCode.EQUALS;
import static javafx.scene.input.KeyCode.EURO_SIGN;
import static javafx.scene.input.KeyCode.EXCLAMATION_MARK;
import static javafx.scene.input.KeyCode.GREATER;
import static javafx.scene.input.KeyCode.LEFT_PARENTHESIS;
import static javafx.scene.input.KeyCode.LESS;
import static javafx.scene.input.KeyCode.MINUS;
import static javafx.scene.input.KeyCode.NUMBER_SIGN;
import static javafx.scene.input.KeyCode.OPEN_BRACKET;
import static javafx.scene.input.KeyCode.PERIOD;
import static javafx.scene.input.KeyCode.PLUS;
import static javafx.scene.input.KeyCode.POUND;
import static javafx.scene.input.KeyCode.QUOTE;
import static javafx.scene.input.KeyCode.QUOTEDBL;
import static javafx.scene.input.KeyCode.RIGHT_PARENTHESIS;
import static javafx.scene.input.KeyCode.SEMICOLON;
import static javafx.scene.input.KeyCode.SHIFT;
import static javafx.scene.input.KeyCode.SLASH;
import static javafx.scene.input.KeyCode.STAR;
import static javafx.scene.input.KeyCode.SUBTRACT;
import static javafx.scene.input.KeyCode.UNDERSCORE;
import static javafx.scene.input.KeyCode.values;

/**
 * The main commander.
 */

public final class XCCommander
  implements XCCommanderType, Initializable
{
  private static final Logger LOG =
    LoggerFactory.getLogger(XCCommander.class);

  private static final Set<KeyCode> ALL_KEY_CODES =
    generateAllAllowedKeyCodes();

  private final ScheduledExecutorService executor;
  private final XBStrings strings;
  private final Stage stage;
  private final ObservableList<XCTestInfo> testsList;
  private final AtomicReference<XCKeyMap> keyMap;
  private final XCKeyMapCache keyMapCache;
  private final AtomicReference<XCRobot> robot;
  private final Robot baseRobot;
  private final AtomicBoolean testsStarted;
  private final OffsetDateTime timeStarted;
  private final Set<String> testsRegistered;
  private volatile int stagesCreatedCount;
  private volatile int stagesReleasedCount;
  private volatile XCTestState testsStateWorst;
  private volatile long testsIndex;
  private volatile long testsFailed;

  @FXML private TextArea input;
  @FXML private TextField status;
  @FXML private Parent splash;
  @FXML private Parent diagnostics;
  @FXML private ListView<XCTestInfo> tests;
  @FXML private ListView<Window> windowListView;
  @FXML private Pane info;
  @FXML private ProgressBar progress;
  @FXML private Label testVersion;
  @FXML private Label statusName;
  @FXML private Rectangle statusLight;
  @FXML private Pane testsInfoContainer;
  @FXML private TextField dataApp;
  @FXML private TextField dataCommit;
  @FXML private TextField dataDuration;
  @FXML private TextField dataHost;
  @FXML private TextField dataOS;
  @FXML private TextField dataRuntime;
  @FXML private TextField dataStarted;
  @FXML private TextField dataVersion;
  @FXML private TextField dataStagesCreated;
  @FXML private TextField dataStagesReleased;
  @FXML private TextField dataTestsExpected;
  @FXML private TextField dataTestsExecuted;
  @FXML private TextField dataTestsFailed;
  @FXML private TextField dataExecutionId;
  @FXML private TextField dataCurrentTestId;
  @FXML private Label heapText;
  @FXML private ProgressBar heapUsed;

  /**
   * Construct a commander.
   *
   * @param inStrings The strings
   * @param inStage   The stage hosting the commander
   */

  public XCCommander(
    final XBStrings inStrings,
    final Stage inStage)
  {
    this.strings =
      Objects.requireNonNull(inStrings, "strings");
    this.stage =
      Objects.requireNonNull(inStage, "stage");

    this.testsRegistered =
      ConcurrentHashMap.newKeySet();
    this.testsStateWorst =
      XCTestState.INITIAL;
    this.testsStarted =
      new AtomicBoolean(false);
    this.timeStarted =
      OffsetDateTime.now();

    this.executor =
      Executors.newSingleThreadScheduledExecutor(runnable -> {
        final var thread = new Thread(runnable);
        thread.setName(
          "com.io7m.xoanon.commander[%d]"
            .formatted(Long.valueOf(thread.getId())));
        thread.setDaemon(true);
        return thread;
      });

    this.testsList =
      FXCollections.observableArrayList();
    this.keyMap =
      new AtomicReference<>();
    this.keyMapCache =
      new XCKeyMapCache(
        Clock.systemUTC(),
        Paths.get(System.getProperty("java.io.tmpdir"))
      );
    this.robot =
      new AtomicReference<>();
    this.baseRobot =
      new Robot();
  }

  /*
   * Generate the set of keycodes that keymap generation is allowed to
   * use. There is a tension here between providing complete coverage and
   * not pressing any "dangerous" keys.
   */

  private static Set<KeyCode> generateAllAllowedKeyCodes()
  {
    final var codes = new HashSet<KeyCode>(256);
    for (final var code : values()) {
      if (code.isLetterKey()) {
        codes.add(code);
      }
      if (code.isDigitKey()) {
        codes.add(code);
      }
    }

    codes.add(ADD);
    codes.add(AMPERSAND);
    codes.add(ASTERISK);
    codes.add(BACK_QUOTE);
    codes.add(BACK_SLASH);
    codes.add(BRACELEFT);
    codes.add(BRACERIGHT);
    codes.add(CIRCUMFLEX);
    codes.add(CLOSE_BRACKET);
    codes.add(COLON);
    codes.add(COMMA);
    codes.add(DECIMAL);
    codes.add(DIVIDE);
    codes.add(DOLLAR);
    codes.add(EQUALS);
    codes.add(EURO_SIGN);
    codes.add(EXCLAMATION_MARK);
    codes.add(GREATER);
    codes.add(LEFT_PARENTHESIS);
    codes.add(LESS);
    codes.add(MINUS);
    codes.add(NUMBER_SIGN);
    codes.add(OPEN_BRACKET);
    codes.add(PERIOD);
    codes.add(PLUS);
    codes.add(POUND);
    codes.add(QUOTE);
    codes.add(QUOTEDBL);
    codes.add(RIGHT_PARENTHESIS);
    codes.add(SEMICOLON);
    codes.add(SLASH);
    codes.add(STAR);
    codes.add(SUBTRACT);
    codes.add(UNDERSCORE);

    return Set.copyOf(codes);
  }

  private static void pause()
  {
    try {
      Thread.sleep(2L * 16L);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void initialize(
    final URL location,
    final ResourceBundle resources)
  {
    try {
      this.dataOS.setText(
        String.format(
          "%s %s %s",
          System.getProperty("os.name"),
          System.getProperty("os.version"),
          System.getProperty("os.arch"))
      );
    } catch (final Exception e) {
      LOG.debug("", e);
    }

    try {
      this.dataRuntime.setText(
        String.format(
          "%s %s",
          System.getProperty("java.vendor"),
          System.getProperty("java.version")
        )
      );
    } catch (final Exception e) {
      LOG.debug("", e);
    }

    try {
      this.dataStarted.setText(
        OffsetDateTime.now(ZoneId.of("UTC")).toString()
      );
    } catch (final Exception e) {
      LOG.debug("", e);
    }

    try {
      this.dataHost.setText(
        InetAddress.getLocalHost().getHostName()
      );
    } catch (final Exception e) {
      LOG.debug("", e);
    }

    this.dataStagesCreated.setText("0");
    this.dataStagesReleased.setText("0");
    this.dataTestsExecuted.setText("0");
    this.dataTestsFailed.setText("0");
    this.dataTestsExpected.setText("0");

    final var execId = UUID.randomUUID();
    this.dataExecutionId.setText(execId.toString());
    LOG.info("execution ID: {}", execId);

    final var title = "Xoanon Test Harness %s".formatted(XBVersion.MAIN_VERSION);
    this.testVersion.setText(title);
    this.stage.setTitle(title);

    this.splash.setFocusTraversable(false);
    this.splash.setMouseTransparent(true);

    this.diagnosticsLock();

    this.status.setMouseTransparent(true);
    this.status.setFocusTraversable(false);

    this.tests.setMouseTransparent(true);
    this.tests.setFocusTraversable(false);

    this.tests.setFixedCellSize(16.0);
    this.tests.setCellFactory(new XBTestCellFactory(this.strings));
    this.tests.setItems(this.testsList);

    this.windowListView.setFixedCellSize(16.0);
    this.windowListView.setCellFactory(new XBWindowCellFactory(this.strings));

    this.status.setText("Waiting...");
    this.statusName.setText(this.testsStateWorst.name());

    this.splash.setVisible(true);
    this.executor.schedule(
      () -> Platform.runLater(this::splashHide),
      1L,
      TimeUnit.SECONDS
    );

    this.executor.scheduleAtFixedRate(
      this::updateHeap, 0L, 1L, TimeUnit.SECONDS);

    Window.getWindows()
      .addListener(XCCommander.this::onWindowsChanged);
  }

  private void onWindowsChanged(
    final ListChangeListener.Change<? extends Window> c)
  {
    while (c.next()) {
      if (c.wasAdded()) {
        for (final var w : c.getAddedSubList()) {
          final var title = (w instanceof final Stage s) ? s.getTitle() : "";
          LOG.debug("window created: [{}] ({})", w, title);
          ++this.stagesCreatedCount;
          this.dataStagesCreated.setText(
            Integer.toString(this.stagesCreatedCount)
          );
        }
      }

      if (c.wasRemoved()) {
        for (final var w : c.getRemoved()) {
          final var title = (w instanceof final Stage s) ? s.getTitle() : "";
          LOG.debug("window removed: [{}] ({})", w, title);
          ++this.stagesReleasedCount;
          this.dataStagesReleased.setText(
            Integer.toString(this.stagesCreatedCount)
          );
        }
      }
    }

    final var windowsNow = List.copyOf(Window.getWindows());
    this.windowListView.setItems(FXCollections.observableList(windowsNow));

    for (var index = 0; index < windowsNow.size(); ++index) {
      final var window = windowsNow.get(index);
      final var title = (window instanceof final Stage s) ? s.getTitle() : "";
      LOG.debug("window [{}] {} {}", Integer.valueOf(index), window, title);
    }
  }

  private void updateHeap()
  {
    final var runtime =
      Runtime.getRuntime();
    final var used =
      runtime.totalMemory() - runtime.freeMemory();
    final var max =
      runtime.totalMemory();

    final var usedProp =
      (double) used / (double) max;

    Platform.runLater(() -> {
      this.heapText.setText(
        String.format(
          "Heap: Used: %s Max: %s",
          Long.toUnsignedString(used),
          Long.toUnsignedString(max)
        ));
      this.heapUsed.setProgress(usedProp);
    });
  }

  private void splashHide()
  {
    final var fade = new FadeTransition(Duration.millis(500L));
    fade.setNode(this.splash);
    fade.setFromValue(1.0);
    fade.setToValue(0.0);
    fade.setInterpolator(LINEAR);
    fade.playFromStart();
    fade.setOnFinished(event -> this.splash.setVisible(false));
  }

  private void splashShow(
    final Runnable onFinished)
  {
    this.splash.setVisible(true);

    final var pause = new FadeTransition(Duration.millis(1000L));
    pause.setNode(this.splash);
    pause.setFromValue(1.0);
    pause.setToValue(1.0);
    pause.setInterpolator(LINEAR);
    pause.setOnFinished(event -> onFinished.run());

    final var fade = new FadeTransition(Duration.millis(500L));
    fade.setNode(this.splash);
    fade.setFromValue(0.0);
    fade.setToValue(1.0);
    fade.setInterpolator(LINEAR);
    fade.setOnFinished(event -> pause.playFromStart());
    fade.playFromStart();
  }

  @Override
  public void close()
    throws Exception
  {
    final var future = new CompletableFuture<Void>();
    this.executor.schedule(() -> {
      try {
        future.complete(this.shutDown());
      } catch (final Throwable e) {
        future.completeExceptionally(e);
      }
    }, 1L, TimeUnit.SECONDS);
    future.get(10L, TimeUnit.SECONDS);
  }

  private Void shutDown()
    throws Exception
  {
    Platform.runLater(() -> {
      this.status.setText("Shutting down...");
    });

    final var closeLatch = new CountDownLatch(1);
    Platform.runLater(() -> {
      this.splashShow(closeLatch::countDown);
    });
    closeLatch.await(30L, TimeUnit.SECONDS);

    this.executor.shutdown();
    return null;
  }

  @Override
  public Stage stage()
  {
    return this.stage;
  }

  @Override
  public void setTestState(
    final XCTestInfo test)
  {
    Objects.requireNonNull(test, "test");

    if (this.testsStarted.compareAndSet(false, true)) {
      this.executor.scheduleAtFixedRate(() -> {
        Platform.runLater(() -> {
          this.dataDuration.setText(
            java.time.Duration.between(
              this.timeStarted,
              OffsetDateTime.now()
            ).toString()
          );
        });
      }, 0L, 1L, TimeUnit.SECONDS);
    }

    Platform.runLater(() -> {
      this.testsRegistered.add(test.id());

      switch (test.state()) {
        case FAILED -> {
          ++this.testsFailed;
        }
        case RUNNING -> {
          ++this.testsIndex;
        }
        case INITIAL, SUCCEEDED -> {

        }
      }

      this.testCountDisplaysUpdate();
      this.dataCurrentTestId.setText(test.id());
      this.status.setText("%s %s".formatted(test.name(), test.state()));

      switch (this.testsStateWorst) {
        case INITIAL, RUNNING, SUCCEEDED -> {
          this.testsStateWorst = test.state();
          this.statusLight.setFill(XBTestColors.colorForTest(test));
          this.statusName.setText(test.state().name());
        }
        case FAILED -> {

        }
      }
    });

    Platform.runLater(() -> {
      final var newList =
        this.tests.getItems()
          .stream()
          .filter(t -> !Objects.equals(t.id(), test.id()))
          .collect(Collectors.toCollection(ArrayList::new));

      newList.add(test);
      newList.sort(Comparator.comparing(XCTestInfo::time).reversed());
      this.testsList.setAll(newList.stream().limit(40L).toList());
    });
  }

  @Override
  public CompletableFuture<XCKeyMap> keyMap()
  {
    final var existing = this.keyMap.get();
    if (existing != null) {
      return CompletableFuture.completedFuture(existing);
    }

    final var future = new CompletableFuture<XCKeyMap>();
    this.executor.execute(() -> {
      try {
        future.complete(this.keyMapLoadCachedOrGenerate());
      } catch (final Throwable e) {
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  @Override
  public void sendToBack()
  {
    Platform.runLater(this.stage::toBack);
  }

  @Override
  public CompletableFuture<XCRobotType> robot()
  {
    final var existing = this.robot.get();
    if (existing != null) {
      return CompletableFuture.completedFuture(existing);
    }

    return this.keyMap()
      .thenApply(k -> {
        final var newRobot = new XCRobot(k, this.baseRobot);
        this.robot.set(newRobot);
        return newRobot;
      });
  }

  @Override
  public CompletableFuture<Stage> stageNew(
    final Consumer<Stage> onCreate)
  {
    final var stageFuture =
      XCFXThread.run(() -> {
        final var newStage = new Stage();
        newStage.setMinWidth(16.0);
        newStage.setMinHeight(16.0);
        newStage.setMaxWidth(3000.0);
        newStage.setMaxHeight(3000.0);
        newStage.setWidth(320.0);
        newStage.setHeight(240.0);
        newStage.show();
        newStage.toFront();

        onCreate.accept(newStage);
        return newStage;
      });

    /*
     * Fetch the stage after a short delay. This gives time for the stage
     * to fully open and display any configured scene.
     */

    final var future = new CompletableFuture<Stage>();
    this.executor.schedule(() -> {
      try {
        future.complete(stageFuture.get());
      } catch (final Throwable e) {
        future.completeExceptionally(e);
      }
    }, 100L, TimeUnit.MILLISECONDS);
    return future;
  }

  @Override
  public CompletableFuture<Void> stageCloseAll()
  {
    return XCFXThread.run(() -> {
      final var windows =
        Window.getWindows()
          .stream()
          .filter(window -> !Objects.equals(window, this.stage))
          .filter(window -> window.isShowing())
          .filter(window -> window instanceof Stage)
          .map(Stage.class::cast)
          .toList();

      for (final var window : windows) {
        try {
          window.close();
        } catch (final Throwable e) {
          LOG.error("close: {} ({}): ", window, window.getTitle(), e);
        }
      }
      return null;
    });
  }

  @Override
  public void setApplicationInfo(
    final XCApplicationInfo appInfo)
  {
    Platform.runLater(() -> {
      this.dataApp.setText(appInfo.name());
      this.dataVersion.setText(appInfo.version());
      this.dataCommit.setText(appInfo.build());
    });
  }

  private void testCountDisplaysUpdate()
  {
    final var total =
      Integer.toUnsignedLong(this.testsRegistered.size());

    this.progress.setProgress(
      (double) this.testsIndex / (double) total
    );
    this.dataTestsExpected.setText(
      Long.toUnsignedString(total)
    );
    this.dataTestsExecuted.setText(
      Long.toUnsignedString(this.testsIndex)
    );
    this.dataTestsFailed.setText(
      Long.toUnsignedString(this.testsFailed)
    );
  }

  /*
   * Check if there's a suitable cached keymap. If there isn't, generate one.
   */

  private XCKeyMap keyMapLoadCachedOrGenerate()
  {
    final var cached = this.keyMapCache.load();
    if (cached.isPresent()) {
      final var newMap = cached.get();
      this.keyMap.set(newMap);
      return newMap;
    }

    for (var attempt = 0; attempt < 3; ++attempt) {
      final XCKeyMap generated;
      try {
        generated = this.keyMapGenerate();
        this.keyMap.set(generated);
        this.keyMapCache.save(generated);
        return generated;
      } catch (final Exception e) {
        // Failed to generate a keymap
      }
    }

    throw new IllegalStateException("Failed to generate a keymap.");
  }

  /**
   * Generate a keymap by pressing every non-special key on the keyboard
   * and recording what happens. The mapping can then be used to work backwards
   * from characters to keys when attempting to send key events to components.
   *
   * @return A keymap
   *
   * @throws Exception On errors
   */

  private XCKeyMap keyMapGenerate()
    throws Exception
  {
    try {

      /*
       * For keymap generation, the commander window must be at the front.
       */

      Platform.runLater(this.stage::toFront);
      Thread.sleep(250L);

      Platform.runLater(() -> {
        this.input.setDisable(false);
      });

      Platform.runLater(() -> {
        this.input.requestFocus();
      });

      Platform.runLater(() -> {
        this.status.setText("Generating keymap...");
      });

      Platform.runLater(this::diagnosticsUnlock);

      final var newMappings =
        new ConcurrentHashMap<Character, XCKey>();

      final var index = new AtomicInteger(1);
      final var count = ALL_KEY_CODES.size();

      for (final var code : ALL_KEY_CODES) {
        Platform.runLater(() -> {
          this.progress.setProgress(
            index.doubleValue() / (double) count
          );
        });

        for (var attempt = 0; attempt < 2; ++attempt) {
          this.keyMapGenerateOneCharacter(newMappings, code);
        }

        Platform.requestNextPulse();
        Platform.runLater(index::incrementAndGet);
      }

      Platform.requestNextPulse();
      Platform.runLater(() -> {
        this.status.setText("Generated keymap.");
      });

      Platform.runLater(() -> {
        this.input.clear();
      });

      Platform.runLater(() -> {
        this.input.setDisable(true);
      });

      final var latch = new CountDownLatch(1);
      Platform.runLater(latch::countDown);
      latch.await();

      LOG.debug(
        "Generated key map of size {}",
        Integer.valueOf(newMappings.size()));

      if (newMappings.size() < 88) {
        throw new IOException("Key mappings incomplete: >= 88 keys required.");
      }

      final var result = new XCKeyMap(Map.copyOf(newMappings));
      this.keyMap.set(result);
      return result;
    } catch (final Throwable e) {
      Platform.runLater(this::diagnosticsLock);
      throw e;
    } finally {
      Platform.requestNextPulse();
      this.releaseAllKeys();
    }
  }

  private void keyMapGenerateOneCharacter(
    final ConcurrentHashMap<Character, XCKey> newMappings,
    final KeyCode code)
  {
    Platform.runLater(() -> {
      LOG.trace("check {}", code);
      this.status.setText("Generating keymap: Checking text for %s".formatted(
        code));

      final var bounds =
        this.input.localToScreen(this.input.getBoundsInLocal());
      final var target =
        new Point2D(bounds.getCenterX(), bounds.getCenterY());

      this.baseRobot.mouseMove(target);
      this.baseRobot.mouseClick(MouseButton.PRIMARY);
    });

    this.keyMapGenerateOneCharacterNoModifiers(newMappings, code);
    this.keyMapGenerateOneCharacterShift(newMappings, code);
  }

  private void keyMapGenerateOneCharacterShift(
    final ConcurrentHashMap<Character, XCKey> newMappings,
    final KeyCode code)
  {
    Platform.requestNextPulse();
    Platform.runLater(() -> {
      this.input.clear();
    });

    Platform.requestNextPulse();
    Platform.runLater(() -> {
      this.baseRobot.keyPress(SHIFT);
    });

    Platform.requestNextPulse();
    Platform.runLater(() -> {
      this.baseRobot.keyType(code);
    });

    pause();

    Platform.requestNextPulse();
    Platform.runLater(() -> {
      this.baseRobot.keyRelease(SHIFT);
    });

    Platform.requestNextPulse();
    Platform.runLater(() -> {
      final var text = this.input.getText();
      LOG.trace("SHIFT code {} -> '{}'", code, text);
      if (text.isEmpty()) {
        return;
      }
      final var characters = text.toCharArray();
      final var character = characters[0];

      newMappings.put(
        Character.valueOf(character),
        new XCKey(code, true, false, false)
      );
    });
  }

  private void keyMapGenerateOneCharacterNoModifiers(
    final ConcurrentHashMap<Character, XCKey> newMappings,
    final KeyCode code)
  {
    Platform.requestNextPulse();
    Platform.runLater(() -> {
      this.input.clear();
    });

    Platform.requestNextPulse();
    Platform.runLater(() -> {
      this.baseRobot.keyType(code);
    });

    pause();

    Platform.requestNextPulse();
    Platform.runLater(() -> {
      final var text = this.input.getText();
      LOG.trace("code {} -> '{}'", code, text);
      if (text.isEmpty()) {
        return;
      }
      final var characters = text.toCharArray();
      final var character = characters[0];

      newMappings.put(
        Character.valueOf(character),
        new XCKey(code, false, false, false)
      );
    });
  }

  private void releaseAllKeys()
  {
    for (final var code : ALL_KEY_CODES) {
      Platform.runLater(() -> this.baseRobot.keyRelease(code));
      Platform.requestNextPulse();
    }
  }

  private void diagnosticsUnlock()
  {
    this.input.setFocusTraversable(true);
    this.input.setMouseTransparent(false);
  }

  private void diagnosticsLock()
  {
    this.input.setFocusTraversable(false);
    this.input.setMouseTransparent(true);
  }
}
