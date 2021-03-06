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

import io.reactivex.*;
import io.reactivex.Observable.NbpOperator;
import io.reactivex.disposables.*;

public final class NbpOperatorSwitchIfEmpty<T> implements NbpOperator<T, T> {
    final ObservableConsumable<? extends T> other;
    public NbpOperatorSwitchIfEmpty(ObservableConsumable<? extends T> other) {
        this.other = other;
    }
    
    @Override
    public Observer<? super T> apply(Observer<? super T> t) {
        SwitchIfEmptySubscriber<T> parent = new SwitchIfEmptySubscriber<T>(t, other);
        t.onSubscribe(parent.arbiter);
        return parent;
    }
    
    static final class SwitchIfEmptySubscriber<T> implements Observer<T> {
        final Observer<? super T> actual;
        final ObservableConsumable<? extends T> other;
        final SerialDisposable arbiter;
        
        boolean empty;
        
        public SwitchIfEmptySubscriber(Observer<? super T> actual, ObservableConsumable<? extends T> other) {
            this.actual = actual;
            this.other = other;
            this.empty = true;
            this.arbiter = new SerialDisposable();
        }
        
        @Override
        public void onSubscribe(Disposable s) {
            arbiter.set(s);
        }
        
        @Override
        public void onNext(T t) {
            if (empty) {
                empty = false;
            }
            actual.onNext(t);
        }
        
        @Override
        public void onError(Throwable t) {
            actual.onError(t);
        }
        
        @Override
        public void onComplete() {
            if (empty) {
                empty = false;
                other.subscribe(this);
            } else {
                actual.onComplete();
            }
        }
    }
}
