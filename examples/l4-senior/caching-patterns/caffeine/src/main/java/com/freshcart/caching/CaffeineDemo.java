package com.freshcart.caching;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Real Caffeine: a bounded, expiring, refreshing price cache with statistics. A manual ticker controls time. */
public class CaffeineDemo {

    public static void main(String[] args) {
        AtomicLong nanos = new AtomicLong();
        Ticker ticker = nanos::get;
        Map<String, Integer> pricingDb = new HashMap<>(Map.of("MILK-1L", 68, "CURD-500G", 45, "EGGS-12", 84));
        Map<String, Integer> loads = new HashMap<>();
        List<Runnable> background = new ArrayList<>();          // background work runs when we say so

        LoadingCache<String, Integer> prices = Caffeine.newBuilder()                               // (1)!
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .refreshAfterWrite(Duration.ofMinutes(1))
                .executor(background::add)
                .ticker(ticker)
                .recordStats()
                .build(sku -> {                                                                    // (2)!
                    loads.merge(sku, 1, Integer::sum);
                    return pricingDb.get(sku);
                });

        for (int i = 0; i < 4; i++) prices.get("MILK-1L");
        prices.get("CURD-500G");
        System.out.println("after 5 reads: loads=" + new java.util.TreeMap<>(loads));

        pricingDb.put("MILK-1L", 70);
        nanos.addAndGet(Duration.ofSeconds(90).toNanos());
        System.out.println("t=90s read returns " + prices.get("MILK-1L") + " (old value served, refresh triggered)");   // (3)!
        while (!background.isEmpty()) background.remove(0).run();
        System.out.println("after the background refresh: " + prices.get("MILK-1L"));

        nanos.addAndGet(Duration.ofMinutes(11).toNanos());
        prices.cleanUp();
        System.out.println("t=12.5min entries left: " + prices.estimatedSize());                   // (4)!

        CacheStats stats = prices.stats();
        System.out.printf("hits=%d misses=%d hitRate=%.2f loads=%d%n", stats.hitCount(), stats.missCount(), stats.hitRate(), stats.loadCount());
    }
}
