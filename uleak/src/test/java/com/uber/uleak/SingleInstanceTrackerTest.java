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

public class SingleInstanceTrackerTest {

  @Mock
  WeakReference<Integer> weakIntReference;
  @Mock
  WeakReference<Integer> weakIntReference2;
  @Mock
  WeakReference<Object> weakIntReference3;
  @Mock
  WeakReferenceGenerator weakReferenceGenerator;

  private final long timeCreatedMs = 1234L;
  private final long timeCreated2Ms = 4321L;

  @SuppressWarnings("NullAway")
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void getEventClass_whenObjectCreated_ShouldReturnSameClass() {
    SingleInstanceTracker tracker =
        new SingleInstanceTracker(Integer.class, weakReferenceGenerator, "Integer");
    assertThat(tracker.getTrackedClass()).isEqualTo(Integer.class);
    assertThat(tracker.requiresTracking()).isFalse();
    assertThat(tracker.getLatestReferenceRegistrationTimeMs()).isEqualTo(0L);
  }

  @Test
  public void track_whenObjectIsTrackedButWeakReferenceReturnsNull_ShouldNotBeAdded() {
    SingleInstanceTracker tracker =
        new SingleInstanceTracker(Integer.class, weakReferenceGenerator, "Integer");
    tracker.track(weakIntReference, timeCreatedMs);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(0);
    assertThat(tracker.requiresTracking()).isFalse();
    // Nothing in allocations.
    assertThat(tracker.getLatestReferenceRegistrationTimeMs()).isEqualTo(0);
  }

  @Test
  public void track_whenObjectIsTrackedButWeakReferenceTypesDontMatch_ShouldNotBeAdded() {
    SingleInstanceTracker tracker =
        new SingleInstanceTracker(Object.class, weakReferenceGenerator, "Object");
    when(weakIntReference.get()).thenReturn(0);
    tracker.track(weakIntReference, timeCreatedMs);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(0);
    assertThat(tracker.requiresTracking()).isFalse();
    assertThat(tracker.getLatestReferenceRegistrationTimeMs()).isEqualTo(0);
  }

  @Test
  public void track_whenObjectIsTracked_ShouldBeAddedAndSizeShouldBeOne() {
    SingleInstanceTracker tracker =
        new SingleInstanceTracker(Integer.class, weakReferenceGenerator, "Integer");
    when(weakIntReference.get()).thenReturn(0);
    tracker.track(weakIntReference, timeCreatedMs);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(1);
    assertThat(tracker.requiresTracking()).isFalse();
    assertThat(tracker.getLatestReferenceRegistrationTimeMs()).isEqualTo(timeCreatedMs);
  }

  @Test
  public void
  hasViolation_when2ObjectsAreTrackedButGenericWeakRefStillHoldsRef_ShouldNotCauseViolation() {
    SingleInstanceTracker tracker =
        new SingleInstanceTracker(Integer.class, new TestWeakRefGenerator(), "Integer");
    when(weakIntReference.get()).thenReturn(0);
    when(weakIntReference2.get()).thenReturn(1);
    when(weakIntReference3.get()).thenReturn(new Object());
    tracker.track(weakIntReference, timeCreatedMs);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(1);
    tracker.track(weakIntReference2, timeCreated2Ms);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(2);
    assertThat(tracker.requiresTracking()).isTrue();
    assertThat(tracker.hasViolation()).isFalse();
    assertThat(tracker.getLatestReferenceRegistrationTimeMs()).isEqualTo(timeCreated2Ms);
  }

  @Test
  public void hasViolation_when2ObjectsAreTrackedButGenericWeakRefIsGced_ShouldCauseViolation() {
    SingleInstanceTracker tracker =
        new SingleInstanceTracker(Integer.class, new TestWeakRefGenerator(), "Integer");
    when(weakIntReference.get()).thenReturn(0);
    when(weakIntReference2.get()).thenReturn(1);
    tracker.track(weakIntReference, timeCreatedMs);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(1);
    tracker.track(weakIntReference2, timeCreated2Ms);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(2);
    assertThat(tracker.requiresTracking()).isTrue();
    assertThat(tracker.hasViolation()).isTrue();
    assertThat(tracker.getTrackerName()).isEqualTo("Integer");
    assertThat(tracker.getLatestReferenceRegistrationTimeMs()).isEqualTo(timeCreated2Ms);
  }

  @Test
  public void reset_when2ObjectsAreTrackedButResetIsCalled_ShouldResetEverything() {
    SingleInstanceTracker tracker =
        new SingleInstanceTracker(Integer.class, weakReferenceGenerator, "Integer");
    when(weakIntReference.get()).thenReturn(0);
    when(weakIntReference2.get()).thenReturn(1);
    tracker.track(weakIntReference, timeCreatedMs);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(1);
    tracker.track(weakIntReference2, timeCreated2Ms);
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(2);
    assertThat(tracker.requiresTracking()).isTrue();
    assertThat(tracker.hasViolation()).isFalse();
    tracker.clearTrackingData();
    assertThat(tracker.getCurrentNumInMemoryInstances()).isEqualTo(0);
    assertThat(tracker.requiresTracking()).isFalse();
  }

  private class TestWeakRefGenerator extends WeakReferenceGenerator {

    @NonNull
    @Override
    public synchronized WeakReference<Object> getGenericWeakRef() {
      return weakIntReference3;
    }
  }
}
