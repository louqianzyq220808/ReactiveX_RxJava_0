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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mockito.InOrder;

import io.reactivex.*;
import io.reactivex.exceptions.TestException;
import io.reactivex.flowable.TestHelper;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.PublishSubject;

public class NbpOperatorSkipLastTimedTest {

    @Test
    public void testSkipLastTimed() {
        TestScheduler scheduler = new TestScheduler();

        PublishSubject<Integer> source = PublishSubject.create();

        // FIXME the timeunit now matters due to rounding
        Observable<Integer> result = source.skipLast(1000, TimeUnit.MILLISECONDS, scheduler);

        Observer<Object> o = TestHelper.mockNbpSubscriber();

        result.subscribe(o);

        source.onNext(1);
        source.onNext(2);
        source.onNext(3);

        scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS);

        source.onNext(4);
        source.onNext(5);
        source.onNext(6);

        scheduler.advanceTimeBy(950, TimeUnit.MILLISECONDS);
        source.onComplete();

        InOrder inOrder = inOrder(o);
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(2);
        inOrder.verify(o).onNext(3);
        inOrder.verify(o, never()).onNext(4);
        inOrder.verify(o, never()).onNext(5);
        inOrder.verify(o, never()).onNext(6);
        inOrder.verify(o).onComplete();
        inOrder.verifyNoMoreInteractions();

        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void testSkipLastTimedErrorBeforeTime() {
        TestScheduler scheduler = new TestScheduler();

        PublishSubject<Integer> source = PublishSubject.create();

        Observable<Integer> result = source.skipLast(1, TimeUnit.SECONDS, scheduler);

        Observer<Object> o = TestHelper.mockNbpSubscriber();

        result.subscribe(o);

        source.onNext(1);
        source.onNext(2);
        source.onNext(3);
        source.onError(new TestException());

        scheduler.advanceTimeBy(1050, TimeUnit.MILLISECONDS);

        verify(o).onError(any(TestException.class));

        verify(o, never()).onComplete();
        verify(o, never()).onNext(any());
    }

    @Test
    public void testSkipLastTimedCompleteBeforeTime() {
        TestScheduler scheduler = new TestScheduler();

        PublishSubject<Integer> source = PublishSubject.create();

        Observable<Integer> result = source.skipLast(1, TimeUnit.SECONDS, scheduler);

        Observer<Object> o = TestHelper.mockNbpSubscriber();

        result.subscribe(o);

        source.onNext(1);
        source.onNext(2);
        source.onNext(3);

        scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS);

        source.onComplete();

        InOrder inOrder = inOrder(o);
        inOrder.verify(o).onComplete();
        inOrder.verifyNoMoreInteractions();

        verify(o, never()).onNext(any());
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void testSkipLastTimedWhenAllElementsAreValid() {
        TestScheduler scheduler = new TestScheduler();

        PublishSubject<Integer> source = PublishSubject.create();

        Observable<Integer> result = source.skipLast(1, TimeUnit.MILLISECONDS, scheduler);

        Observer<Object> o = TestHelper.mockNbpSubscriber();

        result.subscribe(o);

        source.onNext(1);
        source.onNext(2);
        source.onNext(3);

        scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS);

        source.onComplete();

        InOrder inOrder = inOrder(o);
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(2);
        inOrder.verify(o).onNext(3);
        inOrder.verify(o).onComplete();
        inOrder.verifyNoMoreInteractions();
    }
}