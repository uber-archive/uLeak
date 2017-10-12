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
import static com.uber.uleak.LeakType.SINGLETON;

import android.annotation.SuppressLint;
import android.support.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

/**
 * This class is the public interface to ULeak. ULeak is the uber leak reference memory leak
 * detector. Currently it checks only singleton instance (in memory) that may be leaking.
 */
public class ULeak {

  private static final ConcurrentLinkedQueue<QueuedLeakEvent> PREINITIALIZATION_EVENTS =
      new ConcurrentLinkedQueue<>();
  private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
  private static final PublishSubject<Throwable> ERROR_RELAY = PublishSubject.create();

  @Nullable
  private static LeakInstanceTracker trackerSingleton = null;

  private ULeak() {
  }

  /**
   * Allows at most one instances of the class to exist. Note this method may NoOp if the Uleak is
   * initialized with the NoOp instance.
   *
   * @param instance the newly created instance for the class.
   * @param className the class name of the object instance.
   */
  @SuppressLint("DontUseSystemTime")
  @SuppressWarnings("SystemCurrentTimeMillis")
  public static synchronized void allowAtMostOne(Object instance, String className) {
    if (trackerSingleton == null) {
      PREINITIALIZATION_EVENTS.add(
          new QueuedLeakEvent(
              new WeakReference<>(instance), className, System.currentTimeMillis()));
      return;
    }
    trackerSingleton.registerNewReference(
        instance, className, System.currentTimeMillis(), SINGLETON);
  }

  /**
   * Tracks the object passed in. Should be called at the end of the lifecycle of an object. If
   * ULeak is uninitialized, this will NoOp.
   *
   * @param instance the instance of the class to watch.
   * @param className the name of the class of the object.
   */
  @SuppressLint("DontUseSystemTime")
  @SuppressWarnings("SystemCurrentTimeMillis")
  public static synchronized void watchReference(final Object instance, String className) {
    if (trackerSingleton == null) {
      // We don't expect any RIBs to detach before the creation of the trackerSingleton.
      return;
    } else {
      trackerSingleton.registerNewReference(
          instance, className, System.currentTimeMillis(), LIFECYCLE);
    }
  }

  /**
   * @return returns the error observable that all ULeak errors are posted to.
   */
  public static Observable<Throwable> getErrorObservable() {
    return ERROR_RELAY;
  }

  /**
   * This is the initialize method that needs to be called at some point in the application
   * lifecycle. Up till this point all the leak events will be queued up and not consumed by any
   * concrete {@link LeakInstanceTracker}.
   *
   * @param type The type of the uleak tracker. In this case it will be a NoOp or active one.
   * @param reporter the reporter that is used by the instance to report leaks.
   * @param configuration the uleak configuration.
   */
  public static synchronized void initialize(
      final Type type, final ULeakEventReporter reporter, ULeakConfiguration configuration) {
    if (INITIALIZED.getAndSet(true)) {
      return;
    }
    switch (type) {
      case ACTIVE_REPORTING:
        trackerSingleton = new ActiveTracker(reporter, configuration, ERROR_RELAY);
        break;
      case NO_OP:
      default:
        trackerSingleton = new NoOpTracker();
        break;
    }
    trackerSingleton.flushAllQueuedReferences(PREINITIALIZATION_EVENTS);
  }

  /**
   * Helper to select the type of instance tracker to create. We don't want library:app-memory to
   * depend on cached experiments so give the callers a way to initialize this stuff
   */
  public enum Type {
    // would create a no-op instance counter
    // Instance Class: NoOpTracker
    NO_OP,

    // the instance counter would report violations to the backend via UR
    // Caveat: wouldn't catch transient violations that happen before initialize
    // Instance Class: ActiveTracker
    ACTIVE_REPORTING,
  }

  static class QueuedLeakEvent {

    final WeakReference<Object> weakReference;
    final String className;
    final long timeEventRegisteredMs;

    QueuedLeakEvent(
        WeakReference<Object> weakReference, String className, final long timeEventRegisteredMs) {
      this.weakReference = weakReference;
      this.className = className;
      this.timeEventRegisteredMs = timeEventRegisteredMs;
    }
  }
}
