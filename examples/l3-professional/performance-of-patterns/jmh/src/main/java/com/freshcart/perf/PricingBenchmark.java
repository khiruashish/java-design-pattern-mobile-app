package com.freshcart.perf;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Does a decorator chain of pricing rules cost more than one flat method? JMH handles warm-up, forking,
 * dead-code elimination and statistics, which hand-written timing loops get wrong.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class PricingBenchmark {

    interface Price { long of(long paise); }
    record Base() implements Price { public long of(long p) { return p; } }
    record Gst(Price inner) implements Price { public long of(long p) { long v = inner.of(p); return v + v * 5 / 100; } }
    record Member(Price inner) implements Price { public long of(long p) { long v = inner.of(p); return v - v / 20; } }
    record Rounded(Price inner) implements Price { public long of(long p) { return Math.round(inner.of(p) / 100.0) * 100; } }

    @Param({"52000", "120000"})
    long subtotal;

    Price chain;
    List<Price> megamorphicRules;

    @Setup
    public void setUp() {
        chain = new Rounded(new Member(new Gst(new Base())));
        megamorphicRules = List.of(new Base(), new Gst(new Base()), new Member(new Base()), new Rounded(new Base()));
    }

    @Benchmark
    public long flat() {
        long v = subtotal + subtotal * 5 / 100;
        v = v - v / 20;
        return Math.round(v / 100.0) * 100;
    }

    @Benchmark
    public long decoratorChain() {
        return chain.of(subtotal);                     // returned values are consumed by JMH: no dead-code elimination
    }

    @Benchmark
    public void megamorphicLoop(Blackhole bh) {
        for (Price p : megamorphicRules) bh.consume(p.of(subtotal));
    }

    @Benchmark
    public void wrongDeadCode() {
        chain.of(subtotal);                             // result unused: the JIT may remove the work entirely
    }
}
