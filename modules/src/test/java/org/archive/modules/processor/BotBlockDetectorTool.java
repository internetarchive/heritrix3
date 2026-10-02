/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual
 *  contributors.
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.archive.modules.processor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;

import static org.archive.modules.processor.BotBlockDetector.detect;

/**
 * Fetches a single URL and runs {@link BotBlockDetector} against the response.
 */
public class BotBlockDetectorTool {
    public static void main(String[] args) throws Exception {
        String url = null;
        String userAgent = "Mozilla/5.0 (compatible; heritrix/3 +https://github.com/internetarchive/heritrix3)";

        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("-")) {
                url = args[i];
                continue;
            }
            switch (args[i]) {
                case "-h":
                case "--help":
                    System.out.println("Usage: BotBlockDetectorTool [options] URL");
                    System.out.println("Fetches the given URL and prints the detected bot-blocking service, if any");
                    System.out.println("");
                    System.out.println("Options:");
                    System.out.println("  -A, --user-agent UA    User-Agent header to send");
                    System.exit(0);
                    break;
                case "-A":
                case "--user-agent":
                    userAgent = args[++i];
                    break;
                default:
                    System.err.println("BotBlockDetectorTool: Unknown option: " + args[i]);
                    System.err.println("Try --help for usage information.");
                    System.exit(1);
            }
        }

        if (url == null) {
            System.err.println("BotBlockDetectorTool: No URL specified.");
            System.err.println("Try --help for usage information.");
            System.exit(1);
        }

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .build(), HttpResponse.BodyHandlers.ofByteArray());

        CrawlURI curi = new CrawlURI(UURIFactory.getInstance(url));
        curi.setFetchType(CrawlURI.FetchType.HTTP_GET);
        curi.setFetchStatus(response.statusCode());
        response.headers().map().forEach((name, values) -> {
            for (String value : values) curi.putHttpResponseHeader(name, value);
        });
        curi.setContentType(response.headers().firstValue("content-type").orElse(null));

        File tempDir = Files.createTempDirectory("botblockdetector").toFile();
        Recorder recorder = new Recorder(tempDir, "botblockdetector");
        try {
            curi.setRecorder(recorder);
            recorder.inputWrap(new ByteArrayInputStream(response.body()));
            recorder.getRecordedInput().readFully();
            recorder.close();

            System.out.println(response.statusCode() + " " + url);
            String detected = detect(curi);
            System.out.println(detected == null ? "No bot block detected" : "botblock:" + detected);
        } finally {
            recorder.cleanup();
            tempDir.delete();
        }
    }
}
