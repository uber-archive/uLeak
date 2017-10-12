/*
 * Copyright (C) 2017 Uber Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uber.uleak;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class ULeakConfigurationTest {

  @Test
  public void
  uleakConfiguration_whenConfigIsInitializedWithValidValues_ShouldReturnInitializedValues() {
    double explicitGCProbability = .5d;
    ULeakConfiguration configuration =
        new ULeakConfiguration(
            explicitGCProbability, ULeakConfiguration.MINIMUM_CHECK_FREQUENCY_MS + 10);
    assertThat(configuration.explicitGCProbability).isEqualTo(explicitGCProbability);
    assertThat(configuration.shouldUseExplicitGc()).isTrue();
    assertThat(configuration.leakCheckFrequencyMs)
        .isEqualTo(ULeakConfiguration.MINIMUM_CHECK_FREQUENCY_MS + 10);
  }

  @Test
  public void
  uleakConfiguration_whenConfigIsInitializedWithBadProbability_ShouldReturnUpdatedValues() {
    ULeakConfiguration configuration =
        new ULeakConfiguration(-.1, ULeakConfiguration.MINIMUM_CHECK_FREQUENCY_MS + 10);
    assertThat(configuration.explicitGCProbability).isZero();
    assertThat(configuration.shouldUseExplicitGc()).isFalse();
    assertThat(configuration.leakCheckFrequencyMs)
        .isEqualTo(ULeakConfiguration.MINIMUM_CHECK_FREQUENCY_MS + 10);
  }

  @Test
  public void
  uleakConfiguration_whenConfigIsInitializedWithInvalidFrequency_ShouldReturnMinimumFrequency() {
    ULeakConfiguration configuration =
        new ULeakConfiguration(0.0d, ULeakConfiguration.MINIMUM_CHECK_FREQUENCY_MS - 100);
    assertThat(configuration.explicitGCProbability).isZero();
    assertThat(configuration.shouldUseExplicitGc()).isFalse();
    assertThat(configuration.leakCheckFrequencyMs)
        .isEqualTo(ULeakConfiguration.MINIMUM_CHECK_FREQUENCY_MS);
  }

  @Test
  public void
  uleakConfiguration_whenConfigIsInitializedWithInvalidFrequencybutValidP_ShouldReturnMinimumFrequency() {
    double explicitGCProbability = .5d;
    ULeakConfiguration configuration =
        new ULeakConfiguration(
            explicitGCProbability, ULeakConfiguration.MINIMUM_CHECK_FREQUENCY_MS - 100);
    assertThat(configuration.explicitGCProbability).isEqualTo(explicitGCProbability);
    assertThat(configuration.shouldUseExplicitGc()).isTrue();
    assertThat(configuration.leakCheckFrequencyMs)
        .isEqualTo(ULeakConfiguration.MINIMUM_CHECK_FREQUENCY_MS);
  }

  @Test
  public void
  uleakConfiguration_whenConfigIsInitializedWithInvalidInputs_ShouldReturnDefaultValues() {
    ULeakConfiguration configuration =
        new ULeakConfiguration(-.1d, ULeakConfiguration.MINIMUM_CHECK_FREQUENCY_MS - 100);
    assertThat(configuration.explicitGCProbability).isZero();
    assertThat(configuration.shouldUseExplicitGc()).isFalse();
    assertThat(configuration.leakCheckFrequencyMs)
        .isEqualTo(ULeakConfiguration.MINIMUM_CHECK_FREQUENCY_MS);
  }
}
