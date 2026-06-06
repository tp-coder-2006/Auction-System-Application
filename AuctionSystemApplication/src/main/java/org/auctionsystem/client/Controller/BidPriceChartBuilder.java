package org.auctionsystem.client.Controller;

import com.google.gson.JsonObject;
import javafx.collections.ObservableList;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * BidPriceChartBuilder
 *
 * Utility dùng chung cho Controller_Bidding_room, Controller_Item_Detail,
 * Controller_Seller_Item_Detail.
 *
 * Tạo và cập nhật một LineChart hiển thị sự thay đổi giá đấu theo thời gian
 * dựa trên toàn bộ lịch sử bid (allBidHistoryList).
 *
 * Trục X: thời gian (định dạng HH:mm:ss hoặc dd/MM HH:mm tuỳ khoảng cách)
 * Trục Y: giá đặt (đơn vị: nghìn đồng – ₫)
 */
public class BidPriceChartBuilder {

    private static final DateTimeFormatter DT_FLEXIBLE = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd['T'][' ']HH:mm")
            .optionalStart().appendPattern(":ss").optionalEnd()
            .optionalStart().appendPattern(".SSSSSSSSS").optionalEnd()
            .toFormatter();

    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter LABEL_FULL = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    /**
     * Tạo mới một LineChart đã được cấu hình sẵn.
     * Gọi một lần khi khởi tạo controller, sau đó dùng {@link #refresh} để cập nhật.
     */
    public static LineChart<String, Number> createChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Thời gian");
        xAxis.setTickLabelRotation(-45);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Giá đặt (₫)");
        yAxis.setForceZeroInRange(false);
        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis) {
            @Override
            public String toString(Number value) {
                double v = value.doubleValue();
                if (v >= 1_000_000) return String.format("%.1ftr", v / 1_000_000);
                if (v >= 1_000)     return String.format("%.0fk",  v / 1_000);
                return String.format("%.0f", v);
            }
        });

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Biểu đồ giá đấu");
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(false);

        // Style inline — hoà với theme tối của ứng dụng
        chart.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-plot-background-color: #1e2a38;" +
                        "-fx-font-size: 11px;"
        );

        return chart;
    }

    /**
     * Cập nhật dữ liệu cho chart từ allBidHistoryList.
     * Dữ liệu được sort tăng dần theo bidTime trước khi vẽ.
     *
     * @param chart           chart cần cập nhật
     * @param allBidHistory   danh sách bid (có thể chứa thứ tự bất kỳ)
     * @param startingPrice   giá khởi điểm (dùng làm điểm đầu tiên nếu chưa có bid)
     */
    public static void refresh(LineChart<String, Number> chart,
                               ObservableList<JsonObject> allBidHistory,
                               double startingPrice) {
        if (chart == null) return;

        chart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();

        // Sort tăng dần theo bidTime (cũ nhất → mới nhất) để vẽ đúng chiều
        List<JsonObject> sorted = new ArrayList<>(allBidHistory);
        sorted.sort((a, b) -> {
            String ta = a.has("bidTime") ? a.get("bidTime").getAsString() : "";
            String tb = b.has("bidTime") ? b.get("bidTime").getAsString() : "";
            int cmp = ta.compareTo(tb); // tăng dần theo thời gian (cũ → mới)
            if (cmp != 0) return cmp;
            double pa = a.has("bidAmount") ? a.get("bidAmount").getAsDouble() : 0;
            double pb = b.has("bidAmount") ? b.get("bidAmount").getAsDouble() : 0;
            return Double.compare(pa, pb); // tăng dần theo giá (thấp → cao)
        });

        if (sorted.isEmpty()) {
            // Không có bid — chỉ hiển thị giá khởi điểm nếu có
            if (startingPrice > 0) {
                series.getData().add(new XYChart.Data<>("Khởi điểm", startingPrice));
            }
        } else {
            // Xác định format nhãn: nếu cùng ngày dùng HH:mm:ss, khác ngày dùng dd/MM HH:mm
            boolean multiDay = isMultiDay(sorted);
            DateTimeFormatter labelFmt = multiDay ? LABEL_FULL : LABEL_FMT;

            for (JsonObject bid : sorted) {
                double amount = bid.has("bidAmount") ? bid.get("bidAmount").getAsDouble() : 0;
                String rawTime = bid.has("bidTime") ? bid.get("bidTime").getAsString() : "";
                String label   = formatLabel(rawTime, labelFmt);
                if (amount > 0) {
                    series.getData().add(new XYChart.Data<>(label, amount));
                }
            }
        }

        chart.getData().add(series);

        // Style đường và điểm sau khi thêm vào chart
        series.getNode().setStyle("-fx-stroke: #4fc3f7; -fx-stroke-width: 2.5px;");
        for (XYChart.Data<String, Number> d : series.getData()) {
            if (d.getNode() != null) {
                d.getNode().setStyle(
                        "-fx-background-color: #4fc3f7, white;" +
                                "-fx-background-radius: 5px;" +
                                "-fx-padding: 4px;"
                );
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean isMultiDay(List<JsonObject> sorted) {
        if (sorted.size() < 2) return false;
        String first = sorted.get(0).has("bidTime") ? sorted.get(0).get("bidTime").getAsString() : "";
        String last  = sorted.get(sorted.size() - 1).has("bidTime")
                ? sorted.get(sorted.size() - 1).get("bidTime").getAsString() : "";
        try {
            LocalDateTime t1 = LocalDateTime.parse(first, DT_FLEXIBLE);
            LocalDateTime t2 = LocalDateTime.parse(last,  DT_FLEXIBLE);
            return !t1.toLocalDate().equals(t2.toLocalDate());
        } catch (Exception e) {
            return false;
        }
    }

    private static String formatLabel(String raw, DateTimeFormatter fmt) {
        if (raw == null || raw.isBlank()) return raw;
        try {
            return LocalDateTime.parse(raw, DT_FLEXIBLE).format(fmt);
        } catch (Exception e) {
            return raw.length() > 19 ? raw.substring(11, 19) : raw;
        }
    }
}