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

import java.util.concurrent.atomic.*;

import org.reactivestreams.*;

import io.reactivex.*;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.internal.util.BackpressureHelper;

public final class FlowableMaterialize<T> extends Flowable<Try<Optional<T>>> {

    final Publisher<T> source;
    
    public FlowableMaterialize(Publisher<T> source) {
        this.source = source;
    }

    @Override
    protected void subscribeActual(Subscriber<? super Try<Optional<T>>> s) {
        source.subscribe(new MaterializeSubscriber<T>(s));
    }
    
    static final class MaterializeSubscriber<T> extends AtomicLong implements Subscriber<T>, Subscription {
        /** */
        private static final long serialVersionUID = -3740826063558713822L;
        final Subscriber<? super Try<Optional<T>>> actual;
        
        Subscription s;
        
        final AtomicInteger state = new AtomicInteger();
        
        Try<Optional<T>> value;
        
        volatile boolean done;
        
        static final int NO_REQUEST_NO_VALUE = 0;
        static final int NO_REQUEST_HAS_VALUE = 1;
        static final int HAS_REQUEST_NO_VALUE = 2;
        static final int HAS_REQUEST_HAS_VALUE = 3;
        
        public MaterializeSubscriber(Subscriber<? super Try<Optional<T>>> actual) {
            this.actual = actual;
        }
        
        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validateSubscription(this.s, s)) {
                this.s = s;
                actual.onSubscribe(this);
            }
        }
        
        @Override
        public void onNext(T t) {
            actual.onNext(Notification.next(t));
            
            if (get() != Long.MAX_VALUE) {
                decrementAndGet();
            }
        }
        
        void tryEmit(Try<Optional<T>> v) {
            if (get() != 0L) {
                state.lazySet(HAS_REQUEST_HAS_VALUE);
                actual.onNext(v);
                actual.onComplete();
            } else {
                for (;;) {
                    int s = state.get();
                    if (s == HAS_REQUEST_NO_VALUE) {
                        if (state.compareAndSet(s, HAS_REQUEST_HAS_VALUE)) {
                            actual.onNext(v);
                            actual.onComplete();
                            return;
                        }
                    } else
                    if (s == NO_REQUEST_HAS_VALUE) {
                        return;
                    } else
                    if (s == HAS_REQUEST_HAS_VALUE) {
                        value = null;
                        return;
                    } else {
                        value = v;
                        done = true;
                        if (state.compareAndSet(s, NO_REQUEST_HAS_VALUE)) {
                            return;
                        }
                    }
                }
            }
        }
        
        @Override
        public void onError(Throwable t) {
            Try<Optional<T>> v = Notification.error(t);
            
            tryEmit(v);
        }
        
        @Override
        public void onComplete() {
            Try<Optional<T>> v = Notification.complete();
            
            tryEmit(v);
        }
        
        @Override
        public void request(long n) {
            if (!SubscriptionHelper.validateRequest(n)) {
                return;
            }
            BackpressureHelper.add(this, n);
            if (done) {
                for (;;) {
                    int s = state.get();
                    if (s == NO_REQUEST_HAS_VALUE) {
                        if (state.compareAndSet(s, HAS_REQUEST_HAS_VALUE)) {
                            Try<Optional<T>> v = value;
                            value = null;
                            actual.onNext(v);
                            actual.onComplete();
                            return;
                        }
                    } else
                    if (s == HAS_REQUEST_NO_VALUE || s == HAS_REQUEST_HAS_VALUE) {
                        return;
                    } else
                    if (state.compareAndSet(s, HAS_REQUEST_NO_VALUE)) {
                        return;
                    }
                }
            } else {
                s.request(n);
            }
        }
        
        @Override
        public void cancel() {
            state.lazySet(HAS_REQUEST_HAS_VALUE);
            s.cancel();
        }
    }
}
