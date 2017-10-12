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

/**
 * The reporting interface for uLeaks reporter. This is used so we don't have any reporting
 * dependencies in uLeak.
 */
public interface ULeakEventReporter {

  /**
   * Report a particular event to the reporter.
   *
   * @param event the event to be reported.
   */
  void reportEvent(ULeakEvent event);
}
