package com.example.prioweb.model;

import java.sql.Date;
import java.sql.Timestamp;

import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Transactional
public class Node {
    private String id;
    private int product_id;
    private int company_id;
    private String manufacturer;
    private String setup_location;
    private Timestamp setup_datetime;
    private Date scheduled_inspection_date;
    private Timestamp registration_datetime;
}
