package com.example.prioweb.controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.example.prioweb.mapper.NodeMapper;

@RestController
public class NodeController {
    private NodeMapper mapper;

    public NodeController(NodeMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping(value = "node", produces = "application/json; charset=UTF-8")
    public List<Map<String, String>> nodeGet() {
        List<Map<String, String>> nodeList = mapper.NodeGet();
        List<Map<String, String>> resultList = new ArrayList<>();

        // 각 Map에 대해 숫자 값을 문자열로 변환하여 새로운 Map을 만들어 결과 리스트에 추가합니다.
        for (Map<String, String> nodeMap : nodeList) {
            Map<String, String> resultMap = new HashMap<>();
            for (Map.Entry<String, String> entry : nodeMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue(); // 값의 형식을 Object로 변경

                // 만약 값이 숫자라면 문자열로 변환하여 넣어줍니다.
                if (isNumeric(value)) {
                    resultMap.put(key, String.valueOf(value));
                } else {
                    resultMap.put(key, (String) value);
                }
            }
            resultList.add(resultMap);
        }

        return resultList;
    }

    @Autowired
    private RestTemplate restTemplate;

    @GetMapping("/call2")
    public ResponseEntity<String> callOtherApi2() {
        // 저장된 데이터
        // 5741 4D30 3033(노드ID) 0000 018C 20C0 245F(시간) 0006(데이터 수) 0001(1번) 0000
        // 3BA8(1번 데이터) 0002 0000 0C81 0003 0000 0C07 0004 0000 B9F0 0005 0000 0520 0006
        // 0000 0594
        int NODE = 12;
        int TIME = 16;
        int COUNT = 4;
        int HEADER = NODE + TIME;
        int HEADER2 = HEADER + COUNT; // 노드 iD + 시간 + 데이터 수
        int FILENAME = 4; // n번
        int VALUE = 8; // n번 데이터
        int FILEDATA = FILENAME + VALUE;

        // nodeget 호출하여 node 데이터 얻기
        List<Map<String, String>> node = mapper.NodeGet();

        StringBuilder resultBuilder = new StringBuilder();

        for (Map<String, String> map : node) {
            // node 값 가져오기
            String nodeValue = map.get("id");
            System.out.println(nodeValue);

            // 호출할 API의 URL
            String apiUrl = "http://wamons.wiseconn.co.kr:9000/data/" + nodeValue
                    + "/original?from=1701356400000&to=1701356640000";

            // API 호출 및 응답 수신
            ResponseEntity<byte[]> response = restTemplate.getForEntity(apiUrl,
                    byte[].class);
            if (response != null && response.hasBody()) {
                // 응답 바디를 바이트 배열로 가져옴
                byte[] responseBodyBytes = response.getBody();

                if (responseBodyBytes != null) {
                    StringBuilder hexStringBuilder = new StringBuilder();

                    for (int i = 0; i < responseBodyBytes.length; i++) {
                        // 각 바이트를 16진수로 변환하여 StringBuilder에 추가
                        String hexString = String.format("%02X", responseBodyBytes[i]);
                        hexStringBuilder.append(hexString);
                    }
                    // 16진수 문자열 반환
                    // 57414D3030330000018C20C0245F0006000100003BA8000200000C81000300000C0700040000B9F0000500000520000600000594
                    String hexResponseBody = hexStringBuilder.toString();

                    // 반복문의 기준이 되는 데이터 길이를 구하기 위함
                    // 57414D3030330000018C20C0245F (0006)
                    // 000100003BA8000200000C81000300000C0700040000B9F0000500000520000600000594
                    // 0006 >>> 데이터 수 6개 >>> (파일이름 길이 + 파일 데이터 길이)가 6개 존재
                    // 헤더 + 데이터 수 * (파일이름 길이 + 파일 데이터 길이) >>> 데이터 하나의 총 길이
                    String substring = hexResponseBody.substring(HEADER, HEADER2);
                    int DATA = Integer.parseInt(substring, 16);
                    int TOTALLEN = HEADER2 + (DATA * FILEDATA);

                    // TOTALLEN 마다 끊어서 반환
                    for (int i = 0; i < hexResponseBody.length(); i += TOTALLEN) {
                        // 앞에서 12자리(node id 부분)를 잘라서 10진수로 변환하여 StringBuilder에 추가
                        String substringNode = hexResponseBody.substring(i, i + NODE);
                        StringBuilder nodeBuilder = new StringBuilder();
                        for (int j = 0; j < substringNode.length(); j += 2) {
                            String byteString = substringNode.substring(j, j + 2);
                            nodeBuilder.append((char) Integer.parseInt(byteString, 16));
                        }
                        resultBuilder.append(nodeBuilder.toString() + "\n");

                        // // 13부터 28번째 자리를 local 시간으로 변환하여 StringBuilder에 추가
                        String substringTime = hexResponseBody.substring(i + NODE, i + HEADER);
                        long unixTime = Long.parseLong(substringTime, 16);
                        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(unixTime),
                                ZoneId.systemDefault());
                        String localTime = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd a hh:mm:ss.SSS"));
                        resultBuilder.append(localTime + "\n");

                        // // 29번째부터 32번째 자리를 10진수로 변환하여 StringBuilder에 추가
                        String subValue = hexResponseBody.substring(i + HEADER, i + HEADER + 4);
                        int value = Integer.parseInt(subValue, 16);
                        resultBuilder.append(value + "\n");

                        for (int k = i + HEADER2; k < i + TOTALLEN; k += FILEDATA) {
                            String name = hexResponseBody.substring(k, k + FILENAME);
                            String value2 = hexResponseBody.substring(k + FILENAME, k + FILEDATA);
                            // name이 FFFF인 부분은 데이터가 없는 빈 슬롯이므로 출력에서 제외
                            if (!name.equals("FFFF")) {
                                int fileName = Integer.parseInt(name, 16);
                                Long fileData = Long.parseLong(value2, 16);
                                double realData = (double) fileData / 100.0;
                                resultBuilder.append(fileName).append("\n").append(realData).append("\n");
                            }

                        }
                    }
                }
            }
        }
        System.out.println(node);
        // 응답 반환
        return ResponseEntity.ok(resultBuilder.toString());
    }

    @GetMapping("/call")
    public ResponseEntity<String> callOtherApi() {
        int NODE = 12;
        int TIME = 16;
        int HEADER = NODE + TIME;
        int HEADER2 = HEADER + 4;
        int FILENAME = 4;
        int VALUE = 8;
        int FILEDATA = FILENAME + VALUE;

        // 호출할 API의 URL
        String apiUrl = "http://wamons.wiseconn.co.kr:9000/data/PRO001/original?from=1701356400000&to=1701356640000";

        // API 호출 및 응답 수신
        ResponseEntity<byte[]> response = restTemplate.getForEntity(apiUrl,
                byte[].class);

        // 바디를 바이트 배열로 가져옴
        byte[] responseBodyBytes = response.getBody();

        // 바이트 배열을 16진수 문자열로 변환
        StringBuilder hexStringBuilder = new StringBuilder();

        for (int i = 0; i < responseBodyBytes.length; i++) {
            // 각 바이트를 16진수로 변환하여 StringBuilder에 추가
            String hexString = String.format("%02X", responseBodyBytes[i]);
            hexStringBuilder.append(hexString);
        }
        // 16진수 문자열 반환
        String hexResponseBody = hexStringBuilder.toString();

        // 반복문의 기준이 되는 데이터 길이를 구하기 위함
        String substring = hexResponseBody.substring(HEADER, HEADER + 4);
        int DATA = Integer.parseInt(substring, 16);
        int TOTALLEN = HEADER2 + (DATA * FILEDATA);
        System.out.println(TOTALLEN);

        // // TOTALLEN 마다 끊어서 반환
        StringBuilder resultBuilder = new StringBuilder();
        for (int i = 0; i < hexResponseBody.length(); i += TOTALLEN) {
            // 앞에서 12자리를 잘라서 10진수로 변환하여 StringBuilder에 추가
            String substringNode = hexResponseBody.substring(i, i + NODE);
            StringBuilder nodeBuilder = new StringBuilder();
            for (int j = 0; j < substringNode.length(); j += 2) {
                String byteString = substringNode.substring(j, j + 2);
                nodeBuilder.append((char) Integer.parseInt(byteString, 16));
            }
            resultBuilder.append(nodeBuilder.toString() + "\n");

            // 13부터 28번째 자리를 local 시간으로 변환하여 StringBuilder에 추가
            String substringTime = hexResponseBody.substring(i + NODE, i + HEADER);
            long unixTime = Long.parseLong(substringTime, 16);
            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(unixTime),
                    ZoneId.systemDefault());
            String localTime = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd a hh:mm:ss.SSS"));
            resultBuilder.append(localTime + "\n");

            // 29번째부터 32번째 자리를 10진수로 변환하여 StringBuilder에 추가
            String subValue = hexResponseBody.substring(i + HEADER, i + HEADER + 4);
            int value = Integer.parseInt(subValue, 16);
            resultBuilder.append(value + "\n");

            // 33번째부터 파일명(4개)과 데이터(8개)로 구분하여 변환하여 StringBuilder에 추가
            for (int k = i + HEADER2; k < i + TOTALLEN; k += FILEDATA) {
                String subFileName = hexResponseBody.substring(k, k + FILENAME);
                String subFileData = hexResponseBody.substring(k + FILENAME, k + FILEDATA);

                // fileName이 FFFF이면 해당 부분과 fileData 부분은 추가하지 않음
                if (!subFileName.equals("FFFF")) {
                    int fileName = Integer.parseInt(subFileName, 16);
                    int fileData = Integer.parseInt(subFileData, 16);
                    resultBuilder.append(fileName).append("\n").append(fileData).append("\n");
                }
            }

            resultBuilder.append("\n");
        }

        // 응답 반환
        return ResponseEntity.ok(resultBuilder.toString());
    }

    @GetMapping(value = "category", produces = "application/json; charset=UTF-8")
    public List<Map<String, String>> categoryGet() {
        List<Map<String, String>> category = mapper.CategoryGet();
        List<Map<String, String>> resultList = new ArrayList<>();

        // 각 Map에 대해 숫자 값을 문자열로 변환하여 새로운 Map을 만들어 결과 리스트에 추가합니다.
        for (Map<String, String> categoryMap : category) {
            Map<String, String> resultMap = new HashMap<>();
            for (Map.Entry<String, String> entry : categoryMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue(); // 값의 형식을 Object로 변경

                // 만약 값이 숫자라면 문자열로 변환하여 넣어줍니다.
                if (isNumeric(value)) {
                    resultMap.put(key, String.valueOf(value));
                } else {
                    resultMap.put(key, (String) value);
                }
            }
            resultList.add(resultMap);
        }

        return resultList;
    }

    @GetMapping(value = "categorycnt", produces = "application/json; charset=UTF-8")
    public List<Map<String, String>> categoryCnt() {
        List<Map<String, String>> count = mapper.CategoryCnt();
        List<Map<String, String>> resultList = new ArrayList<>();

        // 각 Map에 대해 숫자 값을 문자열로 변환하여 새로운 Map을 만들어 결과 리스트에 추가합니다.
        for (Map<String, String> categorycountMap : count) {
            Map<String, String> resultMap = new HashMap<>();
            for (Map.Entry<String, String> entry : categorycountMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue(); // 값의 형식을 Object로 변경

                // 만약 값이 숫자라면 문자열로 변환하여 넣어줍니다.
                if (isNumeric(value)) {
                    resultMap.put(key, String.valueOf(value));
                } else {
                    resultMap.put(key, (String) value);
                }
            }
            resultList.add(resultMap);
        }

        return resultList;
    }

    @GetMapping(value = "sensor", produces = "application/json; charset=UTF-8")
    public List<Map<String, String>> sensorGet() {
        List<Map<String, String>> sensorList = mapper.SensorGet();
        List<Map<String, String>> resultList = new ArrayList<>();

        // 각 Map에 대해 숫자 값을 문자열로 변환하여 새로운 Map을 만들어 결과 리스트에 추가합니다.
        for (Map<String, String> sensorMap : sensorList) {
            Map<String, String> resultMap = new HashMap<>();
            for (Map.Entry<String, String> entry : sensorMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue(); // 값의 형식을 Object로 변경

                // 만약 값이 숫자라면 문자열로 변환하여 넣어줍니다.
                if (isNumeric(value)) {
                    resultMap.put(key, String.valueOf(value));
                } else {
                    resultMap.put(key, (String) value);
                }
            }
            resultList.add(resultMap);
        }

        return resultList;
    }

    @GetMapping(value = "total", produces = "application/json; charset=UTF-8")
    public List<Map<String, String>> TotalData() {
        List<Map<String, String>> totalList = mapper.TotalData();
        List<Map<String, String>> resultList = new ArrayList<>();

        // 각 Map에 대해 숫자 값을 문자열로 변환하여 새로운 Map을 만들어 결과 리스트에 추가합니다.
        for (Map<String, String> totalMap : totalList) {
            Map<String, String> resultMap = new HashMap<>();
            for (Map.Entry<String, String> entry : totalMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue(); // 값의 형식을 Object로 변경

                // 만약 값이 숫자라면 문자열로 변환하여 넣어줍니다.
                if (isNumeric(value)) {
                    resultMap.put(key, String.valueOf(value));
                } else {
                    resultMap.put(key, (String) value);
                }
            }
            resultList.add(resultMap);
        }

        return resultList;
    }

    // 숫자인지 여부를 확인하는 메서드를 수정하여 Object 타입을 처리합니다.
    private boolean isNumeric(Object obj) {
        if (obj instanceof String) {
            return ((String) obj).matches("-?\\d+(\\.\\d+)?");
        } else if (obj instanceof Integer || obj instanceof Long || obj instanceof Float || obj instanceof Double
                || obj instanceof Short || obj instanceof Byte) {
            return true;
        } else {
            return false;
        }
    }
}
