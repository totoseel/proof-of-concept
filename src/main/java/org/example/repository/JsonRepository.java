package org.example.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JsonRepository {
    private static final String ID_KEY = "_id";
    private final ObjectMapper mapper;
    private final File file;
    private List<Map<String, Object>> data;

    public JsonRepository(String filePath) throws IOException {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.file = new File(filePath);
        load();
    }

    private void load() throws IOException {
        if (file.exists() && file.length() > 0) {
            data = mapper.readValue(file, new TypeReference<List<Map<String, Object>>>() {});
            // _id 필드가 없는 레코드에 자동 부여
            int maxId = data.stream()
                    .mapToInt(r -> toInt(r.get(ID_KEY)))
                    .max().orElse(0);
            for (Map<String, Object> row : data) {
                if (!row.containsKey(ID_KEY)) {
                    row.put(ID_KEY, ++maxId);
                }
            }
        } else {
            data = new ArrayList<>();
        }
    }

    private void save() throws IOException {
        mapper.writeValue(file, data);
    }

    public List<Map<String, Object>> findAll() {
        return new ArrayList<>(data);
    }

    public Optional<Map<String, Object>> findById(int id) {
        return data.stream().filter(r -> toInt(r.get(ID_KEY)) == id).findFirst();
    }

    public Map<String, Object> create(Map<String, Object> record) throws IOException {
        int nextId = data.stream().mapToInt(r -> toInt(r.get(ID_KEY))).max().orElse(0) + 1;
        // _id를 맨 앞에 삽입
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put(ID_KEY, nextId);
        record.forEach((k, v) -> { if (!k.equals(ID_KEY)) ordered.put(k, v); });
        data.add(ordered);
        save();
        return ordered;
    }

    public boolean update(int id, Map<String, Object> fields) throws IOException {
        for (Map<String, Object> row : data) {
            if (toInt(row.get(ID_KEY)) == id) {
                fields.forEach((k, v) -> { if (!k.equals(ID_KEY)) row.put(k, v); });
                save();
                return true;
            }
        }
        return false;
    }

    public boolean delete(int id) throws IOException {
        boolean removed = data.removeIf(r -> toInt(r.get(ID_KEY)) == id);
        if (removed) save();
        return removed;
    }

    static int toInt(Object val) {
        if (val instanceof Integer i) return i;
        if (val instanceof Long l) return l.intValue();
        if (val instanceof Number n) return n.intValue();
        return 0;
    }
}
