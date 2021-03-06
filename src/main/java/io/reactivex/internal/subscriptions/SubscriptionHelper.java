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

package io.reactivex.internal.subscriptions;

import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Subscription;

import io.reactivex.internal.functions.Objects;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * Utility methods to validate Subscriptions and Disposables in the various onSubscribe calls.
 */
public enum SubscriptionHelper {
    ;
    /**
     * Represents a cancelled Subscription.
     * <p>Don't leak this instance!
     */
    public static final Subscription CANCELLED = Cancelled.INSTANCE;
    
    /**
     * Verifies that current is null, next is not null, otherwise signals errors
     * to the RxJavaPlugins and returns false
     * @param current the current Subscription, expected to be null
     * @param next the next Subscription, expected to be non-null
     * @return true if the validation succeeded
     */
    public static boolean validateSubscription(Subscription current, Subscription next) {
        if (next == null) {
            RxJavaPlugins.onError(new NullPointerException("next is null"));
            return false;
        }
        if (current != null) {
            next.cancel();
            reportSubscriptionSet();
            return false;
        }
        return true;
    }
    
    /**
     * Reports that the subscription is already set to the RxJavaPlugins error handler.
     */
    public static void reportSubscriptionSet() {
        RxJavaPlugins.onError(new IllegalStateException("Subscription already set!"));
    }

    /**
     * Validates that the n is positive.
     * @param n the request amount
     * @return false if n is non-positive.
     */
    public static boolean validateRequest(long n) {
        if (n <= 0) {
            RxJavaPlugins.onError(new IllegalArgumentException("n > 0 required but it was " + n));
            return false;
        }
        return true;
    }
    
    /**
     * Check if the given subscription is the common cancelled subscription.
     * @param d the subscription to check
     * @return true if the subscription is the common cancelled subscription
     */
    public static boolean isCancelled(Subscription d) {
        return d == CANCELLED;
    }
    
    /**
     * Atomically sets the subscription on the field and cancels the
     * previous subscription if any.
     * @param field the target field to set the new subscription on
     * @param d the new subscription
     * @return true if the operation succeeded, false if the target field
     * holds the {@link #CANCELLED} instance.
     * @see #replace(AtomicReference, Subscription)
     */
    public static boolean set(AtomicReference<Subscription> field, Subscription d) {
        for (;;) {
            Subscription current = field.get();
            if (current == CANCELLED) {
                if (d != null) {
                    d.cancel();
                }
                return false;
            }
            if (field.compareAndSet(current, d)) {
                if (current != null) {
                    current.cancel();
                }
                return true;
            }
        }
    }
    
    /**
     * Atomically sets the subscription on the field if it is still null.
     * <p>If the field is not null and doesn't contain the {@link #CANCELLED}
     * instance, the {@link #reportSubscriptionSet()} is called.
     * @param field the target field
     * @param d the new subscription to set
     * @return true if the operation succeeded, false if the target field was not null.
     */
    public static boolean setOnce(AtomicReference<Subscription> field, Subscription d) {
        Objects.requireNonNull(d, "d is null");
        if (!field.compareAndSet(null, d)) {
            d.cancel();
            if (field.get() != CANCELLED) {
                reportSubscriptionSet();
            }
            return false;
        }
        return true;
    }

    /**
     * Atomically sets the subscription on the field but does not
     * cancel the previouls subscription.
     * @param field the target field to set the new subscription on
     * @param d the new subscription
     * @return true if the operation succeeded, false if the target field
     * holds the {@link #CANCELLED} instance.
     * @see #set(AtomicReference, Subscription)
     */
    public static boolean replace(AtomicReference<Subscription> field, Subscription d) {
        for (;;) {
            Subscription current = field.get();
            if (current == CANCELLED) {
                if (d != null) {
                    d.cancel();
                }
                return false;
            }
            if (field.compareAndSet(current, d)) {
                return true;
            }
        }
    }
    
    /**
     * Atomically swaps in the common cancelled subscription instance
     * and disposes the previous subscription if any.
     * @param field the target field to dispose the contents of
     * @return true if the swap from the non-cancelled instance to the
     * common cancelled instance happened in the caller's thread (allows
     * further one-time actions).
     */
    public static boolean dispose(AtomicReference<Subscription> field) {
        Subscription current = field.get();
        if (current != CANCELLED) {
            current = field.getAndSet(CANCELLED);
            if (current != CANCELLED) {
                if (current != null) {
                    current.cancel();
                }
                return true;
            }
        }
        return false;
    }
    
    /**
     * The common cancelled instance implemented as a singleton enum.
     */
    enum Cancelled implements Subscription {
        INSTANCE
        ;

        @Override
        public void request(long n) {
            // deliberately ignored
        }

        @Override
        public void cancel() {
            // deliberately ignored
        }
        
    }
}
