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
import static org.mockito.Mockito.when;

import android.support.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.ref.WeakReference;

public class LifecycleInstanceTrackerTest {

  @Mock
  WeakReference<Integer> weakIntReference;
  @Mock
  WeakReference<Object> weakObjectReference;
  @Mock
  WeakReferenceGenerator weakReferenceGenerator;

  private final long lifecycleEndTimeMs = 1234L;

  @SuppressWarnings("NullAway")
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void track_whenObjectIsTrackedButWeakReferenceReturnsNull_shouldNotBeAdded() {
    LifecycleInstanceTracker tracker =
        new LifecycleInstanceTracker(Integer.class, weakReferenceGenerator, "Integer");
    tracker.track(weakIntReference, lifecycleEndTimeMs);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(0);
    assertThat(tracker.requiresTracking()).isFalse();
    // Nothing in allocations.
    assertThat(tracker.getLatestReferenceRegistrationTimeMs()).isEqualTo(0);
  }

  @Test
  public void track_whenObjectIsTrackedButWeakReferenceTypesDontMatch_shouldNotBeAdded() {
    LifecycleInstanceTracker tracker =
        new LifecycleInstanceTracker(Object.class, weakReferenceGenerator, "Object");
    when(weakIntReference.get()).thenReturn(0);
    tracker.track(weakIntReference, lifecycleEndTimeMs);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(0);
    assertThat(tracker.requiresTracking()).isFalse();
    assertThat(tracker.getLatestReferenceRegistrationTimeMs()).isEqualTo(0);
  }

  @Test
  public void track_whenObjectIsTracked_shouldBeAddedAndSizeShouldBeOne() {
    LifecycleInstanceTracker tracker =
        new LifecycleInstanceTracker(Integer.class, weakReferenceGenerator, "Integer");
    when(weakIntReference.get()).thenReturn(0);
    tracker.track(weakIntReference, lifecycleEndTimeMs);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(1);
    assertThat(tracker.requiresTracking()).isTrue();
    assertThat(tracker.getLatestReferenceRegistrationTimeMs()).isEqualTo(lifecycleEndTimeMs);
  }

  @Test
  public void reset_when2ObjectsAreTrackedButResetIsCalled_shouldResetEverything() {
    LifecycleInstanceTracker tracker =
        new LifecycleInstanceTracker(Integer.class, weakReferenceGenerator, "Integer");
    when(weakIntReference.get()).thenReturn(0);
    tracker.track(weakIntReference, lifecycleEndTimeMs);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(1);
    assertThat(tracker.requiresTracking()).isTrue();
    assertThat(tracker.hasViolation()).isFalse();
    tracker.clearTrackingData();
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(0);
    assertThat(tracker.requiresTracking()).isFalse();
  }

  // When object is still in memory aka gc hasn't been called, it should not be a violation.
  @Test
  public void
  hasViolation_whenObjectIsTrackedButGenericWekRefStillHoldsRef_shouldNotCauseViolation() {
    LifecycleInstanceTracker tracker =
        new LifecycleInstanceTracker(Integer.class, new TestWeakRedGenerator(), "Integer");
    when(weakIntReference.get()).thenReturn(0);
    when(weakObjectReference.get()).thenReturn(new Object());
    tracker.track(weakIntReference, lifecycleEndTimeMs);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(1);
    assertThat(tracker.requiresTracking()).isTrue();
    assertThat(tracker.hasViolation()).isFalse();
    assertThat(tracker.getLatestReferenceRegistrationTimeMs()).isEqualTo(lifecycleEndTimeMs);
  }

  // When object has been gc'ed and reference is still in memory, its a leak.
  @Test
  public void hasViolation_whenObjectIsTrackedAndGenericWekRefSIsGCed_shouldCauseViolation() {
    LifecycleInstanceTracker tracker =
        new LifecycleInstanceTracker(Integer.class, new TestWeakRedGenerator(), "Integer");
    when(weakIntReference.get()).thenReturn(0);
    tracker.track(weakIntReference, lifecycleEndTimeMs);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(1);
    assertThat(tracker.requiresTracking()).isTrue();
    assertThat(tracker.hasViolation()).isTrue();
    assertThat(tracker.getLatestReferenceRegistrationTimeMs()).isEqualTo(lifecycleEndTimeMs);
  }

  private class TestWeakRedGenerator extends WeakReferenceGenerator {

    @NonNull
    @Override
    public synchronized WeakReference<Object> getGenericWeakRef() {
      return weakObjectReference;
    }
  }
}
