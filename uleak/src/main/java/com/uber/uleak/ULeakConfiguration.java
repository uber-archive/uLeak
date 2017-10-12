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

import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;

/**
 * A configuration class that sets the various configurations for the uleak instance.
 */
public final class ULeakConfiguration {

  public static final long MINIMUM_CHECK_FREQUENCY_MS = 2000;
  /**
   * The probability that an explicit gc will be called. Value should be between zero and 1
   * inclusive.
   */
  @FloatRange(from = 0, to = 1.0)
  public final double explicitGCProbability;
  /**
   * The frequency of the uleak check in milliseconds.
   */
  @IntRange(from = MINIMUM_CHECK_FREQUENCY_MS)
  public final long leakCheckFrequencyMs;

  public ULeakConfiguration(double explicitGCProbability, long leakCheckFrequencyMs) {
    this.explicitGCProbability = Math.max(0, explicitGCProbability);
    this.leakCheckFrequencyMs = Math.max(MINIMUM_CHECK_FREQUENCY_MS, leakCheckFrequencyMs);
  }

  /**
   * @return Returns if the gc probability is greater than zero.
   */
  public boolean shouldUseExplicitGc() {
    return explicitGCProbability > 0;
  }
}
