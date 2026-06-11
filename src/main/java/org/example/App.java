package org.example;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** OS 진입점 — System.in/out 글루 코드만 포함하며 커버리지 측정 대상에서 제외됩니다. */
public class App {
    public static void main(String[] args) throws IOException {
        PrintStream stdout = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        System.setOut(stdout);
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        Main.start(args, System.in, stdout);
    }
}
