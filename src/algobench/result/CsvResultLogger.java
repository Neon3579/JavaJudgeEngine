package algobench.result;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * CSV 파일 로거. 실행마다 파일을 새로 만들어(첫 {@code log}에서 truncate + 헤더 1회) 결과당 1행 append.
 *
 * <p>첫 호출에서 lazy 초기화(부모 디렉토리 생성 → 파일 truncate → 헤더 기록)하므로
 * 실제 결과가 없으면 빈 파일을 만들지 않는다. {@code log}는 {@code synchronized}로 다중 호출에 안전하다.
 */
public final class CsvResultLogger implements ResultLogger {

    private final Path csvFilePath;
    private final ResultFormatter formatter;
    private boolean initialized = false;

    public CsvResultLogger(Path csvFilePath, ResultFormatter formatter) {
        this.csvFilePath = Objects.requireNonNull(csvFilePath, "csvFilePath");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    @Override
    public synchronized void log(BenchmarkResult result) throws IOException {
        if (!initialized) {
            Path parent = csvFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(csvFilePath, formatter.header() + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            initialized = true;
        }
        Files.writeString(csvFilePath, formatter.format(result) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
    }
}
