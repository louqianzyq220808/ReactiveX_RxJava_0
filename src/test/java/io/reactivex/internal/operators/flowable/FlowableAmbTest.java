/**
 * Copyright 2016 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.internal.operators.flowable;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.*;
import org.mockito.InOrder;
import org.reactivestreams.*;

import io.reactivex.*;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.*;
import io.reactivex.subscribers.*;

public class FlowableAmbTest {

    private TestScheduler scheduler;
    private Scheduler.Worker innerScheduler;

    @Before
    public void setUp() {
        scheduler = new TestScheduler();
        innerScheduler = scheduler.createWorker();
    }

    private Flowable<String> createObservable(final String[] values,
            final long interval, final Throwable e) {
        return Flowable.create(new Publisher<String>() {

            @Override
            public void subscribe(final Subscriber<? super String> subscriber) {
                final CompositeDisposable parentSubscription = new CompositeDisposable();
                
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        
                    }
                    
                    @Override
                    public void cancel() {
                        parentSubscription.dispose();
                    }
                });
                
                long delay = interval;
                for (final String value : values) {
                    parentSubscription.add(innerScheduler.schedule(new Runnable() {
                        @Override
                        public void run() {
                            subscriber.onNext(value);
                        }
                    }
                    , delay, TimeUnit.MILLISECONDS));
                    delay += interval;
                }
                parentSubscription.add(innerScheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                            if (e == null) {
                                subscriber.onComplete();
                            } else {
                                subscriber.onError(e);
                            }
                    }
                }, delay, TimeUnit.MILLISECONDS));
            }
        });
    }

    @Test
    public void testAmb() {
        Flowable<String> observable1 = createObservable(new String[] {
                "1", "11", "111", "1111" }, 2000, null);
        Flowable<String> observable2 = createObservable(new String[] {
                "2", "22", "222", "2222" }, 1000, null);
        Flowable<String> observable3 = createObservable(new String[] {
                "3", "33", "333", "3333" }, 3000, null);

        @SuppressWarnings("unchecked")
        Flowable<String> o = Flowable.amb(observable1,
                observable2, observable3);

        @SuppressWarnings("unchecked")
        DefaultObserver<String> observer = mock(DefaultObserver.class);
        o.subscribe(observer);

        scheduler.advanceTimeBy(100000, TimeUnit.MILLISECONDS);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("2");
        inOrder.verify(observer, times(1)).onNext("22");
        inOrder.verify(observer, times(1)).onNext("222");
        inOrder.verify(observer, times(1)).onNext("2222");
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testAmb2() {
        IOException expectedException = new IOException(
                "fake exception");
        Flowable<String> observable1 = createObservable(new String[] {},
                2000, new IOException("fake exception"));
        Flowable<String> observable2 = createObservable(new String[] {
                "2", "22", "222", "2222" }, 1000, expectedException);
        Flowable<String> observable3 = createObservable(new String[] {},
                3000, new IOException("fake exception"));

        @SuppressWarnings("unchecked")
        Flowable<String> o = Flowable.amb(observable1,
                observable2, observable3);

        @SuppressWarnings("unchecked")
        DefaultObserver<String> observer = mock(DefaultObserver.class);
        o.subscribe(observer);

        scheduler.advanceTimeBy(100000, TimeUnit.MILLISECONDS);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("2");
        inOrder.verify(observer, times(1)).onNext("22");
        inOrder.verify(observer, times(1)).onNext("222");
        inOrder.verify(observer, times(1)).onNext("2222");
        inOrder.verify(observer, times(1)).onError(expectedException);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testAmb3() {
        Flowable<String> observable1 = createObservable(new String[] {
                "1" }, 2000, null);
        Flowable<String> observable2 = createObservable(new String[] {},
                1000, null);
        Flowable<String> observable3 = createObservable(new String[] {
                "3" }, 3000, null);

        @SuppressWarnings("unchecked")
        Flowable<String> o = Flowable.amb(observable1,
                observable2, observable3);

        @SuppressWarnings("unchecked")
        DefaultObserver<String> observer = mock(DefaultObserver.class);
        o.subscribe(observer);

        scheduler.advanceTimeBy(100000, TimeUnit.MILLISECONDS);
        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProducerRequestThroughAmb() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>((Long)null);
        ts.request(3);
        final AtomicLong requested1 = new AtomicLong();
        final AtomicLong requested2 = new AtomicLong();
        Flowable<Integer> o1 = Flowable.create(new Publisher<Integer>() {

            @Override
            public void subscribe(Subscriber<? super Integer> s) {
                s.onSubscribe(new Subscription() {

                    @Override
                    public void request(long n) {
                        System.out.println("1-requested: " + n);
                        requested1.set(n);
                    }

                    @Override
                    public void cancel() {
                        
                    }
                });
            }

        });
        Flowable<Integer> o2 = Flowable.create(new Publisher<Integer>() {

            @Override
            public void subscribe(Subscriber<? super Integer> s) {
                s.onSubscribe(new Subscription() {

                    @Override
                    public void request(long n) {
                        System.out.println("2-requested: " + n);
                        requested2.set(n);
                    }

                    @Override
                    public void cancel() {
                        
                    }
                });
            }

        });
        Flowable.amb(o1, o2).subscribe(ts);
        assertEquals(3, requested1.get());
        assertEquals(3, requested2.get());
    }

    @Test
    public void testBackpressure() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        Flowable.range(0, Flowable.bufferSize() * 2)
                .ambWith(Flowable.range(0, Flowable.bufferSize() * 2))
                .observeOn(Schedulers.computation()) // observeOn has a backpressured RxRingBuffer
                .delay(1, TimeUnit.MICROSECONDS) // make it a slightly slow consumer
                .subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        assertEquals(Flowable.bufferSize() * 2, ts.values().size());
    }
    
    
    @SuppressWarnings("unchecked")
    @Test
    public void testSubscriptionOnlyHappensOnce() throws InterruptedException {
        final AtomicLong count = new AtomicLong();
        Consumer<Subscription> incrementer = new Consumer<Subscription>() {
            @Override
            public void accept(Subscription s) {
                count.incrementAndGet();
            }
        };
        
        //this aync stream should emit first
        Flowable<Integer> o1 = Flowable.just(1).doOnSubscribe(incrementer)
                .delay(100, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.computation());
        //this stream emits second
        Flowable<Integer> o2 = Flowable.just(1).doOnSubscribe(incrementer)
                .delay(100, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.computation());
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        Flowable.amb(o1, o2).subscribe(ts);
        ts.request(1);
        ts.awaitTerminalEvent(5, TimeUnit.SECONDS);
        ts.assertNoErrors();
        assertEquals(2, count.get());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testSecondaryRequestsPropagatedToChildren() throws InterruptedException {
        //this aync stream should emit first
        Flowable<Integer> o1 = Flowable.fromArray(1, 2, 3)
                .delay(100, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.computation());
        //this stream emits second
        Flowable<Integer> o2 = Flowable.fromArray(4, 5, 6)
                .delay(200, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.computation());
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(1L);
        
        Flowable.amb(o1, o2).subscribe(ts);
        // before first emission request 20 more
        // this request should suffice to emit all
        ts.request(20);
        //ensure stream does not hang
        ts.awaitTerminalEvent(5, TimeUnit.SECONDS);
        ts.assertNoErrors();
    }

    @Test
    public void testSynchronousSources() {
        // under async subscription the second observable would complete before
        // the first but because this is a synchronous subscription to sources
        // then second observable does not get subscribed to before first
        // subscription completes hence first observable emits result through
        // amb
        int result = Flowable.just(1).doOnNext(new Consumer<Integer>() {
            @Override
            public void accept(Integer t) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        //
                    }
            }
        }).ambWith(Flowable.just(2)).toBlocking().single();
        assertEquals(1, result);
    }
 
    @SuppressWarnings("unchecked")
    @Test
    public void testAmbCancelsOthers() {
        PublishProcessor<Integer> source1 = PublishProcessor.create();
        PublishProcessor<Integer> source2 = PublishProcessor.create();
        PublishProcessor<Integer> source3 = PublishProcessor.create();
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        
        Flowable.amb(source1, source2, source3).subscribe(ts);
        
        assertTrue("Source 1 doesn't have subscribers!", source1.hasSubscribers());
        assertTrue("Source 2 doesn't have subscribers!", source2.hasSubscribers());
        assertTrue("Source 3 doesn't have subscribers!", source3.hasSubscribers());
        
        source1.onNext(1);

        assertTrue("Source 1 doesn't have subscribers!", source1.hasSubscribers());
        assertFalse("Source 2 still has subscribers!", source2.hasSubscribers());
        assertFalse("Source 2 still has subscribers!", source3.hasSubscribers());
        
    }
}