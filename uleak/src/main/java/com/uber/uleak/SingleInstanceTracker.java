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

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class tracks a particular class that could be leaking. Note this class is not thread safe.
 */
class SingleInstanceTracker extends LeakTracker {

  private static final int MAX_ACTIVE_INSTANCES_ALLOWED = 1;

  /**
   * A list of weak references to instances allocated so far
   */
  private final LinkedBlockingQueue<ReferenceInfo> allocations = new LinkedBlockingQueue<>();

  private final Collection<ReferenceInfo> tempCollection = new LinkedList<>();

  SingleInstanceTracker(
      final Class<?> eventClass,
      final WeakReferenceGenerator weakReferenceGenerator,
      final String name) {
    super(eventClass, weakReferenceGenerator, name, SINGLETON);
  }

  @Override
  protected void track(WeakReference<?> weakReference, final long timeCreatedMs) {
    if (!isValidTrackEvent(weakReference)) {
      return;
    }
    allocations.add(new ReferenceInfo(weakReference, timeCreatedMs));
    if (requiresTracking()) {
      resetWeakReference();
    }
  }

  @Override
  protected void clearTrackingData() {
    super.clearTrackingData();
    allocations.clear();
    tempCollection.clear();
  }

  @Override
  boolean requiresTracking() {
    return clearInvalidAndGetCount() > 1;
  }

  /**
   * checks if the number of active instances are greater than the constraint specified
   *
   * @return true if and only if the leak constraint is violated
   */
  @Override
  boolean hasViolation() {
    WeakReference<Object> gcIndicatorWeakReference = getGcIndicatorWeakReference();
    return gcIndicatorWeakReference != null
        && gcIndicatorWeakReference.get() == null
        && clearInvalidAndGetCount() > MAX_ACTIVE_INSTANCES_ALLOWED;
  }

  @Override
  int getCurrentNumInMemoryInstances() {
    return allocations.size();
  }

  /**
   * The time of creation of the latest reference to the object.
   *
   * @return return the oldest time entry of all the weak references being tracked.
   */
  @Override
  long getLatestReferenceRegistrationTimeMs() {
    long oldestReferenceTime = 0L;
    for (ReferenceInfo referenceInfo : allocations) {
      oldestReferenceTime = Math.max(referenceInfo.getTimeCreatedMs(), oldestReferenceTime);
    }
    return oldestReferenceTime;
  }

  /**
   * Get the number of active instances for this class It iterates through the list of weak
   * references to find the currently active ones and count them
   *
   * @return # of active instances
   */
  private int clearInvalidAndGetCount() {
    tempCollection.clear();
    allocations.drainTo(tempCollection);
    for (ReferenceInfo referenceInfo : tempCollection) {
      if (referenceInfo.getWeakReference().get() != null) {
        allocations.add(referenceInfo);
      }
    }
    return allocations.size();
  }
}
