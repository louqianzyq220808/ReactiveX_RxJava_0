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
package io.reactivex.processors;

import java.util.Arrays;
import java.util.concurrent.atomic.*;

import org.reactivestreams.*;

import io.reactivex.functions.IntFunction;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.internal.util.*;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * A Subject that emits the very last value followed by a completion event or the received error to Subscribers.
 *
 * <p>The implementation of onXXX methods are technically thread-safe but non-serialized calls
 * to them may lead to undefined state in the currently subscribed Subscribers.
 * 
 * <p>Due to the nature Observables are constructed, the AsyncSubject can't be instantiated through
 * {@code new} but must be created via the {@link #create()} method.
 *
 * @param <T> the value type
 */
public final class AsyncProcessor<T> extends FlowProcessor<T> {
    /** The state holding onto the latest value or error and the array of subscribers. */
    final State<T> state;
    /** 
     * Indicates the subject has been terminated. It is checked in the onXXX methods in
     * a relaxed matter: concurrent calls may not properly see it (which shouldn't happen if
     * the reactive-streams contract is held).
     */
    boolean done;
    
    /**
     * Constructs an empty AsyncSubject.
     * @param <T> the observed and observable value type
     * @return the new AsyncSubject instance.
     */
    public static <T> AsyncProcessor<T> create() {
        return new AsyncProcessor<T>();
    }
    
    protected AsyncProcessor() {
        this.state = new State<T>();
    }
    
    @Override
    public void onSubscribe(Subscription s) {
        if (done) {
            s.cancel();
            return;
        }
        s.request(Long.MAX_VALUE);
    }
    
    @Override
    public void onNext(T t) {
        if (done) {
            return;
        }
        if (t == null) {
            onError(new NullPointerException());
            return;
        }
        state.lazySet(t);
    }
    
    @Override
    public void onError(Throwable t) {
        if (done) {
            RxJavaPlugins.onError(t);
            return;
        }
        if (t == null) {
            t = new NullPointerException();
        }
        done = true;
        state.lazySet(NotificationLite.error(t));
        for (AsyncSubscription<T> as : state.terminate()) {
            as.setError(t);
        }
    }
    
    @Override
    public void onComplete() {
        if (done) {
            return;
        }
        done = true;
        @SuppressWarnings("unchecked")
        T value = (T)state.get();
        for (AsyncSubscription<T> as : state.terminate()) {
            as.setValue(value);
        }
    }
    
    @Override
    public boolean hasSubscribers() {
        return state.subscribers().length != 0;
    }
    
    @Override
    public boolean hasValue() {
        Object o = state.get();
        return o != null && !NotificationLite.isError(o);
    }
    
    @Override
    public boolean hasComplete() {
        Object o = state.get();
        return state.subscribers() == State.TERMINATED && !NotificationLite.isError(o);
    }
    
    @Override
    public boolean hasThrowable() {
        return NotificationLite.isError(state.get());
    }
    
    @Override
    public Throwable getThrowable() {
        Object o = state.get();
        if (NotificationLite.isError(o)) {
            return NotificationLite.getError(o);
        }
        return null;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public T getValue() {
        Object o = state.get();
        if (o != null && !NotificationLite.isError(o)) {
            return (T)o;
        }
        return null;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public T[] getValues(T[] array) {
        Object o = state.get();
        if (o != null && !NotificationLite.isError(o) && !NotificationLite.isComplete(o)) {
            int n = array.length;
            if (n == 0) {
                array = Arrays.copyOf(array, 1);
            }
            array[0] = (T)o;
            if (array.length > 1) {
                array[1] = null;
            }
        } else {
            if (array.length != 0) {
                array[0] = null;
            }
        }
        return array;
    }
    
    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
        state.subscribe(s);
    }
    /**
     * The state of the AsyncSubject.
     *
     * @param <T> the value type
     */
    @SuppressWarnings("rawtypes")
    static final class State<T> extends AtomicReference<Object> 
    implements Publisher<T>, IntFunction<AsyncSubscription<T>[]> {
        
        /** */
        private static final long serialVersionUID = 2983503212425065796L;

        /** An empty AsyncSubscription array to avoid allocating it in remove. */
        static final AsyncSubscription[] EMPTY = new AsyncSubscription[0];
        /** An empty array indicating a terminal state .*/
        static final AsyncSubscription[] TERMINATED = new AsyncSubscription[0];
        
        /** The array of current subscribers. */
        @SuppressWarnings("unchecked")
        final AtomicReference<AsyncSubscription<T>[]> subscribers = new AtomicReference<AsyncSubscription<T>[]>(EMPTY);
        
        /**
         * Returns the array of current subscribers.
         * @return the array of current subscribers
         */
        public AsyncSubscription<T>[] subscribers() {
            return subscribers.get();
        }

        /**
         * Terminates the state and returns the last array of subscribers.
         * @return the last array of subscribers
         */
        @SuppressWarnings("unchecked")
        public AsyncSubscription<T>[] terminate() {
            return TerminalAtomicsHelper.terminate(subscribers, TERMINATED);
        }
        
        /**
         * Atomically tries to add the AsyncSubscription to the subscribers array
         * or returns false if the state has been terminated.
         * @param as the AsyncSubscription to add
         * @return true if successful, false if the state has been terminated
         */
        @SuppressWarnings("unchecked")
        boolean add(AsyncSubscription<T> as) {
            return TerminalAtomicsHelper.add(subscribers, as, TERMINATED, this);
        }
        
        /**
         * Atomically removes the given AsyncSubscription.
         * @param as the AsyncSubscription to remove
         */
        @SuppressWarnings("unchecked")
        void remove(AsyncSubscription<T> as) {
            TerminalAtomicsHelper.remove(subscribers, as, TERMINATED, EMPTY, this);
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public AsyncSubscription<T>[] apply(int value) {
            return new AsyncSubscription[value];
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public void subscribe(Subscriber<? super T> t) {
            AsyncSubscription<T> as = new AsyncSubscription<T>(t, this);
            t.onSubscribe(as);
            
            if (add(as)) {
                if (as.isDone()) {
                    remove(as);
                }
            } else {
                Object o = get();
                if (NotificationLite.isError(o)) {
                    as.setError(NotificationLite.getError(o));
                } else {
                    as.setValue((T)o);
                }
            }
        }
    }
    
    /**
     * A subscription implementation that wraps the actual Subscriber and manages the request and cancel calls.
     *
     * @param <T> the value type
     */
    static final class AsyncSubscription<T> extends AtomicInteger implements Subscription {
        /** */
        private static final long serialVersionUID = 2900823026377918858L;
        /** The actual subscriber. */
        final Subscriber<? super T> actual;
        /** The AsyncSubject state containing the value. */
        final State<T> state;
        
        /** State management: no request and no value has been set. */
        static final int NO_REQUEST_NO_VALUE = 0;
        /** State management: no request but value is available. */
        static final int NO_REQUEST_HAS_VALUE = 1;
        /** State management: a positive request has been made but no value is available. */
        static final int HAS_REQUEST_NO_VALUE = 2;
        /** State management: both positive request and value is available, terminal state. */
        static final int HAS_REQUEST_HAS_VALUE = 3;
        
        public AsyncSubscription(Subscriber<? super T> actual, State<T> state) {
            this.actual = actual;
            this.state = state;
        }
        
        /**
         * Indicates the given value is available and emits it to the actual Subscriber if
         * it has requested.
         * @param value the value to emit
         */
        public void setValue(T value) {
            for (;;) {
                int s = get();
                if (s == NO_REQUEST_HAS_VALUE || s == HAS_REQUEST_HAS_VALUE) {
                    return;
                } else
                if (s == NO_REQUEST_NO_VALUE) {
                    if (compareAndSet(NO_REQUEST_NO_VALUE, NO_REQUEST_HAS_VALUE)) {
                        return;
                    }
                } else
                if (s == HAS_REQUEST_NO_VALUE) {
                    lazySet(HAS_REQUEST_HAS_VALUE); // setValue is called once, no need for CAS
                    if (value != null) {
                        actual.onNext(value);
                    }
                    actual.onComplete();
                }
            }
        }
        
        /**
         * Terminates the AsyncSubscription and emits the given error
         * if not already terminated.
         * @param e the Throwable to emit to the Subscriber
         */
        public void setError(Throwable e) {
            int s = get();
            if (s != HAS_REQUEST_HAS_VALUE) {
                s = getAndSet(HAS_REQUEST_HAS_VALUE);
                if (s != HAS_REQUEST_HAS_VALUE) {
                    actual.onError(e);
                }
            }
        }
        
        @Override
        public void request(long n) {
            if (!SubscriptionHelper.validateRequest(n)) {
                return;
            }
            for (;;) {
                int s = get();
                if (s == HAS_REQUEST_NO_VALUE || s == HAS_REQUEST_HAS_VALUE) {
                    return;
                } else
                if (s == NO_REQUEST_NO_VALUE) {
                    if (compareAndSet(NO_REQUEST_NO_VALUE, HAS_REQUEST_NO_VALUE)) {
                        return;
                    }
                } else {
                    if (compareAndSet(NO_REQUEST_HAS_VALUE, HAS_REQUEST_HAS_VALUE)) {
                        @SuppressWarnings("unchecked")
                        T v = (T)state.get();
                        if (v != null) {
                            actual.onNext(v);
                        }
                        actual.onComplete();
                        return;
                    }
                }
            }
        }
        
        @Override
        public void cancel() {
            int s = get();
            if (s != HAS_REQUEST_HAS_VALUE) {
                s = getAndSet(HAS_REQUEST_HAS_VALUE);
                if (s != HAS_REQUEST_HAS_VALUE) {
                    state.remove(this);
                }
            }
        }
        
        /**
         * Returns true if the AsyncSubscription has reached its terminal state.
         * @return  true if the AsyncSubscription has reached its terminal state
         */
        boolean isDone() {
            return get() == HAS_REQUEST_HAS_VALUE;
        }
    }
}
