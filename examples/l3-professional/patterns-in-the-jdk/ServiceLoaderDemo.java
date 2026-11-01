import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.ServiceLoader;

/**
 * ServiceLoader: the JDK's plugin mechanism (Service Provider Interface). Implementations are listed in
 * META-INF/services/<interface name> (or `provides` in module-info) and discovered at runtime; the caller
 * depends only on the interface. To stay a single file, this demo writes that registration file to a temp folder.
 */
public class ServiceLoaderDemo {

    public interface SmsGateway { String name(); String send(String to, String text); }                // (1)!

    public static class TextBlastGateway implements SmsGateway {
        public String name() { return "textblast"; }
        public String send(String to, String text) { return "TB-OK " + to; }
    }
    public static class BackupGateway implements SmsGateway {
        public String name() { return "backup"; }
        public String send(String to, String text) { return "BK-OK " + to; }
    }

    static ClassLoader withRegistration(Class<?> service, List<Class<?>> impls) throws Exception {        // (2)!
        Path root = Files.createTempDirectory("spi");
        Path file = root.resolve("META-INF/services/" + service.getName());
        Files.createDirectories(file.getParent());
        Files.write(file, impls.stream().map(Class::getName).toList());
        return new URLClassLoader(new URL[] {root.toUri().toURL()}, ServiceLoaderDemo.class.getClassLoader());
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = withRegistration(SmsGateway.class, List.of(TextBlastGateway.class, BackupGateway.class));
        ServiceLoader<SmsGateway> gateways = ServiceLoader.load(SmsGateway.class, loader);
        for (SmsGateway g : gateways) System.out.println("found " + g.name() + ": " + g.send("98450", "hi"));   // (3)!

        System.out.println("JDK's own providers (file systems): "
                + FileSystemProvider.installedProviders().stream().map(FileSystemProvider::getScheme).toList()
                + ", default: " + FileSystems.getDefault().provider().getScheme());
    }
}
