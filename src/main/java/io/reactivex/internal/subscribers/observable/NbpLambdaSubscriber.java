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

package io.reactivex.internal.subscribers.observable;

import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.disposables.DisposableHelper;
import io.reactivex.plugins.RxJavaPlugins;

public final class NbpLambdaSubscriber<T> extends AtomicReference<Disposable> implements Observer<T>, Disposable {
    /** */
    private static final long serialVersionUID = -7251123623727029452L;
    final Consumer<? super T> onNext;
    final Consumer<? super Throwable> onError;
    final Runnable onComplete;
    final Consumer<? super Disposable> onSubscribe;
    
    public NbpLambdaSubscriber(Consumer<? super T> onNext, Consumer<? super Throwable> onError,
            Runnable onComplete,
            Consumer<? super Disposable> onSubscribe) {
        super();
        this.onNext = onNext;
        this.onError = onError;
        this.onComplete = onComplete;
        this.onSubscribe = onSubscribe;
    }
    
    @Override
    public void onSubscribe(Disposable s) {
        if (DisposableHelper.setOnce(this, s)) {
            onSubscribe.accept(this);
        }
    }
    
    @Override
    public void onNext(T t) {
        try {
            onNext.accept(t);
        } catch (Throwable e) {
            onError(e);
        }
    }
    
    @Override
    public void onError(Throwable t) {
        dispose();
        try {
            onError.accept(t);
        } catch (Throwable e) {
            RxJavaPlugins.onError(e);
            RxJavaPlugins.onError(t);
        }
    }
    
    @Override
    public void onComplete() {
        dispose();
        try {
            onComplete.run();
        } catch (Throwable e) {
            RxJavaPlugins.onError(e);
        }
    }
    
    @Override
    public void dispose() {
        DisposableHelper.dispose(this);
    }

    @Override
    public boolean isDisposed() {
        return get() == DisposableHelper.DISPOSED;
    }
}
