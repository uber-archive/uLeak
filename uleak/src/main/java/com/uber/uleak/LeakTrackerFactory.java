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

import static com.uber.uleak.LeakType.SINGLETON;

/**
 * Factory to create different types of {@link LeakTracker}s.
 */
class LeakTrackerFactory {

  private LeakTrackerFactory() {
  }

  /**
   * Creates a {@link LeakTracker}
   *
   * @param eventClass class to be tracked
   * @param weakReferenceGenerator generator to create a weak reference
   * @param name class name
   * @param leakType type of leak tracker to be created
   * @return a {@link LeakTracker} instance.
   */
  static LeakTracker getTracker(
      final Class<?> eventClass,
      final WeakReferenceGenerator weakReferenceGenerator,
      final String name,
      final LeakType leakType) {
    if (leakType == SINGLETON) {
      return new SingleInstanceTracker(eventClass, weakReferenceGenerator, name);
    } else {
      return new LifecycleInstanceTracker(eventClass, weakReferenceGenerator, name);
    }
  }
}
