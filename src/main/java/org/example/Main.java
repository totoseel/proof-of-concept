package org.example;

import org.example.repository.JsonRepository;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

public class Main {

    private static JsonRepository repo;
    private static final Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);

    public static void main(String[] args) throws IOException {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        String filePath = resolveFilePath(args);
        System.out.println("=== JSON CRUD 콘솔 앱 ===");
        System.out.println("데이터 파일: " + new File(filePath).getAbsolutePath());

        repo = new JsonRepository(filePath);

        while (true) {
            printMenu();
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1" -> listAll();
                case "2" -> readOne();
                case "3" -> createOne();
                case "4" -> updateOne();
                case "5" -> deleteOne();
                case "0" -> { System.out.println("종료합니다."); return; }
                default  -> System.out.println("잘못된 입력입니다. 다시 선택해주세요.");
            }
        }
    }

    private static String resolveFilePath(String[] args) {
        if (args.length > 0) return args[0];

        System.out.print("JSON 파일 경로 (기본값: data.json): ");
        String input = sc.nextLine().trim();
        return input.isEmpty() ? "data.json" : input;
    }

    private static void printMenu() {
        System.out.println("\n─────────────────────────────");
        System.out.println(" 1. 전체 목록 조회");
        System.out.println(" 2. 단건 조회 (ID)");
        System.out.println(" 3. 새 항목 추가");
        System.out.println(" 4. 항목 수정");
        System.out.println(" 5. 항목 삭제");
        System.out.println(" 0. 종료");
        System.out.print("선택: ");
    }

    private static void listAll() {
        List<Map<String, Object>> list = repo.findAll();
        if (list.isEmpty()) {
            System.out.println("데이터가 없습니다.");
            return;
        }
        System.out.println("\n[전체 목록] " + list.size() + "건");
        System.out.println("─".repeat(60));
        list.forEach(r -> System.out.println(formatRecord(r)));
        System.out.println("─".repeat(60));
    }

    private static void readOne() {
        System.out.print("조회할 ID: ");
        int id = readInt();
        Optional<Map<String, Object>> found = repo.findById(id);
        if (found.isPresent()) {
            System.out.println("\n[조회 결과]");
            System.out.println(formatRecord(found.get()));
        } else {
            System.out.println("ID " + id + "에 해당하는 항목이 없습니다.");
        }
    }

    private static void createOne() throws IOException {
        System.out.println("\n[새 항목 추가]");
        System.out.println("필드를 입력하세요. 빈 이름을 입력하면 완료됩니다.");

        Map<String, Object> record = new LinkedHashMap<>();
        while (true) {
            System.out.print("필드 이름 (완료: Enter): ");
            String key = sc.nextLine().trim();
            if (key.isEmpty()) break;
            System.out.print("값 [" + key + "]: ");
            String raw = sc.nextLine().trim();
            record.put(key, parseValue(raw));
        }

        if (record.isEmpty()) {
            System.out.println("입력된 필드가 없습니다.");
            return;
        }

        Map<String, Object> created = repo.create(record);
        System.out.println("추가되었습니다. (ID: " + created.get("_id") + ")");
    }

    private static void updateOne() throws IOException {
        System.out.print("\n수정할 ID: ");
        int id = readInt();
        Optional<Map<String, Object>> existing = repo.findById(id);
        if (existing.isEmpty()) {
            System.out.println("ID " + id + "에 해당하는 항목이 없습니다.");
            return;
        }

        Map<String, Object> old = existing.get();
        System.out.println("현재 값: " + formatRecord(old));
        System.out.println("수정할 필드를 입력하세요. 빈 이름을 입력하면 완료됩니다.");
        System.out.println("(값을 비워두면 기존 값 유지, 새 필드 이름을 입력하면 추가됩니다)");

        Map<String, Object> updates = new LinkedHashMap<>();
        while (true) {
            System.out.print("필드 이름 (완료: Enter): ");
            String key = sc.nextLine().trim();
            if (key.isEmpty()) break;
            if (key.equals("_id")) {
                System.out.println("_id는 수정할 수 없습니다.");
                continue;
            }
            Object current = old.get(key);
            String prompt = current != null ? "값 [" + key + "] (현재: " + current + "): "
                                            : "값 [" + key + "] (새 필드): ";
            System.out.print(prompt);
            String raw = sc.nextLine().trim();
            if (!raw.isEmpty()) {
                updates.put(key, parseValue(raw));
            }
        }

        if (updates.isEmpty()) {
            System.out.println("변경된 내용이 없습니다.");
            return;
        }

        repo.update(id, updates);
        System.out.println("수정되었습니다.");
    }

    private static void deleteOne() throws IOException {
        System.out.print("\n삭제할 ID: ");
        int id = readInt();
        if (repo.delete(id)) {
            System.out.println("ID " + id + " 항목이 삭제되었습니다.");
        } else {
            System.out.println("ID " + id + "에 해당하는 항목이 없습니다.");
        }
    }

    private static String formatRecord(Map<String, Object> record) {
        StringBuilder sb = new StringBuilder();
        record.forEach((k, v) -> sb.append(k).append(": ").append(v).append("  "));
        return sb.toString().stripTrailing();
    }

    // 숫자로 파싱 가능하면 숫자로, 아니면 문자열로 저장
    private static Object parseValue(String raw) {
        try { return Integer.parseInt(raw); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(raw); } catch (NumberFormatException ignored) {}
        return raw;
    }

    private static int readInt() {
        while (true) {
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.print("숫자를 입력해주세요: ");
            }
        }
    }
}
