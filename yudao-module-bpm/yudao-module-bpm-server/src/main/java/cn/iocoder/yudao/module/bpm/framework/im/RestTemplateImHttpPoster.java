package cn.iocoder.yudao.module.bpm.framework.im;

import cn.iocoder.yudao.framework.common.util.spring.SpringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class RestTemplateImHttpPoster implements ImHttpPoster {

    private RestTemplate restTemplate() {
        return SpringUtils.getBean(RestTemplate.class);
    }

    @Override
    public String get(String url) {
        return restTemplate().getForObject(url, String.class);
    }

    @Override
    public String postJson(String url, String jsonBody) {
        return postJson(url, jsonBody, null);
    }

    @Override
    public String postJson(String url, String jsonBody, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        ResponseEntity<String> resp = restTemplate().postForEntity(url, new HttpEntity<>(jsonBody, headers), String.class);
        return resp.getBody();
    }
}
