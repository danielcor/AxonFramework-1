/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.core.eventhandler;

import org.axonframework.core.Event;
import org.axonframework.core.StubDomainEvent;
import org.junit.*;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventProcessingSchedulerTest {

    private EventProcessingScheduler testSubject;

    @Test
    public void testEventProcessingSchedule_EventBatchRetried() {
        MockEventListener listener = executeEventProcessing(RetryPolicy.RETRY_TRANSACTION);

        // each event is handled twice, since we retry the entire batch
        assertEquals(5, listener.handledEvents.size());
        assertEquals(2, listener.transactionsStarted);
        assertEquals(1, listener.transactionsSucceeded);
        assertEquals(1, listener.transactionsFailed);
    }

    @Test
    public void testEventProcessingSchedule_SingleEventRetried() {
        MockEventListener listener = executeEventProcessing(RetryPolicy.RETRY_LAST_EVENT);

        // each event is handled twice, since we retry the entire batch
        assertEquals(4, listener.handledEvents.size());
        assertEquals(2, listener.transactionsStarted);
        assertEquals(1, listener.transactionsSucceeded);
        assertEquals(1, listener.transactionsFailed);
    }

    @Test
    public void testEventProcessingSchedule_FailedEventIgnored() {
        MockEventListener listener = executeEventProcessing(RetryPolicy.IGNORE_FAILED_TRANSACTION);

        // each event is handled twice, since we retry the entire batch
        assertEquals(3, listener.handledEvents.size());
        assertEquals(2, listener.transactionsStarted);
        assertEquals(1, listener.transactionsSucceeded);
        assertEquals(1, listener.transactionsFailed);
    }

    private MockEventListener executeEventProcessing(RetryPolicy policy) {
        ExecutorService mockExecutorService = mock(ExecutorService.class);
        MockEventListener listener = new MockEventListener(policy);
        testSubject = new EventProcessingScheduler(listener,
                                                   mockExecutorService,
                                                   new NullShutdownCallback());

        doNothing().doThrow(new RejectedExecutionException()).when(mockExecutorService).execute(isA(Runnable.class));
        testSubject.scheduleEvent(new StubDomainEvent());
        listener.failOnEvent = 2;
        testSubject.scheduleEvent(new StubDomainEvent());
        testSubject.scheduleEvent(new StubDomainEvent());

        testSubject.run();
        return listener;
    }

    private class MockEventListener implements EventListener, TransactionAware {

        private int failOnEvent;
        private List<Event> handledEvents = new LinkedList<Event>();
        private RetryPolicy retryPolicy;
        private int transactionsStarted;
        private int transactionsSucceeded;
        private int transactionsFailed;

        public MockEventListener(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
        }

        @Override
        public boolean canHandle(Class<? extends Event> eventType) {
            return true;
        }

        @Override
        public void handle(Event event) {
            handledEvents.add(event);
            if (--failOnEvent == 0) {
                throw new RuntimeException("Mock exception");
            }
        }

        @Override
        public EventSequencingPolicy getEventSequencingPolicy() {
            return new SequentialPolicy();
        }

        @Override
        public void beforeTransaction(TransactionStatus transactionStatus) {
            transactionStatus.setRetryPolicy(retryPolicy);
            transactionsStarted++;
        }

        @Override
        public void afterTransaction(TransactionStatus transactionStatus) {
            assertEquals(failOnEvent != 0, transactionStatus.isSuccessful());
            if (transactionStatus.isSuccessful()) {
                transactionsSucceeded++;
            } else {
                transactionsFailed++;
            }
        }
    }

    private static class NullShutdownCallback implements EventProcessingScheduler.ShutdownCallback {

        @Override
        public void afterShutdown(EventProcessingScheduler scheduler) {
        }
    }
}