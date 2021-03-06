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
import io.reactivex.disposables.*;

public final class ObservableFromArray<T> extends Observable<T> {
    final T[] array;
    public ObservableFromArray(T[] array) {
        this.array = array;
    }
    public T[] array() {
        return array; // NOPMD
    }
    @Override
    public void subscribeActual(Observer<? super T> s) {
        Disposable d = Disposables.empty();
        
        s.onSubscribe(d);
        
        T[] a = array;
        int n = a.length;
        
        for (int i = 0; i < n && !d.isDisposed(); i++) {
            T value = a[i];
            if (value == null) {
                s.onError(new NullPointerException("The " + i + "th element is null"));
                return;
            }
            s.onNext(value);
        }
        if (!d.isDisposed()) {
            s.onComplete();
        }
    }
}