package org.example;

import org.example.repository.JsonRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

public class Main {

    private final JsonRepository repo;
    private final Scanner sc;
    private final PrintStream out;

    Main(JsonRepository repo, InputStream in, PrintStream out) {
        this.repo = repo;
        this.sc   = new Scanner(in, StandardCharsets.UTF_8);
        this.out  = out;
    }

    static void start(String[] args, InputStream in, PrintStream out) throws IOException {
        String filePath = resolveFilePath(args, in, out);
        out.println("=== JSON CRUD 콘솔 앱 ===");
        out.println("데이터 파일: " + new File(filePath).getAbsolutePath());

        JsonRepository repo = new JsonRepository(filePath);
        new Main(repo, in, out).run();
    }

    static String resolveFilePath(String[] args, InputStream in, PrintStream out) {
        if (args.length > 0) return args[0];
        out.print("JSON 파일 경로 (기본값: data.json): ");
        String input = new Scanner(in, StandardCharsets.UTF_8).nextLine().trim();
        return input.isEmpty() ? "data.json" : input;
    }

    void run() throws IOException {
        while (true) {
            printMenu();
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1" -> listAll();
                case "2" -> readOne();
                case "3" -> createOne();
                case "4" -> updateOne();
                case "5" -> deleteOne();
                case "0" -> { out.println("종료합니다."); return; }
                default  -> out.println("잘못된 입력입니다. 다시 선택해주세요.");
            }
        }
    }

    private void printMenu() {
        out.println("\n─────────────────────────────");
        out.println(" 1. 전체 목록 조회");
        out.println(" 2. 단건 조회 (ID)");
        out.println(" 3. 새 항목 추가");
        out.println(" 4. 항목 수정");
        out.println(" 5. 항목 삭제");
        out.println(" 0. 종료");
        out.print("선택: ");
    }

    void listAll() {
        List<Map<String, Object>> list = repo.findAll();
        if (list.isEmpty()) {
            out.println("데이터가 없습니다.");
            return;
        }
        out.println("\n[전체 목록] " + list.size() + "건");
        out.println("─".repeat(60));
        list.forEach(r -> out.println(formatRecord(r)));
        out.println("─".repeat(60));
    }

    void readOne() {
        out.print("조회할 ID: ");
        int id = readInt();
        Optional<Map<String, Object>> found = repo.findById(id);
        if (found.isPresent()) {
            out.println("\n[조회 결과]");
            out.println(formatRecord(found.get()));
        } else {
            out.println("ID " + id + "에 해당하는 항목이 없습니다.");
        }
    }

    void createOne() throws IOException {
        out.println("\n[새 항목 추가]");
        out.println("필드를 입력하세요. 빈 이름을 입력하면 완료됩니다.");

        Map<String, Object> record = new LinkedHashMap<>();
        while (true) {
            out.print("필드 이름 (완료: Enter): ");
            String key = sc.nextLine().trim();
            if (key.isEmpty()) break;
            out.print("값 [" + key + "]: ");
            String raw = sc.nextLine().trim();
            record.put(key, parseValue(raw));
        }

        if (record.isEmpty()) {
            out.println("입력된 필드가 없습니다.");
            return;
        }

        Map<String, Object> created = repo.create(record);
        out.println("추가되었습니다. (ID: " + created.get("_id") + ")");
    }

    void updateOne() throws IOException {
        out.print("\n수정할 ID: ");
        int id = readInt();
        Optional<Map<String, Object>> existing = repo.findById(id);
        if (existing.isEmpty()) {
            out.println("ID " + id + "에 해당하는 항목이 없습니다.");
            return;
        }

        Map<String, Object> old = existing.get();
        out.println("현재 값: " + formatRecord(old));
        out.println("수정할 필드를 입력하세요. 빈 이름을 입력하면 완료됩니다.");
        out.println("(값을 비워두면 기존 값 유지, 새 필드 이름을 입력하면 추가됩니다)");

        Map<String, Object> updates = new LinkedHashMap<>();
        while (true) {
            out.print("필드 이름 (완료: Enter): ");
            String key = sc.nextLine().trim();
            if (key.isEmpty()) break;
            if (key.equals("_id")) {
                out.println("_id는 수정할 수 없습니다.");
                continue;
            }
            Object current = old.get(key);
            String prompt = current != null
                    ? "값 [" + key + "] (현재: " + current + "): "
                    : "값 [" + key + "] (새 필드): ";
            out.print(prompt);
            String raw = sc.nextLine().trim();
            if (!raw.isEmpty()) {
                updates.put(key, parseValue(raw));
            }
        }

        if (updates.isEmpty()) {
            out.println("변경된 내용이 없습니다.");
            return;
        }

        repo.update(id, updates);
        out.println("수정되었습니다.");
    }

    void deleteOne() throws IOException {
        out.print("\n삭제할 ID: ");
        int id = readInt();
        if (repo.delete(id)) {
            out.println("ID " + id + " 항목이 삭제되었습니다.");
        } else {
            out.println("ID " + id + "에 해당하는 항목이 없습니다.");
        }
    }

    static String formatRecord(Map<String, Object> record) {
        StringBuilder sb = new StringBuilder();
        record.forEach((k, v) -> sb.append(k).append(": ").append(v).append("  "));
        return sb.toString().stripTrailing();
    }

    static Object parseValue(String raw) {
        try { return Integer.parseInt(raw); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(raw); } catch (NumberFormatException ignored) {}
        return raw;
    }

    int readInt() {
        while (true) {
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                out.print("숫자를 입력해주세요: ");
            }
        }
    }
}
