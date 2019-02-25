package com.balamaci.reactor;

import com.balamaci.reactor.util.Helpers;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * The flatMap operator is so important and has so many different uses it deserves it's own category to explain
 * I like to think of it as a sort of fork-join operation because what flatMap does is it takes individual items
 * and maps each of them to a Publisher(so it creates new Streams from each object) and then 'flattens' the
 * events from these Streams back into a single Stream.
 * Why this looks like fork-join because for each element you can fork some async jobs that emit some items before completing,
 * and these items are sent downstream to the subscribers as coming from a single stream
 *
 * RuleOfThumb 1: When you have an 'item' as parameter and you need to invoke something that returns an
 *                    Flux<T> instead of <T>, you need flatMap
 * RuleOfThumb 2: When you have Flux<Flux<T>> you probably need flatMap.
 *
 * @author sbalamaci
 */
public class Part06FlatMapOperator implements BaseTestFlux {

    /**
     * Common usecase when for each item you make an async remote call that returns a stream of items (a Publisher<T>)
     *
     * The thing to notice that it's not clear upfront that events from the flatMaps 'substreams' don't arrive
     * in a guaranteed order and events from a substream might get interleaved with the events from other substreams.
     *
     */
    @Test
    public void flatMap() {
        Flux<String> colors = Flux.just("orange", "red", "green")
                .flatMap(colorName -> simulateRemoteOperation(colorName));
        subscribeWithLog(colors);
    }

    /**
     * Inside the flatMap we can operate on the substream with the same stream operators like for ex count
     */
    @Test
    public void flatMapSubstreamOperations() {
        Flux<String> colors = Flux.just("orange", "red", "green", "blue");

        Flux<Tuple2<String, Long>> colorsCounted = colors
                .flatMap(colorName -> {
                    Flux<Long> timer = Flux.interval(Duration.of(2, ChronoUnit.SECONDS));

                    return simulateRemoteOperation(colorName) // <- Still a stream
                                    .zipWith(timer, (val, timerVal) -> val)
                                    .count()
                                    .map(counter -> Tuples.of(colorName, counter));
                    }
                );

        subscribeWithLogWaiting(colorsCounted);
    }


    /**
     * Controlling the level of concurrency of the substreams.
     *
     * In the ex. below, only the first substream(the Flux returned by simulateRemoteOperation) is subscribed.
     * As soon as it completes, another substream is subscribed and so on.
     *
     */
    @Test
    public void flatMapConcurrency() {
        Flux<String> colors = Flux.just("orange", "red", "green")
                .flatMap(val -> simulateRemoteOperation(val), 1);
        subscribeWithLogWaiting(colors);
    }

    /**
     * As seen above flatMap might mean that events emitted by multiple streams might get interleaved
     *
     * concatMap operator acts as a flatMap with 1 level of concurrency which means only one of the created
     * substreams(Flux) is subscribed and thus only one emits events so it's just this substream which
     * emits events until it finishes and a new one will be subscribed and so on
     */
    @Test
    public void concatMap() {
        Flux<String> colors = Flux.just("orange", "red", "green", "blue")
                .subscribeOn(Schedulers.elastic())
                .concatMap(val -> simulateRemoteOperation(val));
        subscribeWithLogWaiting(colors);
    }

    /**
     * Using concatMap to implement a variable delay in the stream.
     * We'll use the stream values to set the delay and we can do it by creating a substream
     * to which we subscribe with concatMap but we also 'delaySubscription' to achieve the
     * required emission.
     */
    @Test
    public void delayOperatorWithVariableDelay() {
        Flux<Integer> delayedValues = Flux.range(0, 5)
                                          .concatMap(val -> Mono.just(val)
                                                                  .delaySubscription(Duration.of(val * 2,
                                                                          ChronoUnit.SECONDS)));
        subscribeWithLogWaiting(delayedValues);
    }


    /**
     * When you have a Stream of Streams - Flux<Flux<T>>
     */
    @Test
    public void flatMapFor() {
        Flux<String> colors = Flux.just("red", "green", "blue", "red", "yellow", "green", "green");

        Flux<GroupedFlux<String, String>> groupedColorsStream = colors
                                                                   .groupBy(val -> val);//grouping key
                                                                   // is the String itself, the color

        Flux<Tuple2<String, Long>> countedColors = groupedColorsStream
                                                    .flatMap(groupedObservable -> groupedObservable
                                                                 .count()
                                                                 .map(countVal ->
                                                                     Tuples.of(groupedObservable.key(), countVal))
                                                    );
        subscribeWithLogWaiting(countedColors);
    }

    /**
     * flatMap in it's most complex form allows to override the 'error' and 'complete' event not just the 'next' event
     * Here we introduce another event on window stream completion
     */
    @Test
    public void flatMapOverrideCompleteEvent() {
        Flux<String> colors = Flux.just("red", "green", "blue", "red", "yellow", "green", "green");
        Flux remastered = colors
                .window(2)
                .flatMap(window -> window.flatMap(Flux::just,
                                                  Flux::error,
                                                  () -> Flux.just("===")
                                         )
                );

        subscribeWithLogWaiting(remastered);
    }

    /**
     * flatMap we ignore thrown exceptions
     */
    @Test
    public void flatMapOverrideError() {
        Flux<String> colors = Flux.just("green", "blue", "red", "yellow", "pink", "green");

        Flux<String> remastered = colors
                .flatMap(color -> simulateRemoteOperation(color)
                                    .flatMap(Flux::just,
                                             err -> {
                                                    log.info("Got exception but we ignore");
                                                    return Flux.empty();
                                            },
                                            Flux::empty
                                    )
                );

        subscribeWithLogWaiting(remastered);
    }

    /**
     * Simulated remote operation that emits as many events as the length of the color string
     * @param color color
     * @return Flux
     */
    private Flux<String> simulateRemoteOperation(String color) {
        return Flux.<String>create(subscriber -> {
            Runnable asyncRun = () -> {
                if("pink".equals(color)) {
                    subscriber.error(new RuntimeException("Pink is not allowed"));
                }
                for (int i = 0; i < color.length(); i++) {
                    subscriber.next(color + i);
                    Helpers.sleepMillis(200);
                }

                subscriber.complete();
            };
            new Thread(asyncRun).start();
        });
    }


}
