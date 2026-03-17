package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.IntentFewShot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IntentFewShotMapper {
    int insert(IntentFewShot fewShot);

    IntentFewShot selectById(@Param("id") Long id);

    List<IntentFewShot> selectActiveExamples();

    List<IntentFewShot> selectByIntent(@Param("intent") String intent);

    List<IntentFewShot> selectList(
        @Param("intent") String intent,
        @Param("activeOnly") Boolean activeOnly,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    int update(IntentFewShot fewShot);

    int toggleActive(@Param("id") Long id);

    int deleteById(@Param("id") Long id);
}
