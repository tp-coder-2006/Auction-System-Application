package org.auctionsystem.server.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * [SỬA #4] GsonConfig — cung cấp Gson đã đăng ký TypeAdapter cho LocalDateTime.
 *
 * VẤN ĐỀ GỐC: Gson() mặc định không biết cách serialize/deserialize LocalDateTime.
 * Khi gặp trường LocalDateTime (ví dụ Item.startTime, Bid.bidTime), Gson crash với:
 *   java.lang.UnsupportedOperationException
 * Toàn bộ response trả về lỗi, client không nhận được dữ liệu.
 *
 * CÁCH SỬA: Đăng ký TypeAdapter tùy chỉnh:
 *   - Serialize:   LocalDateTime -> String "yyyy-MM-dd HH:mm:ss"
 *   - Deserialize: String "yyyy-MM-dd HH:mm:ss" -> LocalDateTime
 *   - Xử lý null:  trả null thay vì crash
 */
public class GsonConfig {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final TypeAdapter<LocalDateTime> LOCAL_DATE_TIME_ADAPTER =
            new TypeAdapter<LocalDateTime>() {
                @Override
                public void write(JsonWriter out, LocalDateTime value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                    } else {
                        out.value(value.format(FORMATTER));
                    }
                }

                @Override
                public LocalDateTime read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    }
                    return LocalDateTime.parse(in.nextString(), FORMATTER);
                }
            };

    /** Dùng method này thay vì new Gson() ở mọi Service/Handler. */
    public static Gson create() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, LOCAL_DATE_TIME_ADAPTER)
                .create();
    }
}
