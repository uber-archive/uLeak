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
import static com.uber.uleak.LeakType.LIFECYCLE;
import static com.uber.uleak.LeakType.SINGLETON;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TrackedReferencesTest {

  TrackedReferences references;
  @Mock
  private ActiveTracker.RefGeneratorFactory refGeneratorFactory;

  @SuppressWarnings("NullAway")
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    references = new TrackedReferences();
  }

  @Test
  public void contains_whenMapIsEmpty_shouldReturnFalse() {
    assertThat(references.contains(Integer.class, SINGLETON)).isFalse();
  }

  @Test
  public void contains_whenParticularLeakTypeDoesNotExist_shouldReturnNull() {
    references.getOrCreate(Integer.class, SINGLETON, "Integer", refGeneratorFactory);
    assertThat(references.contains(Integer.class, LIFECYCLE)).isFalse();
  }

  @Test
  public void contains_whenExists_shouldReturnTrue() {
    references.getOrCreate(Integer.class, SINGLETON, "Integer", refGeneratorFactory);
    assertThat(references.contains(Integer.class, SINGLETON)).isTrue();
  }

  @Test
  public void getAllLeakTrackers_whenThereAreTwo_shouldReturnArrayListWithThoseTwoTrackers() {
    references.getOrCreate(Integer.class, SINGLETON, "Integer", refGeneratorFactory);
    references.getOrCreate(String.class, LIFECYCLE, "String", refGeneratorFactory);
    assertThat(references.getAllLeakTrackers().size()).isEqualTo(2);
  }
}
