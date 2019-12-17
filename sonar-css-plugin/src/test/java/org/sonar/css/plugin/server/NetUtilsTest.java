/*
 * SonarCSS
 * Copyright (C) 2018-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.css.plugin.server;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class NetUtilsTest {

  @Test
  public void findOpenPort_should_not_return_zero() throws IOException {
    assertThat(NetUtils.findOpenPort())
      .isGreaterThan(0)
      .isLessThanOrEqualTo(65535);
  }

  @Test
  public void waitServerToStart_can_be_interrupted() throws InterruptedException {
    // try to connect to a port that does not exists
    Thread worker = new Thread(() -> NetUtils.waitServerToStart("localhost", 8, 1000));
    worker.start();
    Awaitility.setDefaultTimeout(1, TimeUnit.SECONDS);
    // wait for the worker thread to start and to be blocked on Thread.sleep(20);
    await().until(() -> worker.getState() == Thread.State.TIMED_WAITING);

    long start = System.currentTimeMillis();
    worker.interrupt();
    worker.join();
    long timeToInterrupt = System.currentTimeMillis() - start;
    assertThat(timeToInterrupt).isLessThan(20);
  }

}
