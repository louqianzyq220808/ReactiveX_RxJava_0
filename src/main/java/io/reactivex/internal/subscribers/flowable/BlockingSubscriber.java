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

package io.reactivex.internal.subscribers.flowable;

import io.reactivex.internal.subscriptions.SubscriptionHelper;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.*;

import io.reactivex.internal.util.NotificationLite;

public final class BlockingSubscriber<T> extends AtomicReference<Subscription> implements Subscriber<T>, Subscription {
    /** */
    private static final long serialVersionUID = -4875965440900746268L;

    public static final Object TERMINATED = new Object();
    
    final Queue<Object> queue;
    
    public BlockingSubscriber(Queue<Object> queue) {
        this.queue = queue;
    }
    
    @Override
    public void onSubscribe(Subscription s) {
        if (!compareAndSet(null, s)) {
            s.cancel();
            if (get() != SubscriptionHelper.CANCELLED) {
                onError(new IllegalStateException("Subscription already set"));
            }
            return;
        }
        queue.offer(NotificationLite.subscription(this));
    }
    
    @Override
    public void onNext(T t) {
        queue.offer(NotificationLite.next(t));
    }
    
    @Override
    public void onError(Throwable t) {
        queue.offer(NotificationLite.error(t));
    }
    
    @Override
    public void onComplete() {
        queue.offer(NotificationLite.complete());
    }
    
    @Override
    public void request(long n) {
        get().request(n);
    }
    
    @Override
    public void cancel() {
        if (SubscriptionHelper.dispose(this)) {
            queue.offer(TERMINATED);
        }
    }
    
    public boolean isCancelled() {
        return get() == SubscriptionHelper.CANCELLED;
    }
}
