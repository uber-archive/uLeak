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

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.operators.observable.ObservableInterval;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

class ActiveTracker implements LeakInstanceTracker {

  @VisibleForTesting
  static final String CLASS_DIMENSION_KEY_NAME = "class_name";
  @VisibleForTesting
  static final String GC_TYPE_DIMENSION_KEY_NAME = "gcType";
  @VisibleForTesting
  static final String INSTANCES_COUNT_METRIC_KEY_NAME = "numInstances";
  @VisibleForTesting
  static final String LAST_LEAK_CREATED_TIME_MS = "leakCreatedTimeMs";
  @VisibleForTesting
  static final String LEAK_TYPE_KEY_NAME = "leakType";
  private final ULeakEventReporter reporter;
  private final ObservableInterval observableInterval;
  private final GcCallWrapper gcCallWrapper;
  private final AtomicBoolean explicitGcCalledLastIteration = new AtomicBoolean(false);
  private final ULeakConfiguration configuration;
  private final Random random;
  private final PublishSubject<Throwable> errorRelay;
  private final TrackedReferences trackedReferences = new TrackedReferences();
  @Nullable
  private Disposable disposable;
  private final RefGeneratorFactory refGeneratorFactory;

  ActiveTracker(
      ULeakEventReporter reporter,
      ULeakConfiguration configuration,
      PublishSubject<Throwable> errorRelay) {
    this(
        reporter,
        configuration,
        new GcCallWrapper(),
        new RefGeneratorFactory(),
        new Random(),
        errorRelay);
  }

  @VisibleForTesting
  ActiveTracker(
      ULeakEventReporter reporter,
      ULeakConfiguration configuration,
      GcCallWrapper gcCallWrapper,
      RefGeneratorFactory refGeneratorFactory,
      Random random,
      PublishSubject<Throwable> errorRelay) {
    this.errorRelay = errorRelay;
    this.reporter = reporter;
    long leakCheckFrequencyMillis = configuration.leakCheckFrequencyMs;
    observableInterval =
        new ObservableInterval(
            leakCheckFrequencyMillis,
            leakCheckFrequencyMillis,
            TimeUnit.MILLISECONDS,
            Schedulers.io());
    this.configuration = configuration;
    this.gcCallWrapper = gcCallWrapper;
    this.random = random;
    this.refGeneratorFactory = refGeneratorFactory;
  }

  @Override
  public void flushAllQueuedReferences(ConcurrentLinkedQueue<ULeak.QueuedLeakEvent> events) {
    ULeak.QueuedLeakEvent queuedLeakEvent;
    boolean shouldStartTracking = false;
    synchronized (trackedReferences) {
      while ((queuedLeakEvent = events.poll()) != null) {
        Object object = queuedLeakEvent.weakReference.get();
        if (object == null) {
          continue;
        }
        LeakTracker tracker =
            trackedReferences.getOrCreate(
                object.getClass(), SINGLETON, queuedLeakEvent.className, refGeneratorFactory);
        if (tracker == null) {
          continue;
        }
        tracker.track(queuedLeakEvent.weakReference, queuedLeakEvent.timeEventRegisteredMs);
        shouldStartTracking = shouldStartTracking || tracker.requiresTracking();
      }
    }
    if (shouldStartTracking && disposable == null) {
      startTracking();
    }
  }

  @Override
  public void registerNewReference(
      Object object, String className, final long timeCreatedMs, final LeakType leakType) {
    Class<?> clazz = object.getClass();
    LeakTracker tracker;
    synchronized (trackedReferences) {
      tracker = trackedReferences.getOrCreate(clazz, leakType, className, refGeneratorFactory);
      if (tracker == null) {
        return;
      }
      tracker.track(new WeakReference<>(object), timeCreatedMs);
      if (tracker.requiresTracking() && disposable == null) {
        startTracking();
      }
    }
  }

  private void startTracking() {
    synchronized (observableInterval) {
      if (disposable != null) {
        return;
      }
      disposable =
          observableInterval.subscribe(
              new Consumer<Long>() {
                @Override
                public void accept(Long aLong) throws Exception {
                  boolean useExplicitGcThisRound =
                      configuration.shouldUseExplicitGc()
                          && random.nextDouble() < configuration.explicitGCProbability;
                  boolean continueTracking = false;
                  boolean callExplicitGC = false;
                  synchronized (trackedReferences) {
                    for (LeakTracker tracker : trackedReferences.getAllLeakTrackers()) {
                      if (tracker.hasViolation()) {
                        reporter.reportEvent(createReportingEvent(tracker));
                        if (useExplicitGcThisRound && !explicitGcCalledLastIteration.get()) {
                          // try again with an explicit gc call.
                          callExplicitGC = true;
                          continueTracking = true;
                          tracker.resetWeakReference();
                        } else {
                          tracker.clearTrackingData();
                        }
                      }
                      continueTracking = continueTracking || tracker.requiresTracking();
                    }
                  }
                  if (!continueTracking) {
                    // stop the interval if there isn't anything to track.
                    synchronized (observableInterval) {
                      if (disposable != null) {
                        disposable.dispose();
                        disposable = null;
                      }
                    }
                    return;
                  }
                  if (callExplicitGC) {
                    gcCallWrapper.callGC();
                    explicitGcCalledLastIteration.set(true);
                  } else {
                    explicitGcCalledLastIteration.set(false);
                  }
                }
              },
              new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) throws Exception {
                  explicitGcCalledLastIteration.set(false);
                  errorRelay.onNext(throwable);
                }
              });
    }
  }

  /**
   * prepare the violation event that is to be reported to the backend
   *
   * @param tracker the SingleInstanceTracker that observed a violation
   * @return the UR event
   */
  private ULeakEvent createReportingEvent(final LeakTracker tracker) {
    ULeakEvent reporterEvent = new ULeakEvent();
    reporterEvent.addDimension(CLASS_DIMENSION_KEY_NAME, tracker.getTrackerName());
    reporterEvent.addDimension(
        GC_TYPE_DIMENSION_KEY_NAME,
        explicitGcCalledLastIteration.get()
            ? ViolationType.EXPLICIT_GC_VIOLATION.name()
            : ViolationType.IMPLICIT_GC_VIOLATION.name());
    reporterEvent.addDimension(LEAK_TYPE_KEY_NAME, tracker.getLeakType().name());
    reporterEvent.addMetric(
        INSTANCES_COUNT_METRIC_KEY_NAME, tracker.getCurrentNumInMemoryInstances());
    reporterEvent.addMetric(
        LAST_LEAK_CREATED_TIME_MS, tracker.getLatestReferenceRegistrationTimeMs());
    return reporterEvent;
  }

  @VisibleForTesting
  enum ViolationType {
    EXPLICIT_GC_VIOLATION,
    IMPLICIT_GC_VIOLATION
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  static class RefGeneratorFactory {

    WeakReferenceGenerator createGenerator() {
      return new WeakReferenceGenerator();
    }
  }
}
