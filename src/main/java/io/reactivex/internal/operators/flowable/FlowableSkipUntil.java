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

import java.util.concurrent.atomic.AtomicBoolean;

import org.reactivestreams.*;

import io.reactivex.Flowable;
import io.reactivex.internal.subscriptions.*;
import io.reactivex.subscribers.SerializedSubscriber;

public final class FlowableSkipUntil<T, U> extends Flowable<T> {
    final Publisher<T> source;
    final Publisher<U> other;
    public FlowableSkipUntil(Publisher<T> source, Publisher<U> other) {
        this.source = source;
        this.other = other;
    }
    
    @Override
    protected void subscribeActual(Subscriber<? super T> child) {
        final SerializedSubscriber<T> serial = new SerializedSubscriber<T>(child);
        
        final ArrayCompositeSubscription frc = new ArrayCompositeSubscription(2);
        
        final SkipUntilSubscriber<T> sus = new SkipUntilSubscriber<T>(serial, frc);
        
        other.subscribe(new Subscriber<U>() {
            Subscription s;
            @Override
            public void onSubscribe(Subscription s) {
                if (SubscriptionHelper.validateSubscription(this.s, s)) {
                    this.s = s;
                    if (frc.setResource(1, s)) {
                        s.request(Long.MAX_VALUE);
                    }
                }
            }
            
            @Override
            public void onNext(U t) {
                s.cancel();
                sus.notSkipping = true;
            }
            
            @Override
            public void onError(Throwable t) {
                frc.dispose();
                // in case the other emits an onError before the main even sets a subscription
                if (sus.compareAndSet(false, true)) {
                    EmptySubscription.error(t, serial);
                } else {
                    serial.onError(t);
                }
            }
            
            @Override
            public void onComplete() {
                sus.notSkipping = true;
            }
        });
        
        source.subscribe(sus);
    }
    
    
    static final class SkipUntilSubscriber<T> extends AtomicBoolean implements Subscriber<T>, Subscription {
        /** */
        private static final long serialVersionUID = -1113667257122396604L;
        final Subscriber<? super T> actual;
        final ArrayCompositeSubscription frc;
        
        Subscription s;
        
        volatile boolean notSkipping;
        boolean notSkippingLocal;

        public SkipUntilSubscriber(Subscriber<? super T> actual, ArrayCompositeSubscription frc) {
            this.actual = actual;
            this.frc = frc;
        }
        
        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validateSubscription(this.s, s)) {
                this.s = s;
                if (frc.setResource(0, s)) {
                    if (compareAndSet(false, true)) {
                        actual.onSubscribe(this);
                    }
                }
            }
        }
        
        @Override
        public void onNext(T t) {
            if (notSkippingLocal) {
                actual.onNext(t);
            } else
            if (notSkipping) {
                notSkippingLocal = true;
                actual.onNext(t);
            } else {
                s.request(1);
            }
        }
        
        @Override
        public void onError(Throwable t) {
            frc.dispose();
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            frc.dispose();
            actual.onComplete();
        }
        
        @Override
        public void request(long n) {
            s.request(n);
        }
        
        @Override
        public void cancel() {
            frc.dispose();
        }
    }
}
