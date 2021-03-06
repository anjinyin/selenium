// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.grid.sessionmap.local;

import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.grid.data.Session;
import org.openqa.selenium.grid.sessionmap.SessionMap;
import org.openqa.selenium.remote.SessionId;
import org.openqa.selenium.remote.tracing.DistributedTracer;
import org.openqa.selenium.remote.tracing.Span;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LocalSessionMap extends SessionMap {

  private final DistributedTracer tracer;
  private final Map<SessionId, Session> knownSessions = new HashMap<>();
  private final ReadWriteLock lock = new ReentrantReadWriteLock(/* be fair */ true);

  public LocalSessionMap(DistributedTracer tracer) {
    this.tracer = Objects.requireNonNull(tracer);
  }

  @Override
  public boolean add(Session session) {
    Objects.requireNonNull(session, "Session has not been set");

    try (Span span = tracer.createSpan("sessionmap.add", tracer.getActiveSpan())) {
      span.addTag("session.id", session.getId());
      span.addTag("session.capabilities", session.getCapabilities());
      span.addTag("session.uri", session.getUri());

      Lock writeLock = lock.writeLock();
      writeLock.lock();
      try {
        knownSessions.put(session.getId(), session);
      } finally {
        writeLock.unlock();
      }

      return true;
    }
  }

  @Override
  public Session get(SessionId id) {
    Objects.requireNonNull(id, "Session ID has not been set");

    try (Span span = tracer.createSpan("sessionmap.get", tracer.getActiveSpan())) {
      span.addTag("session.id", id);

      Lock readLock = lock.readLock();
      readLock.lock();
      try {
        Session session = knownSessions.get(id);
        if (session == null) {
          throw new NoSuchSessionException("Unable to find session with ID: " + id);
        }

        span.addTag("session.capabilities", session.getCapabilities());
        span.addTag("session.uri", session.getUri());

        return session;
      } finally {
        readLock.unlock();
      }
    }
  }

  @Override
  public void remove(SessionId id) {
    Objects.requireNonNull(id, "Session ID has not been set");

    try (Span span = tracer.createSpan("sessionmap.remove", tracer.getActiveSpan())) {
      span.addTag("session.id", id);

      Lock writeLock = lock.writeLock();
      writeLock.lock();
      try {
        knownSessions.remove(id);
      } finally {
        writeLock.unlock();
      }
    }
  }
}
