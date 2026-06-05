package com.example.consistency.persistence;

import java.util.List;

public record SqlStatement(
        String sql,
        List<Object> params
) {

    public static SqlStatement of(String sql, Object... params) {
        return new SqlStatement(sql, List.of(params));
    }
}

