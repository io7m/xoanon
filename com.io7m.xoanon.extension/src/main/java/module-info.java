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

import org.junit.platform.launcher.LauncherSessionListener;

/**
 * JUnit 5 JavaFX extension (Extension)
 */

module com.io7m.xoanon.extension
{
  requires static org.osgi.annotation.bundle;
  requires static org.osgi.annotation.versioning;

  requires transitive org.junit.platform.launcher;
  requires transitive org.junit.jupiter.api;
  requires transitive javafx.base;
  requires transitive javafx.controls;

  requires com.io7m.xoanon.commander;
  requires com.io7m.xoanon.commander.api;

  requires org.slf4j;

  provides LauncherSessionListener
    with com.io7m.xoanon.extension.XoExtension;

  exports com.io7m.xoanon.extension;
}
