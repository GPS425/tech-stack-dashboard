package com.capstone.jobtrend.crawler;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 한 번 받아 둔 사람인 HTML(커밋하지 않는 폴더). 없으면 그 시험은 건너뛴다.
 * 폴더는 Gradle 이 넘기는 savedHtml.dir(절대 경로), 없으면 클래스패스의 saved-html.
 * -PrequireSavedHtml 로 돌리면(savedHtml.require=true) 없을 때 건너뛰지 않고 실패한다.
 * 같은 구조를 흉내 낸 합성 HTML 시험은 SyntheticHtmlTest 에 있고 항상 돈다.
 */
final class SavedHtml {

    private SavedHtml() {}

    static Document load(String name) throws IOException {
        Path f = dir().resolve(name);
        if (!Files.exists(f)) {
            String msg = "저장한 HTML 없음: " + f.toAbsolutePath();
            if (Boolean.getBoolean("savedHtml.require")) throw new AssertionError(msg);
            assumeTrue(false, msg);
        }
        return Jsoup.parse(f.toFile(), "UTF-8", SaraminClient.BASE);
    }

    static Path dir() {
        String prop = System.getProperty("savedHtml.dir");
        if (prop != null && !prop.isBlank()) return Path.of(prop);
        URL url = SavedHtml.class.getResource("/saved-html");   // IDE 에서 Gradle 없이 돌릴 때
        if (url != null && "file".equals(url.getProtocol())) {
            try {
                return Path.of(url.toURI());
            } catch (URISyntaxException e) {
                // 아래 상대 경로로
            }
        }
        return Path.of("src/test/resources/saved-html");
    }
}
