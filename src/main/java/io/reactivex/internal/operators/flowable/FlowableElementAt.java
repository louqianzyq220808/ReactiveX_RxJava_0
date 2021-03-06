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

import org.reactivestreams.*;

import io.reactivex.Flowable;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.plugins.RxJavaPlugins;

public final class FlowableElementAt<T> extends Flowable<T> {
    final Publisher<T> source;
    final long index;
    final T defaultValue;
    public FlowableElementAt(Publisher<T> source, long index, T defaultValue) {
        this.source = source;
        this.index = index;
        this.defaultValue = defaultValue;
    }
    
    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
        source.subscribe(new ElementAtSubscriber<T>(s, index, defaultValue));
    }
    
    static final class ElementAtSubscriber<T> implements Subscriber<T>, Subscription {
        final Subscriber<? super T> actual;
        final long index;
        final T defaultValue;
        
        Subscription s;
        
        long count;
        
        boolean done;
        
        public ElementAtSubscriber(Subscriber<? super T> actual, long index, T defaultValue) {
            this.actual = actual;
            this.index = index;
            this.defaultValue = defaultValue;
        }
        
        @Override
        public void onSubscribe(Subscription s) {
            if (this.s != null) {
                s.cancel();
                RxJavaPlugins.onError(new IllegalStateException("Subscription already set!"));
                return;
            }
            this.s = s;
            actual.onSubscribe(this);
        }
        
        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }
            long c = count;
            if (c == index) {
                done = true;
                s.cancel();
                actual.onNext(t);
                actual.onComplete();
                return;
            }
            count = c + 1;
        }
        
        @Override
        public void onError(Throwable t) {
            if (done) {
                return;
            }
            done = true;
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            if (index <= count && !done) {
                done = true;
                T v = defaultValue;
                if (v == null) {
                    actual.onError(new IndexOutOfBoundsException());
                } else {
                    actual.onNext(v);
                    actual.onComplete();
                }
            }
        }
        
        @Override
        public void request(long n) {
            if (SubscriptionHelper.validateRequest(n)) {
                s.request(Long.MAX_VALUE);
            }
        }
        
        @Override
        public void cancel() {
            s.cancel();
        }
    }
}
