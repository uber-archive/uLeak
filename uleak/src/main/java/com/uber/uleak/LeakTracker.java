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

import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;

import java.lang.ref.WeakReference;

/**
 * The base class for the various trackers.
 */
abstract class LeakTracker {

  /**
   * The class for which we track uLeak violations
   */
  private final Class<?> eventClass;

  private final WeakReferenceGenerator weakReferenceGenerator;
  private final String name;
  private final LeakType leakType;

  /**
   * A placeholder WeakReference that acts as a marker to tell if the GC has run since the
   * allocation
   */
  @Nullable
  private WeakReference<Object> gcIndicatorWeakReference;

  LeakTracker(
      Class<?> eventClass,
      WeakReferenceGenerator weakReferenceGenerator,
      String name,
      final LeakType leakType) {
    this.eventClass = eventClass;
    this.weakReferenceGenerator = weakReferenceGenerator;
    this.name = name;
    this.leakType = leakType;
  }

  /**
   * Java class for the underlying tracker event
   *
   * @return the java class for the underlying tracker event
   */
  final Class<?> getTrackedClass() {
    return eventClass;
  }

  final String getTrackerName() {
    return name;
  }

  @Nullable
  final WeakReference<Object> getGcIndicatorWeakReference() {
    return gcIndicatorWeakReference;
  }

  final Class<?> getEventClass() {
    return eventClass;
  }

  final void resetWeakReference() {
    gcIndicatorWeakReference = weakReferenceGenerator.getGenericWeakRef();
  }

  final boolean isValidTrackEvent(WeakReference<?> weakReference) {
    Object materialized = weakReference.get();
    if (materialized == null || this.getEventClass() != materialized.getClass()) {
      return false;
    }
    return true;
  }

  final LeakType getLeakType() {
    return leakType;
  }

  @CallSuper
  protected void clearTrackingData() {
    gcIndicatorWeakReference = null;
  }

  /**
   * Starts tracking the new event (instance); ensures that the instance is alive and belongs to the
   * tracker
   *
   * @param weakReference to be tracked.
   * @param timeCreatedMs time the tracking was initialized.
   */
  abstract void track(WeakReference<?> weakReference, final long timeCreatedMs);

  abstract boolean requiresTracking();

  abstract boolean hasViolation();

  abstract long getLatestReferenceRegistrationTimeMs();

  abstract int getCurrentNumInMemoryInstances();
}
