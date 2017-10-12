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

import static com.uber.uleak.LeakType.LIFECYCLE;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class tracks objects with a lifecycle that have indicated that they should no longer exist
 * in memory.
 */
class LifecycleInstanceTracker extends LeakTracker {

  private final LinkedBlockingQueue<ReferenceInfo> allocations = new LinkedBlockingQueue<>();
  private final Collection<ReferenceInfo> tempCollection = new LinkedList<>();

  LifecycleInstanceTracker(
      final Class clazz,
      final WeakReferenceGenerator weakReferenceGenerator,
      final String className) {
    super(clazz, weakReferenceGenerator, className, LIFECYCLE);
  }

  @Override
  void track(WeakReference<?> weakReference, long timeCreatedMs) {
    if (!isValidTrackEvent(weakReference)) {
      return;
    }
    allocations.add(new ReferenceInfo(weakReference, timeCreatedMs));
    if (requiresTracking()) {
      resetWeakReference();
    }
  }

  @Override
  boolean requiresTracking() {
    // we always want to track an object at the end of its lifecycle given we were able to generate
    // a valid weak
    // reference.
    return clearInvalidAndGetCount() >= 1;
  }

  @Override
  boolean hasViolation() {
    WeakReference<Object> gcIndicatorWeakReference = getGcIndicatorWeakReference();
    return gcIndicatorWeakReference != null
        && gcIndicatorWeakReference.get() == null
        && isInMemory();
  }

  /**
   * The time at which the riblet was detached.
   *
   * @return the time at which the riblet was detached.
   */
  @Override
  long getLatestReferenceRegistrationTimeMs() {
    long oldestReferenceTime = 0L;
    for (ReferenceInfo referenceInfo : allocations) {
      oldestReferenceTime = Math.max(referenceInfo.getTimeCreatedMs(), oldestReferenceTime);
    }
    return oldestReferenceTime;
  }

  @Override
  int getCurrentNumInMemoryInstances() {
    return clearInvalidAndGetCount();
  }

  @Override
  protected void clearTrackingData() {
    super.clearTrackingData();
    allocations.clear();
    tempCollection.clear();
  }

  // Re-checks all weak references and adds them back to the allocations queue only if the reference
  // still exists.
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

  // If any reference still exists it means the object being referenced is still in memory.
  private boolean isInMemory() {
    return clearInvalidAndGetCount() > 0;
  }
}
