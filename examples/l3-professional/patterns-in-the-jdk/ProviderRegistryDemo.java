import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Production: TextBlast's notification service discovers delivery channels as plugins, using only JDK patterns:
 * - ServiceLoader (plugin discovery) with ServiceLoader.Provider, so metadata is read WITHOUT instantiating plugins
 * - a Comparator (Strategy) picks the best provider per country by priority
 * - startup validation fails on duplicate ids or missing coverage
 * - every provider is wrapped in a java.lang.reflect.Proxy (dynamic Proxy) that records calls and failures
 */
public class ProviderRegistryDemo {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ChannelInfo { String id(); String[] countries(); int priority(); }

    public interface Channel { String send(String to, String text); }

    @ChannelInfo(id = "textblast-in", countries = {"IN"}, priority = 10)
    public static class TextBlastIndia implements Channel {
        public String send(String to, String text) { return "TBIN:" + to; }
    }
    @ChannelInfo(id = "textblast-in", countries = {"IN"}, priority = 20)
    public static class TextBlastIndiaV2 implements Channel {                                          // a copy-pasted jar with the same id
        public String send(String to, String text) { return "TBIN2:" + to; }
    }
    @ChannelInfo(id = "globalsms", countries = {"IN", "AE", "SG"}, priority = 5)
    public static class GlobalSms implements Channel {
        public String send(String to, String text) { if (to.startsWith("+65")) throw new IllegalStateException("SG route down"); return "GLOBAL:" + to; }
    }
    @ChannelInfo(id = "expensive-legacy", countries = {"IN"}, priority = 1)
    public static class LegacyGateway implements Channel {
        public LegacyGateway() { System.out.println("  (legacy gateway constructed: slow TLS handshake)"); }
        public String send(String to, String text) { return "LEGACY:" + to; }
    }

    record Stats(AtomicLong calls, AtomicLong failures) { Stats() { this(new AtomicLong(), new AtomicLong()); } }

    static final class Registry {
        final Map<String, Stats> stats = new LinkedHashMap<>();
        private final Map<String, ServiceLoader.Provider<Channel>> byId = new LinkedHashMap<>();
        final Map<String, Channel> instances = new LinkedHashMap<>();

        Registry(ClassLoader loader, List<String> requiredCountries) {
            for (ServiceLoader.Provider<Channel> p : ServiceLoader.load(Channel.class, loader).stream().toList()) {   // (1)!
                ChannelInfo info = p.type().getAnnotation(ChannelInfo.class);
                if (info == null) throw new IllegalStateException(p.type().getName() + " has no @ChannelInfo");
                if (byId.putIfAbsent(info.id(), p) != null) throw new IllegalStateException("duplicate channel id " + info.id());   // (2)!
            }
            for (String country : requiredCountries)
                if (candidates(country).isEmpty()) throw new IllegalStateException("no channel covers " + country);
        }

        List<String> candidates(String country) {                                                       // (3)!
            return byId.values().stream()
                    .map(p -> p.type().getAnnotation(ChannelInfo.class))
                    .filter(i -> List.of(i.countries()).contains(country))
                    .sorted(Comparator.comparingInt(ChannelInfo::priority).reversed())
                    .map(ChannelInfo::id).toList();
        }

        Channel channel(String id) {
            return instances.computeIfAbsent(id, k -> instrument(k, byId.get(k).get()));
        }

        Channel instrument(String id, Channel target) {                                                 // (4)!
            Stats s = stats.computeIfAbsent(id, k -> new Stats());
            return (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[] {Channel.class}, (proxy, method, args) -> {
                s.calls().incrementAndGet();
                try { return method.invoke(target, args); }
                catch (InvocationTargetException e) { s.failures().incrementAndGet(); throw e.getCause(); }
            });
        }

        String send(String country, String to, String text) {                                          // (5)!
            for (String id : candidates(country)) {
                try { return channel(id).send(to, text); }
                catch (RuntimeException e) { System.out.println("  " + id + " failed (" + e.getMessage() + "), trying next"); }
            }
            throw new IllegalStateException("all channels failed for " + country);
        }
    }

    static ClassLoader registering(List<Class<?>> impls) throws Exception {
        Path root = Files.createTempDirectory("spi");
        Path f = root.resolve("META-INF/services/" + Channel.class.getName());
        Files.createDirectories(f.getParent());
        Files.write(f, impls.stream().map(Class::getName).toList());
        return new URLClassLoader(new URL[] {root.toUri().toURL()}, ProviderRegistryDemo.class.getClassLoader());
    }

    public static void main(String[] args) throws Exception {
        Registry registry = new Registry(registering(List.of(TextBlastIndia.class, GlobalSms.class, LegacyGateway.class)), List.of("IN", "SG"));
        System.out.println("IN candidates: " + registry.candidates("IN") + ", SG: " + registry.candidates("SG"));
        System.out.println(registry.send("IN", "+9198450", "Order placed"));
        System.out.println(registry.send("IN", "+9170000", "Order placed"));
        try { registry.send("SG", "+6591234", "Order placed"); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        System.out.println("stats: " + registry.stats.entrySet().stream()
                .map(e -> e.getKey() + " calls=" + e.getValue().calls() + " failures=" + e.getValue().failures()).collect(Collectors.joining("; ")));
        System.out.println("legacy gateway instantiated: " + registry.instances.containsKey("expensive-legacy"));
        try { new Registry(registering(List.of(TextBlastIndia.class, TextBlastIndiaV2.class)), List.of("IN")); }
        catch (IllegalStateException e) { System.out.println("startup refused: " + e.getMessage()); }
        try { new Registry(registering(List.of(TextBlastIndia.class)), List.of("IN", "AE")); }
        catch (IllegalStateException e) { System.out.println("startup refused: " + e.getMessage()); }
    }
}
