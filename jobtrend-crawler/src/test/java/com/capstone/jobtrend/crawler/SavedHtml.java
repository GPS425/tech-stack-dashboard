package com.capstone.jobtrend.crawler;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** 한 번 받아 둔 사람인 HTML(커밋하지 않는 폴더). 없으면 그 시험은 건너뛴다. */
final class SavedHtml {

    private static final Path DIR = Path.of("src/test/resources/saved-html");

    private SavedHtml() {}

    static Document load(String name) throws IOException {
        Path f = DIR.resolve(name);
        assumeTrue(Files.exists(f), "저장한 HTML 없음: " + f);
        return Jsoup.parse(f.toFile(), "UTF-8", SaraminClient.BASE);
    }
}
