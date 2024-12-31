package com.stdev.futurebox.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypeStatistics {
    private String category;
    private String typeName;
    private Long count;
    private Double percentage;
} 