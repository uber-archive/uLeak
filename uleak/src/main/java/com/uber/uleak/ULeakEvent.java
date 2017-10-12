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

import java.util.HashMap;
import java.util.Map;

/**
 * A uLeak reportable event.
 */
public final class ULeakEvent {

  private final Map<String, Number> metrics = new HashMap<>();
  private final Map<String, String> dimensions = new HashMap<>();

  ULeakEvent() {
  }

  /**
   * @return returns the list of metrics that were added to this event.
   */
  public Map<String, Number> getMetrics() {
    return metrics;
  }

  /**
   * @return returns the list of dimensions that were added to this event.
   */
  public Map<String, String> getDimensions() {
    return dimensions;
  }

  void addMetric(String key, Number metric) {
    metrics.put(key, metric);
  }

  void addDimension(String key, String dimension) {
    dimensions.put(key, dimension);
  }
}
