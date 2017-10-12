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

import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks all {@link LeakTracker}s for {@link ULeak}.
 */
final class TrackedReferences {

  private final Map<Class<?>, Map<LeakType, LeakTracker>> leakTrackerMultiMap;

  TrackedReferences() {
    leakTrackerMultiMap =
        Collections.synchronizedMap(new HashMap<Class<?>, Map<LeakType, LeakTracker>>());
  }

  /**
   * Returns whether or not there is a {@link LeakTracker} for the class clazz of type {@link
   * LeakType}.
   *
   * @param clazz the class to check a tracker for.
   * @param leakType the leakType for the tracker to check for.
   * @return whether or not the tracker exists.
   */
  boolean contains(Class<?> clazz, LeakType leakType) {
    Map<LeakType, LeakTracker> trackers = leakTrackerMultiMap.get(clazz);
    return trackers != null && trackers.containsKey(leakType);
  }

  /**
   * Returns an existing tracker or creates one if it doesn't exist.
   *
   * @param clazz the class to get a tracker for.
   * @param leakType the leakType to track.
   * @param className the name of the class to track.
   * @param refGeneratorFactory factory to generate a weak reference.
   * @return existing leak tracker or a new one if it didn't exist already.
   */
  @Nullable
  LeakTracker getOrCreate(
      Class<?> clazz,
      LeakType leakType,
      String className,
      ActiveTracker.RefGeneratorFactory refGeneratorFactory) {
    if (leakTrackerMultiMap.containsKey(clazz)
        && leakTrackerMultiMap.get(clazz).containsKey(leakType)) {
      return leakTrackerMultiMap.get(clazz).get(leakType);
    } else {
      LeakTracker tracker =
          LeakTrackerFactory.getTracker(
              clazz, refGeneratorFactory.createGenerator(), className, leakType);
      addTracker(tracker);
      return tracker;
    }
  }

  /**
   * Adds a leak tracker to the reference map.
   *
   * @param leakTracker the tracker to add to the map.
   */
  private void addTracker(LeakTracker leakTracker) {
    if (leakTrackerMultiMap.containsKey(leakTracker.getEventClass())) {
      Map<LeakType, LeakTracker> trackers = leakTrackerMultiMap.get(leakTracker.getEventClass());
      // Never expect this scenario to occur because if the map contains the class key, then it must
      // have a
      // corresponding trackers map.
      if (trackers == null) {
        return;
      }
      if (!trackers.containsKey(leakTracker.getLeakType())) {
        trackers.put(leakTracker.getLeakType(), leakTracker);
        leakTrackerMultiMap.put(leakTracker.getEventClass(), trackers);
      }
    } else {
      Map<LeakType, LeakTracker> trackers = new HashMap<>();
      trackers.put(leakTracker.getLeakType(), leakTracker);
      leakTrackerMultiMap.put(leakTracker.getEventClass(), trackers);
    }
  }

  /**
   * returns an ArrayList of {@link LeakTracker}s in the reference map.
   *
   * @return an ArrayList of all leak trackers.
   */
  List<LeakTracker> getAllLeakTrackers() {
    List<LeakTracker> leakTrackers = new ArrayList<>();
    for (Map<LeakType, LeakTracker> trackers : leakTrackerMultiMap.values()) {
      leakTrackers.addAll(trackers.values());
    }
    return leakTrackers;
  }
}
