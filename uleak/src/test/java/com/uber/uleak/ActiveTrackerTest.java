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
import static com.uber.uleak.ActiveTracker.CLASS_DIMENSION_KEY_NAME;
import static com.uber.uleak.ActiveTracker.GC_TYPE_DIMENSION_KEY_NAME;
import static com.uber.uleak.ActiveTracker.INSTANCES_COUNT_METRIC_KEY_NAME;
import static com.uber.uleak.ActiveTracker.LAST_LEAK_CREATED_TIME_MS;
import static com.uber.uleak.ActiveTracker.LEAK_TYPE_KEY_NAME;
import static com.uber.uleak.ActiveTracker.ViolationType.EXPLICIT_GC_VIOLATION;
import static com.uber.uleak.ActiveTracker.ViolationType.IMPLICIT_GC_VIOLATION;
import static com.uber.uleak.LeakType.LIFECYCLE;
import static com.uber.uleak.LeakType.SINGLETON;
import static com.uber.uleak.ULeakConfiguration.MINIMUM_CHECK_FREQUENCY_MS;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.support.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import io.reactivex.Scheduler;
import io.reactivex.functions.Function;
import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.PublishSubject;

public class ActiveTrackerTest {

  @Mock
  private ULeakEventReporter uLeakEventReporter;
  @Mock
  private GcCallWrapper gcCallWrapper;
  @Mock
  private Random random;
  @Mock
  private WeakReference<Object> genericWeakRef1;
  @Mock
  private WeakReference<Object> genericWeakRef2;
  @Mock
  private ActiveTracker.RefGeneratorFactory testRefGeneratorFactory;
  @Mock
  private WeakReferenceGenerator weakReferenceGenerator;

  private PublishSubject<Throwable> errorRelay;
  private TestScheduler scheduler;
  private ActiveTracker activeTracker;
  private TestObserver<Throwable> testObserver;
  private long createdTimeMs1 = 1234L;
  private long createdTimeMs2 = 4321L;
  private long createdTimeMs3 = 4322L;
  private String className = "Integer";

  @SuppressWarnings("NullAway")
  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    scheduler = new TestScheduler();
    RxJavaPlugins.setIoSchedulerHandler(new Function<Scheduler, Scheduler>() {
      @Override
      public Scheduler apply(Scheduler scheduler) throws Exception {
        return scheduler;
      }
    });
    errorRelay = PublishSubject.create();
    testObserver = TestObserver.create();
    errorRelay.subscribe(testObserver);
  }

  @After
  public void tearDown() {
    RxJavaPlugins.reset();
  }

  @Test
  public void flushQueue_whenWeakReferenceReturnsNull_shouldDoNothing() {
    createTracker(0.0);
    ConcurrentLinkedQueue<ULeak.QueuedLeakEvent> queue = new ConcurrentLinkedQueue<>();
    queue.add(new ULeak.QueuedLeakEvent(genericWeakRef1, className, createdTimeMs1));
    activeTracker.flushAllQueuedReferences(queue);
    scheduler.triggerActions();
    verify(uLeakEventReporter, never()).reportEvent(any(ULeakEvent.class));
    verify(gcCallWrapper, never()).callGC();
    verify(random, never()).nextDouble();
    verify(genericWeakRef1).get();
  }

  @Test
  public void flushQueue_whenWeakReferenceReturnsObject_shouldStillDoNothing() {
    Integer ref = 0;
    WeakReference<Object> weakReference = new WeakReference<>((Object) ref);
    createTracker(0.0);
    ConcurrentLinkedQueue<ULeak.QueuedLeakEvent> queue = new ConcurrentLinkedQueue<>();
    queue.add(new ULeak.QueuedLeakEvent(weakReference, className, createdTimeMs1));

    activeTracker.flushAllQueuedReferences(queue);
    scheduler.triggerActions();
    verify(uLeakEventReporter, never()).reportEvent(any(ULeakEvent.class));
    verify(gcCallWrapper, never()).callGC();
    verify(random, never()).nextDouble();
  }

  @Test
  public void flushQueue_whenWeak2WeakReferencesSameClass_shouldStartTrackingAndCauseReport() {
    Integer ref = 0;
    Integer ref2 = 1;
    WeakReference<Object> weakReference = new WeakReference<>((Object) ref);
    WeakReference<Object> weakReference2 = new WeakReference<>((Object) ref2);
    createTracker(0.0);
    ConcurrentLinkedQueue<ULeak.QueuedLeakEvent> queue = new ConcurrentLinkedQueue<>();
    queue.add(new ULeak.QueuedLeakEvent(weakReference, className, createdTimeMs1));
    queue.add(new ULeak.QueuedLeakEvent(weakReference2, className, createdTimeMs2));

    when(testRefGeneratorFactory.createGenerator()).thenReturn(weakReferenceGenerator);
    when(weakReferenceGenerator.getGenericWeakRef()).thenReturn(this.genericWeakRef1);

    activeTracker.flushAllQueuedReferences(queue);
    verify(weakReferenceGenerator).getGenericWeakRef();

    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);

    ArgumentCaptor<ULeakEvent> captor = ArgumentCaptor.forClass(ULeakEvent.class);
    verify(uLeakEventReporter).reportEvent(captor.capture());
    ULeakEvent uLeakEvent = captor.getValue();
    assertReporterEvent(uLeakEvent, IMPLICIT_GC_VIOLATION, 2, createdTimeMs2, SINGLETON);
    verify(gcCallWrapper, never()).callGC();
    verify(random, never()).nextDouble();
    testObserver.assertNoValues();
  }

  @Test
  public void flushQueue_when3WeakReferencesSameClass_shouldStartTrackingAndCauseReport() {
    Integer ref = 0;
    Integer ref2 = 1;
    Integer ref3 = 2;
    WeakReference<Object> weakReference = new WeakReference<>((Object) ref);
    WeakReference<Object> weakReference2 = new WeakReference<>((Object) ref2);
    WeakReference<Object> weakReference3 = new WeakReference<>((Object) ref3);
    createTracker(0.0);
    ConcurrentLinkedQueue<ULeak.QueuedLeakEvent> queue = new ConcurrentLinkedQueue<>();
    queue.add(new ULeak.QueuedLeakEvent(weakReference, className, createdTimeMs1));
    queue.add(new ULeak.QueuedLeakEvent(weakReference2, className, createdTimeMs2));
    long createdTimeMs3 = 9999L;
    queue.add(new ULeak.QueuedLeakEvent(weakReference3, className, createdTimeMs3));

    when(testRefGeneratorFactory.createGenerator()).thenReturn(weakReferenceGenerator);
    when(weakReferenceGenerator.getGenericWeakRef()).thenReturn(this.genericWeakRef1);

    activeTracker.flushAllQueuedReferences(queue);
    verify(weakReferenceGenerator, times(2)).getGenericWeakRef();

    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);

    ArgumentCaptor<ULeakEvent> captor = ArgumentCaptor.forClass(ULeakEvent.class);
    verify(uLeakEventReporter).reportEvent(captor.capture());
    ULeakEvent uLeakEvent = captor.getValue();
    assertReporterEvent(uLeakEvent, IMPLICIT_GC_VIOLATION, 3, createdTimeMs3, SINGLETON);
    verify(gcCallWrapper, never()).callGC();
    verify(random, never()).nextDouble();
    testObserver.assertNoValues();
  }

  @Test
  public void
  registerNewReference_whenWeak2SingletonWeakReferencesSameClass_shouldStartTrackingAndCauseReport() {
    Integer ref = 0;
    WeakReference<Object> weakReference = new WeakReference<>((Object) ref);
    createTracker(0.0);
    ConcurrentLinkedQueue<ULeak.QueuedLeakEvent> queue = new ConcurrentLinkedQueue<>();
    queue.add(new ULeak.QueuedLeakEvent(weakReference, className, createdTimeMs1));

    when(testRefGeneratorFactory.createGenerator()).thenReturn(weakReferenceGenerator);
    when(weakReferenceGenerator.getGenericWeakRef()).thenReturn(this.genericWeakRef1);

    activeTracker.flushAllQueuedReferences(queue);
    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);

    verify(uLeakEventReporter, never()).reportEvent(any(ULeakEvent.class));
    verify(gcCallWrapper, never()).callGC();
    verify(random, never()).nextDouble();
    verify(weakReferenceGenerator, never()).getGenericWeakRef();
    testObserver.assertNoValues();

    Integer ref2 = 1;
    activeTracker.registerNewReference(ref2, className, createdTimeMs2, SINGLETON);

    verify(weakReferenceGenerator).getGenericWeakRef();

    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);
    ArgumentCaptor<ULeakEvent> captor = ArgumentCaptor.forClass(ULeakEvent.class);
    verify(uLeakEventReporter).reportEvent(captor.capture());
    ULeakEvent uLeakEvent = captor.getValue();
    assertReporterEvent(uLeakEvent, IMPLICIT_GC_VIOLATION, 2, createdTimeMs2, SINGLETON);
  }

  @Test
  public void
  registerNewReference_whenWeak2SingletonWeakReferencesSameClassExplicitGC_shouldStartTrackingAndCauseReport() {
    Integer ref = 0;
    WeakReference<Object> weakReference = new WeakReference<>((Object) ref);
    createTracker(1.0);
    ConcurrentLinkedQueue<ULeak.QueuedLeakEvent> queue = new ConcurrentLinkedQueue<>();
    queue.add(new ULeak.QueuedLeakEvent(weakReference, className, createdTimeMs1));

    when(testRefGeneratorFactory.createGenerator()).thenReturn(weakReferenceGenerator);
    when(weakReferenceGenerator.getGenericWeakRef()).thenReturn(this.genericWeakRef1);
    when(random.nextDouble()).thenReturn(.5);

    activeTracker.flushAllQueuedReferences(queue);
    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);

    verify(uLeakEventReporter, never()).reportEvent(any(ULeakEvent.class));
    verify(gcCallWrapper, never()).callGC();
    verify(random, never()).nextDouble();
    verify(weakReferenceGenerator, never()).getGenericWeakRef();
    testObserver.assertNoValues();

    Integer ref2 = 1;
    activeTracker.registerNewReference(ref2, className, createdTimeMs2, SINGLETON);

    verify(weakReferenceGenerator).getGenericWeakRef();

    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);
    ArgumentCaptor<ULeakEvent> captor = ArgumentCaptor.forClass(ULeakEvent.class);
    verify(uLeakEventReporter).reportEvent(captor.capture());
    ULeakEvent uLeakEvent = captor.getValue();
    assertReporterEvent(uLeakEvent, IMPLICIT_GC_VIOLATION, 2, createdTimeMs2, SINGLETON);
    verify(gcCallWrapper).callGC();
    verify(random).nextDouble();
    testObserver.assertNoValues();

    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);
    verify(uLeakEventReporter, times(2)).reportEvent(captor.capture());
    uLeakEvent = captor.getValue();
    assertReporterEvent(uLeakEvent, EXPLICIT_GC_VIOLATION, 2, createdTimeMs2, SINGLETON);
    verify(gcCallWrapper).callGC();
    verify(random, times(2)).nextDouble();
    testObserver.assertNoValues();
    // Check to make sure nothing else happens.
    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);
    verify(uLeakEventReporter, times(2)).reportEvent(any(ULeakEvent.class));
    verify(gcCallWrapper).callGC();
    verify(random, times(2)).nextDouble();
    testObserver.assertNoValues();
  }

  @Test
  public void registerNewReference_whenALifecycleWeakReference_shouldStartTrackingAndCauseReport() {
    createTracker(0.0);

    when(testRefGeneratorFactory.createGenerator()).thenReturn(weakReferenceGenerator);
    when(weakReferenceGenerator.getGenericWeakRef()).thenReturn(this.genericWeakRef1);

    verify(uLeakEventReporter, never()).reportEvent(any(ULeakEvent.class));
    verify(gcCallWrapper, never()).callGC();
    verify(random, never()).nextDouble();
    verify(weakReferenceGenerator, never()).getGenericWeakRef();
    testObserver.assertNoValues();

    Integer ref1 = 1;
    activeTracker.registerNewReference(ref1, className, createdTimeMs2, LIFECYCLE);

    verify(weakReferenceGenerator).getGenericWeakRef();

    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);
    ArgumentCaptor<ULeakEvent> captor = ArgumentCaptor.forClass(ULeakEvent.class);
    verify(uLeakEventReporter).reportEvent(captor.capture());
    ULeakEvent uLeakEvent = captor.getValue();
    assertReporterEvent(uLeakEvent, IMPLICIT_GC_VIOLATION, 1, createdTimeMs2, LIFECYCLE);
  }

  @Test
  public void
  registerNewReference_whenALifecycleWeakReferenceExplicitGC_shouldStartTrackingAndCauseReport() {
    createTracker(1.0);

    when(testRefGeneratorFactory.createGenerator()).thenReturn(weakReferenceGenerator);
    when(weakReferenceGenerator.getGenericWeakRef()).thenReturn(this.genericWeakRef1);
    when(random.nextDouble()).thenReturn(.5);

    verify(uLeakEventReporter, never()).reportEvent(any(ULeakEvent.class));
    verify(gcCallWrapper, never()).callGC();
    verify(random, never()).nextDouble();
    verify(weakReferenceGenerator, never()).getGenericWeakRef();
    testObserver.assertNoValues();

    Integer ref1 = 1;
    activeTracker.registerNewReference(ref1, className, createdTimeMs2, LIFECYCLE);

    verify(weakReferenceGenerator).getGenericWeakRef();

    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);
    ArgumentCaptor<ULeakEvent> captor = ArgumentCaptor.forClass(ULeakEvent.class);
    verify(uLeakEventReporter).reportEvent(captor.capture());
    ULeakEvent uLeakEvent = captor.getValue();
    assertReporterEvent(uLeakEvent, IMPLICIT_GC_VIOLATION, 1, createdTimeMs2, LIFECYCLE);
    verify(gcCallWrapper).callGC();
    verify(random).nextDouble();
    testObserver.assertNoValues();

    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);
    verify(uLeakEventReporter, times(2)).reportEvent(captor.capture());
    uLeakEvent = captor.getValue();
    assertReporterEvent(uLeakEvent, EXPLICIT_GC_VIOLATION, 1, createdTimeMs2, LIFECYCLE);
    verify(gcCallWrapper).callGC();
    verify(random, times(2)).nextDouble();
    testObserver.assertNoValues();
    // Check to make sure nothing else happens.
    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);
    verify(uLeakEventReporter, times(2)).reportEvent(any(ULeakEvent.class));
    verify(gcCallWrapper).callGC();
    verify(random, times(2)).nextDouble();
    testObserver.assertNoValues();
  }

  @Test
  public void
  registerNewReference_when2SingletonAndALifecycleWeakReference_shouldTrackAndCause2Reports() {
    Integer ref = 0;
    WeakReference<Object> weakReference = new WeakReference<>((Object) ref);
    createTracker(0.0);
    ConcurrentLinkedQueue<ULeak.QueuedLeakEvent> queue = new ConcurrentLinkedQueue<>();
    queue.add(new ULeak.QueuedLeakEvent(weakReference, className, createdTimeMs1));

    when(testRefGeneratorFactory.createGenerator()).thenReturn(weakReferenceGenerator);
    when(weakReferenceGenerator.getGenericWeakRef()).thenReturn(this.genericWeakRef1);

    activeTracker.flushAllQueuedReferences(queue);
    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);

    verify(uLeakEventReporter, never()).reportEvent(any(ULeakEvent.class));
    verify(gcCallWrapper, never()).callGC();
    verify(random, never()).nextDouble();
    verify(weakReferenceGenerator, never()).getGenericWeakRef();
    testObserver.assertNoValues();

    Integer ref2 = 1;
    activeTracker.registerNewReference(ref2, className, createdTimeMs2, SINGLETON);
    Integer ref3 = 2;
    activeTracker.registerNewReference(ref3, className, createdTimeMs3, LIFECYCLE);

    verify(weakReferenceGenerator, times(2)).getGenericWeakRef();

    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);
    ArgumentCaptor<ULeakEvent> captor = ArgumentCaptor.forClass(ULeakEvent.class);
    verify(uLeakEventReporter, times(2)).reportEvent(captor.capture());
    assertThat(captor.getAllValues().size()).isEqualTo(2);
    List<Long> creationTimes = new ArrayList<>(2);
    creationTimes.add(createdTimeMs2);
    creationTimes.add(createdTimeMs3);
    List<Integer> instances = new ArrayList<>(2);
    instances.add(1);
    instances.add(2);
    List<String> leakTypes = new ArrayList<>(2);
    leakTypes.add(SINGLETON.name());
    leakTypes.add(LIFECYCLE.name());

    List<String> ensureDifferentViolations = new ArrayList<>(2);
    ensureDifferentViolations.add(
        captor.getAllValues().get(0).getDimensions().get(LEAK_TYPE_KEY_NAME));
    ensureDifferentViolations.add(
        captor.getAllValues().get(1).getDimensions().get(LEAK_TYPE_KEY_NAME));

    // additional assertions to verify that we are getting both the different type of violations,
    // not two copies
    // of the same, so that the assertions below actually test the right thing.
    assertThat(ensureDifferentViolations).contains(SINGLETON.name());
    assertThat(ensureDifferentViolations).contains(LIFECYCLE.name());

    assertMultipleReporterEvents(
        captor.getAllValues().get(0), IMPLICIT_GC_VIOLATION, instances, creationTimes, leakTypes);
    assertMultipleReporterEvents(
        captor.getAllValues().get(1), IMPLICIT_GC_VIOLATION, instances, creationTimes, leakTypes);
  }

  @Test
  public void
  flushQueue_whenWeak2WeakReferencesSameClassOneReferenceIsCollected_shouldStartTrackingButNoReporting() {
    Integer ref = 0;
    WeakReference<Object> weakReference = new WeakReference<>((Object) ref);
    createTracker(0.0);
    ConcurrentLinkedQueue<ULeak.QueuedLeakEvent> queue = new ConcurrentLinkedQueue<>();
    queue.add(new ULeak.QueuedLeakEvent(weakReference, className, createdTimeMs1));
    queue.add(new ULeak.QueuedLeakEvent(genericWeakRef2, className, createdTimeMs2));

    when(genericWeakRef2.get()).thenReturn(1).thenReturn(1).thenReturn(1).thenReturn(null);

    when(testRefGeneratorFactory.createGenerator()).thenReturn(weakReferenceGenerator);
    when(weakReferenceGenerator.getGenericWeakRef()).thenReturn(this.genericWeakRef2);

    activeTracker.flushAllQueuedReferences(queue);
    verify(weakReferenceGenerator).getGenericWeakRef();
    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);

    verify(uLeakEventReporter, never()).reportEvent(any(ULeakEvent.class));
    verify(gcCallWrapper, never()).callGC();
    verify(random, never()).nextDouble();
    testObserver.assertNoValues();
  }

  @Test
  public void
  flushQueue_whenWeak2WeakReferencesSameClassAndExplicitGc_shouldStartTrackingAndCauseReport() {
    Integer ref = 0;
    Integer ref2 = 1;
    WeakReference<Object> weakReference = new WeakReference<>((Object) ref);
    WeakReference<Object> weakReference2 = new WeakReference<>((Object) ref2);
    createTracker(1.0);
    ConcurrentLinkedQueue<ULeak.QueuedLeakEvent> queue = new ConcurrentLinkedQueue<>();
    queue.add(new ULeak.QueuedLeakEvent(weakReference, className, createdTimeMs1));
    queue.add(new ULeak.QueuedLeakEvent(weakReference2, className, createdTimeMs2));

    when(testRefGeneratorFactory.createGenerator()).thenReturn(weakReferenceGenerator);
    when(weakReferenceGenerator.getGenericWeakRef()).thenReturn(this.genericWeakRef1);
    when(random.nextDouble()).thenReturn(.5);

    activeTracker.flushAllQueuedReferences(queue);
    verify(weakReferenceGenerator).getGenericWeakRef();

    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);

    verify(testRefGeneratorFactory).createGenerator();
    ArgumentCaptor<ULeakEvent> captor = ArgumentCaptor.forClass(ULeakEvent.class);
    verify(uLeakEventReporter).reportEvent(captor.capture());
    ULeakEvent uLeakEvent = captor.getValue();
    assertReporterEvent(uLeakEvent, IMPLICIT_GC_VIOLATION, 2, createdTimeMs2, SINGLETON);
    verify(gcCallWrapper).callGC();
    verify(random).nextDouble();
    testObserver.assertNoValues();
    verify(weakReferenceGenerator, times(2)).getGenericWeakRef();

    scheduler.advanceTimeBy(MINIMUM_CHECK_FREQUENCY_MS, TimeUnit.MILLISECONDS);
    verify(uLeakEventReporter, times(2)).reportEvent(captor.capture());
    uLeakEvent = captor.getValue();
    assertReporterEvent(uLeakEvent, EXPLICIT_GC_VIOLATION, 2, createdTimeMs2, SINGLETON);
    verify(gcCallWrapper).callGC();
    verify(random, times(2)).nextDouble();
    testObserver.assertNoValues();
    verify(weakReferenceGenerator, times(2)).getGenericWeakRef();
  }

  private void assertReporterEvent(
      @NonNull ULeakEvent uLeakEvent,
      @NonNull ActiveTracker.ViolationType type,
      final int instances,
      final long lastCreatedReferenceTimeMs,
      @NonNull final LeakType leakType) {
    assertThat(uLeakEvent.getDimensions()).hasSize(3);
    assertThat(uLeakEvent.getDimensions().containsKey(CLASS_DIMENSION_KEY_NAME)).isTrue();
    assertThat(uLeakEvent.getDimensions().get(CLASS_DIMENSION_KEY_NAME)).isEqualTo(className);
    assertThat(uLeakEvent.getDimensions().containsKey(GC_TYPE_DIMENSION_KEY_NAME)).isTrue();
    assertThat(uLeakEvent.getDimensions().get(GC_TYPE_DIMENSION_KEY_NAME)).isEqualTo(type.name());
    assertThat(uLeakEvent.getDimensions().containsKey(LEAK_TYPE_KEY_NAME)).isTrue();
    assertThat(uLeakEvent.getDimensions().get(LEAK_TYPE_KEY_NAME)).isEqualTo(leakType.name());
    assertThat(uLeakEvent.getMetrics()).hasSize(2);
    assertThat(uLeakEvent.getMetrics().containsKey(INSTANCES_COUNT_METRIC_KEY_NAME)).isTrue();
    assertThat(uLeakEvent.getMetrics().get(INSTANCES_COUNT_METRIC_KEY_NAME)).isEqualTo(instances);
    assertThat(uLeakEvent.getMetrics().containsKey(LAST_LEAK_CREATED_TIME_MS)).isTrue();
    assertThat(uLeakEvent.getMetrics().get(LAST_LEAK_CREATED_TIME_MS))
        .isEqualTo(lastCreatedReferenceTimeMs);
  }

  private void assertMultipleReporterEvents(
      @NonNull ULeakEvent uLeakEvent,
      @NonNull ActiveTracker.ViolationType type,
      final List<Integer> instances,
      final List<Long> lastCreatedReferenceTimeMs,
      @NonNull final List<String> leakType) {
    assertThat(uLeakEvent.getDimensions()).hasSize(3);
    assertThat(uLeakEvent.getDimensions().containsKey(CLASS_DIMENSION_KEY_NAME)).isTrue();
    assertThat(uLeakEvent.getDimensions().get(CLASS_DIMENSION_KEY_NAME)).isEqualTo(className);
    assertThat(uLeakEvent.getDimensions().containsKey(GC_TYPE_DIMENSION_KEY_NAME)).isTrue();
    assertThat(uLeakEvent.getDimensions().get(GC_TYPE_DIMENSION_KEY_NAME)).isEqualTo(type.name());
    assertThat(uLeakEvent.getDimensions().containsKey(LEAK_TYPE_KEY_NAME)).isTrue();
    assertThat(uLeakEvent.getDimensions().get(LEAK_TYPE_KEY_NAME)).isIn(leakType);
    assertThat(uLeakEvent.getMetrics()).hasSize(2);
    assertThat(uLeakEvent.getMetrics().containsKey(INSTANCES_COUNT_METRIC_KEY_NAME)).isTrue();
    assertThat(uLeakEvent.getMetrics().get(INSTANCES_COUNT_METRIC_KEY_NAME)).isIn(instances);
    assertThat(uLeakEvent.getMetrics().containsKey(LAST_LEAK_CREATED_TIME_MS)).isTrue();
    assertThat(uLeakEvent.getMetrics().get(LAST_LEAK_CREATED_TIME_MS))
        .isIn(lastCreatedReferenceTimeMs);
  }

  private void createTracker(double gcProbability) {
    ULeakConfiguration configuration =
        new ULeakConfiguration(gcProbability, MINIMUM_CHECK_FREQUENCY_MS);
    activeTracker =
        new ActiveTracker(
            uLeakEventReporter,
            configuration,
            gcCallWrapper,
            testRefGeneratorFactory,
            random,
            errorRelay);
  }
}
