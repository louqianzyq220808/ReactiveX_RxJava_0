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

package io.reactivex.internal.operators.completable;

import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Supplier;

public final class CompletableToSingle<T> extends Single<T> {
    final CompletableConsumable source;
    
    final Supplier<? extends T> completionValueSupplier;
    
    final T completionValue;
    
    public CompletableToSingle(CompletableConsumable source, 
            Supplier<? extends T> completionValueSupplier, T completionValue) {
        this.source = source;
        this.completionValue = completionValue;
        this.completionValueSupplier = completionValueSupplier;
    }

    @Override
    protected void subscribeActual(final SingleSubscriber<? super T> s) {
        source.subscribe(new CompletableSubscriber() {

            @Override
            public void onComplete() {
                T v;

                if (completionValueSupplier != null) {
                    try {
                        v = completionValueSupplier.get();
                    } catch (Throwable e) {
                        s.onError(e);
                        return;
                    }
                } else {
                    v = completionValue;
                }
                
                if (v == null) {
                    s.onError(new NullPointerException("The value supplied is null"));
                } else {
                    s.onSuccess(v);
                }
            }

            @Override
            public void onError(Throwable e) {
                s.onError(e);
            }

            @Override
            public void onSubscribe(Disposable d) {
                s.onSubscribe(d);
            }
            
        });        
    }

}
