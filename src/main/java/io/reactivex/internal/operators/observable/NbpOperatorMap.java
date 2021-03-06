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


package io.reactivex.internal.operators.observable;

import io.reactivex.Observable.NbpOperator;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.internal.disposables.DisposableHelper;
import io.reactivex.plugins.RxJavaPlugins;

public final class NbpOperatorMap<T, U> implements NbpOperator<U, T> {
    final Function<? super T, ? extends U> function;
    
    public NbpOperatorMap(Function<? super T, ? extends U> function) {
        this.function = function;
    }
    
    @Override
    public Observer<? super T> apply(Observer<? super U> t) {
        return new MapperSubscriber<T, U>(t, function);
    }
    
    static final class MapperSubscriber<T, U> implements Observer<T> {
        final Observer<? super U> actual;
        final Function<? super T, ? extends U> function;
        
        Disposable subscription;
        
        boolean done;
        
        public MapperSubscriber(Observer<? super U> actual, Function<? super T, ? extends U> function) {
            this.actual = actual;
            this.function = function;
        }
        @Override
        public void onSubscribe(Disposable s) {
            if (DisposableHelper.validate(this.subscription, s)) {
                subscription = s;
                actual.onSubscribe(s);
            }
        }
        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }
            U u;
            try {
                u = function.apply(t);
            } catch (Throwable e) {
                done = true;
                subscription.dispose();
                actual.onError(e);
                return;
            }
            if (u == null) {
                done = true;
                subscription.dispose();
                actual.onError(new NullPointerException("Value returned by the function is null"));
                return;
            }
            actual.onNext(u);
        }
        @Override
        public void onError(Throwable t) {
            if (done) {
                RxJavaPlugins.onError(t);
                return;
            }
            done = true;
            actual.onError(t);
        }
        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            actual.onComplete();
        }
    }
}
