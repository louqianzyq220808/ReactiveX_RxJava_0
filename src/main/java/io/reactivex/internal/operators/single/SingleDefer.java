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

package io.reactivex.internal.operators.single;

import io.reactivex.*;
import io.reactivex.functions.Supplier;
import io.reactivex.internal.disposables.EmptyDisposable;

public final class SingleDefer<T> extends Single<T> {

    final Supplier<? extends SingleConsumable<? extends T>> singleSupplier;
    
    public SingleDefer(Supplier<? extends SingleConsumable<? extends T>> singleSupplier) {
        this.singleSupplier = singleSupplier;
    }

    @Override
    protected void subscribeActual(SingleSubscriber<? super T> s) {
        SingleConsumable<? extends T> next;
        
        try {
            next = singleSupplier.get();
        } catch (Throwable e) {
            s.onSubscribe(EmptyDisposable.INSTANCE);
            s.onError(e);
            return;
        }
        
        if (next == null) {
            s.onSubscribe(EmptyDisposable.INSTANCE);
            s.onError(new NullPointerException("The Single supplied was null"));
            return;
        }
        
        next.subscribe(s);
    }

}
