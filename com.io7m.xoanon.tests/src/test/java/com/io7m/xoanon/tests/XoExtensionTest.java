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

package com.io7m.xoanon.tests;

import com.io7m.percentpass.extension.PercentPassing;
import com.io7m.xoanon.extension.XoBots;
import com.io7m.xoanon.extension.XoExtension;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static javafx.scene.input.KeyCode.E;
import static javafx.scene.input.KeyCode.H;
import static javafx.scene.input.KeyCode.L;
import static javafx.scene.input.KeyCode.O;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(XoExtension.class)
public final class XoExtensionTest
{
  private static final Logger LOG =
    LoggerFactory.getLogger(XoExtensionTest.class);

  @PercentPassing(executionCount = 6, passPercent = 50.0)
  public void testButton(
    final Stage stage)
    throws Exception
  {
    final var bot = XoBots.createForStage(stage);

    final var clicked = new AtomicBoolean(false);
    Platform.runLater(() -> {
      final var button = new Button();
      button.setId("x");
      button.setOnMouseClicked(event -> {
        LOG.debug("click!");
        clicked.set(true);
      });
      stage.setScene(new Scene(button));
    });

    final var node = bot.findWithId("x");
    bot.click(node);
    bot.sleepForFrames(60);

    assertTrue(clicked.get());
  }

  @PercentPassing(executionCount = 6, passPercent = 50.0)
  public void testTextField(
    final Stage stage)
    throws Exception
  {
    final var bot = XoBots.createForStage(stage);

    final var text = new AtomicReference<String>();
    Platform.runLater(() -> {
      final var field = new TextField();
      field.setId("x");
      field.textProperty()
        .addListener((observable, oldValue, newValue) -> {
          text.set(newValue);
        });
      stage.setScene(new Scene(field));
    });

    final var node = bot.findWithId("x");
    bot.click(node);
    bot.type(node, H, E, L, L, O);
    bot.sleepForFrames(60);

    assertEquals("hello", text.get());
  }

  @PercentPassing(executionCount = 6, passPercent = 50.0)
  public void testTextFieldShift(
    final Stage stage)
    throws Exception
  {
    final var bot = XoBots.createForStage(stage);

    final var text = new AtomicReference<String>();
    Platform.runLater(() -> {
      final var field = new TextField();
      field.setId("x");
      field.textProperty()
        .addListener((observable, oldValue, newValue) -> {
          text.set(newValue);
        });
      stage.setScene(new Scene(field));
    });

    final var node = bot.findWithId("x");
    bot.click(node);
    bot.typeWithShift(node, H, E, L, L, O);
    bot.sleepForFrames(60);

    assertEquals("HELLO", text.get());
  }
}
