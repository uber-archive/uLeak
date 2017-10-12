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

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Generates weak references
 */
class WeakReferenceGenerator {

  private static final int NUMBER_ENQUEUED_LONG_LIVED_OBJECTS = 10;

  private final Queue<Object> longLivedObjects = new LinkedList<>();

  WeakReferenceGenerator() {
    for (int i = 0; i < NUMBER_ENQUEUED_LONG_LIVED_OBJECTS; i++) {
      longLivedObjects.add(new Object());
    }
  }

  protected synchronized WeakReference<Object> getGenericWeakRef() {
    longLivedObjects.add(new Object());
    return new WeakReference<>(longLivedObjects.remove());
  }
}
