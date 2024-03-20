package com.example.prioweb.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NodeMapper {
    List<Integer> NodeCheck();

    List<Map<String, String>> NodeGet();

    List<Map<String, String>> CategoryGet();

    List<Map<String, String>> SensorGet();
}
