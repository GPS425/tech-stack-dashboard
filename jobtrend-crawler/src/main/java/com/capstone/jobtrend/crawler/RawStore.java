package com.capstone.jobtrend.crawler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * raw/{공고번호}.json 쓰기·읽기·지우기.
 * DB 밖에 두는 ④(기술 추출)의 재료다. 약관 때문에 API로 내보내지 않고, 마감 뒤 보관 기간이 지나면 지운다.
 */
public class RawStore {

    public record Raw(List<DetailPageParser.Tag> tags, List<String> required, List<String> preferred) {}

    private final Path dir;
    // Raw 에 칸을 더해도 옛 파일을 읽을 수 있게 모르는 칸은 넘긴다
    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public RawStore(Path dir) {
        this.dir = dir;
    }

    public void save(long postingId, Raw raw) throws IOException {
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, postingId + "-", ".tmp");   // 같은 공고를 둘이 써도 섞이지 않게 이름을 따로
        try {
            json.writeValue(tmp.toFile(), raw);
            Files.move(tmp, file(postingId), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);           // 쓰다 끊겨도 이전 파일이 남는다
        } finally {
            Files.deleteIfExists(tmp);                         // 실패했을 때 찌꺼기를 남기지 않는다
        }
    }

    /** 파일이 없으면 빈 값. 깨진 파일은 IOException(JsonProcessingException) — ④가 그 공고만 다시 받게 한다. */
    public Optional<Raw> read(long postingId) throws IOException {
        try {
            return Optional.of(json.readValue(file(postingId).toFile(), Raw.class));
        } catch (NoSuchFileException | FileNotFoundException e) {
            return Optional.empty();
        }
    }

    /** 폴더가 있고 파일이 하나라도 있는지. 볼륨이 안 붙었거나 경로가 틀리면 false */
    public boolean looksMounted() {
        try (var files = Files.list(dir)) {
            return files.findAny().isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    public boolean delete(long postingId) throws IOException {
        return Files.deleteIfExists(file(postingId));
    }

    private Path file(long postingId) {
        return dir.resolve(postingId + ".json");
    }
}
