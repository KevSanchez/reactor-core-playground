package com.balamaci.reactor;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * @author sbalamaci
 */
public class Part05AdvancedOperators implements BaseTestFlux {

    @Test
    public void buffer() {
        Flux<Long> numbers = Flux.interval(Duration.of(1, ChronoUnit.SECONDS));

        Flux<List<Long>> delayedNumbersWindow = numbers.buffer(5);

        subscribeWithLog(delayedNumbersWindow);
    }

    /**
     * Split the stream into multiple windows.
     * The window size can be specified as a number of events
     */
    @Test
    public void simpleWindow() {
        Flux<Long> numbers = Flux.interval(Duration.of(1, ChronoUnit.SECONDS));

        Flux<Long> delayedNumbersWindow = numbers
                .window(5) //size of events
                .flatMap(window -> window.doOnComplete(() -> log.info("Window completed"))
                                         .doOnSubscribe((sub) -> log.info("Window started"))
                        );

        subscribeWithLogWaiting(delayedNumbersWindow);
    }


    /**
     * Split the stream into multiple windows
     * When the period to start the windows(timeshit) is bigger than the window timespan, some events will be lost
     */
    @Test
    public void windowWithLoosingEvents() {
        Flux<Long> numbers = Flux.interval(Duration.of(1, ChronoUnit.SECONDS));

        Duration timespan = Duration.of(5, ChronoUnit.SECONDS);
        Duration timeshift = Duration.of(10, ChronoUnit.SECONDS);

        subscribeTimeWindow(numbers, timespan, timeshift);
    }


    @Test
    public void windowWithDuplicateEvents() {
        Flux<Long> numbers = Flux.interval(Duration.of(1, ChronoUnit.SECONDS));

        Duration timespan = Duration.of(9, ChronoUnit.SECONDS);
        Duration timeshift = Duration.of(3, ChronoUnit.SECONDS);

        subscribeTimeWindow(numbers, timespan, timeshift);
    }

    /**
     * Simple .window() splits the stream into multiple windows. The windows are delimited when
     * a cancelation event is triggered
     */
    @Test
    public void windowLimitedByUnsubscription() {
        Flux<String> colors = Flux.fromArray(new String[]{"red", "green", "blue",
                "red", "yellow", "#", "green", "green"});


        colors.window()
                .concatMap(window -> window.takeUntil(val -> val.equals("#")).buffer())
                .subscribe(list -> {
                    String listCommaSeparated = String.join(",", list);
                    log.info("List {}", listCommaSeparated);
                });
    }


    private void subscribeTimeWindow(Flux<Long> numbersStream, Duration timespan, Duration timeshift) {
        Flux<Long> delayedNumbersWindow = numbersStream
                .window(timespan, timeshift)
                .flatMap(window -> window
                        .doOnComplete(() -> log.info("Window completed"))
                        .doOnSubscribe((sub) -> log.info("Window started"))
                );

        subscribeWithLogWaiting(delayedNumbersWindow);
    }


    /**
     * groupBy splits the stream into multiple streams with the key generated by the function passed as
     * parameter to groupBy
     */
    @Test
    public void groupBy() {
        Flux<String> colors = Flux.fromArray(new String[]{"red", "green", "blue",
                "red", "yellow", "green", "green"});

        Flux<GroupedFlux<String, String>> groupedColorsStream = colors
                .groupBy(val -> val); //identity function
//                .groupBy(val -> "length" + val.length());


        Flux<Tuple2<String, Long>> colorCountStream = groupedColorsStream
                .flatMap(groupedColor -> groupedColor
                                            .count()
                                            .map(count -> Tuples.of(groupedColor.key(), count))
                );

        subscribeWithLogWaiting(colorCountStream);
    }



}
