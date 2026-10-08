package gg.fotia.futureui.asset;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** 独立输出可分发 ZIP；文件顺序与时间固定，相同输入文件产生相同 ZIP。 */
final class ResourcePackArchive {
    static final String FILE = "generated/futureui-resource-pack.zip";
    private ResourcePackArchive() {}

    static Path write(Path root, Path staging, PackManifest manifest) throws Exception {
        Path target = root.resolve(FILE);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "futureui-pack-", ".zip");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8);
                    var paths = Files.walk(staging)) {
                for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                    ZipEntry entry = new ZipEntry(staging.relativize(file).toString().replace('\\', '/'));
                    entry.setTime(0L);
                    zip.putNextEntry(entry);
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
            try (ZipFile zip = new ZipFile(temporary.toFile())) { PackVerifier.verify(zip, manifest); }
            ResourcePackFiles.copy(temporary, target);
            return target;
        } finally { Files.deleteIfExists(temporary); }
    }
}
