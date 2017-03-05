import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Richard on 05/03/2017.
 */
public class Runner {

    public static void main(String[] args) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        AtomicInteger seen = new AtomicInteger(0);
        AtomicInteger processed = new AtomicInteger(0);

        Path source = Paths.get("F:\\iPod_Control\\Music");
        Path destination = Paths.get("\\\\NAS\\media\\Music");

        List<Path> unknown = new ArrayList<>();

        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    if (!attrs.isDirectory()) {
                        AudioFile f = AudioFileIO.read(file.toFile());
                        Tag tag = f.getTag();
                        String artist = tag.getFirst(FieldKey.ARTIST);
                        String album = tag.getFirst(FieldKey.ALBUM);
                        String title = tag.getFirst(FieldKey.TITLE);
                        String track = tag.getFirst(FieldKey.TRACK);
                        if (isMissing(artist) || isMissing(album) || isMissing(title) || isMissing(track)) {
                            unknown.add(file);
                        }

                        seen.incrementAndGet();
                        executorService.submit(() -> {
                            Path newFolder = destination.resolve(escape(artist)).resolve(escape(album));
                            String fileString = file.toString();
                            String ext = fileString.substring(fileString.lastIndexOf('.'));
                            Path newPath = newFolder.resolve(track + " - " + escape(title) + ext);
                            try {
                                Files.createDirectories(newFolder);
                                Files.copy(file, newPath, StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                e.printStackTrace();
                                unknown.add(file);
                            }

                            int processedFiles = processed.incrementAndGet();
                            if (processedFiles % 10 == 0) {
                                System.out.println(processedFiles + " processed out of " + seen.intValue());
                            }
                        });
                    }
                } catch (IOException | CannotReadException | ReadOnlyFileException | InvalidAudioFrameException | TagException e) {
                    unknown.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            private boolean isMissing(String string) {
                return "".equals(string) || string == null;
            }

            private String escape(String string) {
                return string.replace(".", "").replace("\\", " ").replace("/", " ");
            }
        });

        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.HOURS);

        System.out.println("Unknown:");
        unknown.forEach(System.out::println);

    }
}
