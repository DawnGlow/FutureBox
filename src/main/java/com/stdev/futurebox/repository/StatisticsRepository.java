package com.stdev.futurebox.repository;

import com.stdev.futurebox.connection.DBConnectionUtil;
import com.stdev.futurebox.dto.DailyStatistics;
import com.stdev.futurebox.dto.TypeStatistics;
import com.stdev.futurebox.dto.ItemStatistics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class StatisticsRepository {

    public List<DailyStatistics> getDailyStatistics(LocalDate startDate, LocalDate endDate) throws SQLException {
        String sql = """
            WITH RECURSIVE dates AS (
                SELECT ?::date as date
                UNION ALL
                SELECT date + 1
                FROM dates
                WHERE date < ?::date
            ),
            daily_stats AS (
                SELECT 
                    DATE(created_at) as stat_date,
                    COUNT(*) as total_count,
                    SUM(CASE WHEN is_opened THEN 1 ELSE 0 END) as opened_count,
                    SUM(CASE WHEN future_movie_type IS NOT NULL THEN 1 ELSE 0 END) as movie_count,
                    SUM(CASE WHEN future_gifticon_type IS NOT NULL THEN 1 ELSE 0 END) as gifticon_count,
                    SUM(CASE WHEN future_invention_type IS NOT NULL THEN 1 ELSE 0 END) as invention_count
                FROM future_box
                WHERE DATE(created_at) BETWEEN ? AND ?
                GROUP BY DATE(created_at)
            )
            SELECT 
                d.date,
                COALESCE(s.total_count, 0) as total_count,
                COALESCE(s.opened_count, 0) as opened_count,
                COALESCE(s.movie_count, 0) as movie_count,
                COALESCE(s.gifticon_count, 0) as gifticon_count,
                COALESCE(s.invention_count, 0) as invention_count
            FROM dates d
            LEFT JOIN daily_stats s ON d.date = s.stat_date
            ORDER BY d.date ASC
        """;

        List<DailyStatistics> statistics = new ArrayList<>();
        
        try (Connection con = DBConnectionUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            
            pstmt.setDate(1, Date.valueOf(startDate));
            pstmt.setDate(2, Date.valueOf(endDate));
            pstmt.setDate(3, Date.valueOf(startDate));
            pstmt.setDate(4, Date.valueOf(endDate));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    statistics.add(new DailyStatistics(
                        rs.getDate("date").toLocalDate(),
                        rs.getLong("total_count"),
                        rs.getLong("opened_count"),
                        rs.getLong("movie_count"),
                        rs.getLong("gifticon_count"),
                        rs.getLong("invention_count")
                    ));
                }
            }
        }
        
        return statistics;
    }

    public List<TypeStatistics> getTypeStatistics() throws SQLException {
        String sql = """
            WITH movie_stats AS (
                SELECT 
                    '영화' AS type_category,
                    m.name AS type_name,
                    COUNT(*) AS count,
                    (COUNT(*) * 100.0 / NULLIF((SELECT COUNT(*) FROM future_box WHERE future_movie_type IS NOT NULL), 0)) AS percentage
                FROM future_box fb
                RIGHT JOIN future_movie_types m ON fb.future_movie_type = m.id
                GROUP BY m.name
            ),
            gifticon_stats AS (
                SELECT 
                    '기프티콘' AS type_category,
                    g.name AS type_name,
                    COUNT(*) AS count,
                    (COUNT(*) * 100.0 / NULLIF((SELECT COUNT(*) FROM future_box WHERE future_gifticon_type IS NOT NULL), 0)) AS percentage
                FROM future_box fb
                RIGHT JOIN future_gifticon_types g ON fb.future_gifticon_type = g.id
                GROUP BY g.name
            ),
            invention_stats AS (
                SELECT 
                    '발명품' AS type_category,
                    i.name AS type_name,
                    COUNT(*) AS count,
                    (COUNT(*) * 100.0 / NULLIF((SELECT COUNT(*) FROM future_box WHERE future_invention_type IS NOT NULL), 0)) AS percentage
                FROM future_box fb
                RIGHT JOIN future_invention_types i ON fb.future_invention_type = i.id
                GROUP BY i.name
            )
            SELECT * FROM movie_stats
            UNION ALL
            SELECT * FROM gifticon_stats
            UNION ALL
            SELECT * FROM invention_stats
            ORDER BY type_category, count DESC
        """;

        List<TypeStatistics> statistics = new ArrayList<>();
        
        try (Connection con = DBConnectionUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                String category = rs.getString("type_category");
                String typeName = rs.getString("type_name");
                Long count = rs.getLong("count");
                Double percentage = rs.getDouble("percentage");
                statistics.add(new TypeStatistics(category, typeName, count, percentage));
            }
        }
        
        return statistics;
    }

    public Long getCreateCount(LocalDate startDate, LocalDate endDate) throws SQLException {
        String sql = """
            SELECT COUNT(*) AS create_count
            FROM future_box
            WHERE DATE(created_at) BETWEEN ? AND ?
        """;
        try (Connection con = DBConnectionUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            
            pstmt.setDate(1, Date.valueOf(startDate));
            pstmt.setDate(2, Date.valueOf(endDate));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("create_count");
                }
            }
        }
        return 0L;
    }

    public List<ItemStatistics> getItemStatistics() throws SQLException {
        String sql = """
            WITH total_boxes AS (
                SELECT COUNT(*) AS total
                FROM future_box
            )
            SELECT\s
                'FaceMirror' AS item_name,
                COUNT(fm.id) AS count,
                COUNT(fm.id)*100.0/(SELECT total FROM total_boxes) AS percentage
            FROM future_box fb
            LEFT JOIN future_face_mirror fm ON fb.id = fm.box_id
            WHERE fm.id IS NOT NULL
            UNION ALL
            SELECT
                'Gifticon',
                COUNT(*) AS count,
                COUNT(*)*100.0/(SELECT total FROM total_boxes) AS percentage
            FROM future_box
            WHERE future_gifticon_type IS NOT NULL
            UNION ALL
            SELECT
                'Hologram',
                COUNT(fh.id),
                COUNT(fh.id)*100.0/(SELECT total FROM total_boxes)
            FROM future_box fb
            LEFT JOIN future_hologram fh ON fb.id = fh.box_id
            WHERE fh.id IS NOT NULL
            UNION ALL
            SELECT
                'Invention',
                COUNT(*),
                COUNT(*)*100.0/(SELECT total FROM total_boxes)
            FROM future_box
            WHERE future_invention_type IS NOT NULL
            UNION ALL
            SELECT
                'Lotto',
                COUNT(fl.id),
                COUNT(fl.id)*100.0/(SELECT total FROM total_boxes)
            FROM future_box fb
            LEFT JOIN future_lotto fl ON fb.id = fl.box_id
            WHERE fl.id IS NOT NULL
            UNION ALL
            SELECT
                'Movie',
                COUNT(*),
                COUNT(*)*100.0/(SELECT total FROM total_boxes)
            FROM future_box
            WHERE future_movie_type IS NOT NULL
            UNION ALL
            SELECT
                'Note',
                COUNT(fn.id),
                COUNT(fn.id)*100.0/(SELECT total FROM total_boxes)
            FROM future_box fb
            LEFT JOIN future_note fn ON fb.id = fn.box_id
            WHERE fn.id IS NOT NULL
            ORDER BY 2 DESC  -- count DESC
        """;

        List<ItemStatistics> statistics = new ArrayList<>();
        
        try (Connection con = DBConnectionUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
             
            while (rs.next()) {
                String itemName = rs.getString("item_name");
                Long count = rs.getLong("count");
                Double percentage = rs.getDouble("percentage");
                statistics.add(new ItemStatistics(itemName, count, percentage));
            }
        }
        
        return statistics;
    }

}